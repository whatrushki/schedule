package app.what.data.updater

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.what.domain.services.SemanticVersion
import app.what.foundation.services.AppLogger.Companion.Auditor
import app.what.foundation.services.auto_update.AppUpdateManager
import app.what.foundation.services.auto_update.DownloadProgress
import app.what.foundation.services.auto_update.DownloadState
import app.what.foundation.services.auto_update.GitHubRelease
import app.what.foundation.services.auto_update.ReleaseNotes
import app.what.foundation.services.auto_update.UpdateConfig
import app.what.foundation.services.auto_update.UpdateInfo
import app.what.foundation.services.auto_update.UpdateResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlin.system.exitProcess

enum class DesktopPlatform {
    WINDOWS,
    LINUX,
    MACOS,
    UNKNOWN
}

class DesktopAppUpdateManager(
    private val httpClient: HttpClient,
    private val config: UpdateConfig = UpdateConfig("whatrushki", "schedule", "1.5.3"),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) : AppUpdateManager {

    override var updateInfo by mutableStateOf<UpdateInfo?>(null)
        private set

    private val _downloadState = mutableStateOf<DownloadState>(DownloadState.Idle)
    override val downloadState: DownloadState get() = _downloadState.value

    private var downloadJob: Job? = null
    private var downloadedInstallerFile: File? = null

    val currentPlatform: DesktopPlatform by lazy {
        val os = System.getProperty("os.name", "").lowercase()
        when {
            os.contains("win") -> DesktopPlatform.WINDOWS
            os.contains("mac") -> DesktopPlatform.MACOS
            os.contains("nix") || os.contains("nux") || os.contains("aix") -> DesktopPlatform.LINUX
            else -> DesktopPlatform.UNKNOWN
        }
    }

    init {
        scope.launch(Dispatchers.IO) {
            Auditor.debug("desktop_updater", "Запуск проверки обновлений для десктопа (платформа: $currentPlatform)")
            try {
                checkForUpdates()
            } catch (e: Exception) {
                Auditor.warn("desktop_updater", "Ошибка при первоначальной проверке: ${e.message}")
            }
        }
    }

    override suspend fun checkForUpdates(): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val releases = httpClient.get("https://api.github.com/repos/${config.githubOwner}/${config.githubRepo}/releases") {
                parameter("per_page", 5)
            }.body<List<GitHubRelease>>()

            val latest = releases.firstOrNull { !it.draft && !it.prerelease }
                ?: return@withContext UpdateResult.NotAvailable

            val latestVersion = SemanticVersion.parse(latest.tagName)
            val currentVersion = SemanticVersion.parse(config.currentVersion)

            if (latestVersion <= currentVersion) {
                return@withContext UpdateResult.UpToDate
            }

            // Выбираем правильный нативный установщик для текущей платформы
            val asset = findNativeAsset(latest)

            // Загружаем structured release-notes.json если доступен
            val jsonAsset = latest.assets.firstOrNull { it.name == "release-notes.json" }
            var parsedReleaseNotes: ReleaseNotes? = null
            if (jsonAsset != null) {
                try {
                    val jsonString = httpClient.get(jsonAsset.browserDownloadUrl).body<String>()
                    parsedReleaseNotes = ReleaseNotes.fromJson(jsonString)
                } catch (e: Exception) {
                    Auditor.warn("desktop_updater", "Не удалось распарсить release-notes.json: ${e.message}")
                }
            }

            val releaseBody = latest.body
            if (parsedReleaseNotes == null && !releaseBody.isNullOrBlank()) {
                parsedReleaseNotes = ReleaseNotes(
                    version = latest.tagName,
                    changelog = ReleaseNotes.parseMarkdownChangelog(releaseBody)
                )
            }

            val downloadUrl = asset?.browserDownloadUrl
                ?: "https://github.com/${config.githubOwner}/${config.githubRepo}/releases/tag/${latest.tagName}"

            val info = UpdateInfo(
                version = latest.tagName,
                fileSize = asset?.size ?: 0L,
                downloadUrl = downloadUrl,
                releaseNotes = latest.body,
                releaseNotesData = parsedReleaseNotes
            )

            updateInfo = info
            UpdateResult.Available(info)
        } catch (e: Exception) {
            Auditor.err("desktop_updater", "Сбой проверки обновлений: ${e.message}")
            UpdateResult.Error(e.message ?: "Failed to check updates")
        }
    }

    override fun handleAction() {
        val current = _downloadState.value
        when (current) {
            is DownloadState.Idle, is DownloadState.Error -> {
                val info = updateInfo ?: return
                startDownload(info)
            }
            is DownloadState.Completed -> {
                val file = downloadedInstallerFile ?: (current.file as? File)
                if (file != null && file.exists()) {
                    installUpdate(file)
                } else {
                    updateInfo?.let { startDownload(it) }
                }
            }
            else -> {
                // Downloading or Preparing - ignore duplicate click
            }
        }
    }

    override fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _downloadState.value = DownloadState.Idle
    }

    override fun release() {
        cancelDownload()
    }

    /**
     * Подбирает подходящий нативный дистрибутив под ОС пользователя,
     * избегая универсальных нерабочих jar-архивов.
     */
    private fun findNativeAsset(release: GitHubRelease) = when (currentPlatform) {
        DesktopPlatform.WINDOWS -> {
            // Windows: MSI установщик в приоритете (чистое обновление поверх), затем EXE установщик
            release.assets.firstOrNull { it.name.endsWith(".msi", ignoreCase = true) }
                ?: release.assets.firstOrNull { it.name.endsWith(".exe", ignoreCase = true) }
                ?: release.assets.firstOrNull { it.name.contains("windows", ignoreCase = true) }
        }
        DesktopPlatform.LINUX -> {
            // Linux: DEB пакет (Ubuntu/Debian) -> RPM (Fedora/RHEL) -> JAR
            release.assets.firstOrNull { it.name.endsWith(".deb", ignoreCase = true) }
                ?: release.assets.firstOrNull { it.name.endsWith(".rpm", ignoreCase = true) }
                ?: release.assets.firstOrNull { it.name.endsWith(".jar", ignoreCase = true) }
        }
        DesktopPlatform.MACOS -> {
            // macOS: DMG образ -> ZIP
            release.assets.firstOrNull { it.name.endsWith(".dmg", ignoreCase = true) }
                ?: release.assets.firstOrNull { it.name.contains("mac", ignoreCase = true) && it.name.endsWith(".zip", ignoreCase = true) }
        }
        DesktopPlatform.UNKNOWN -> {
            release.assets.firstOrNull {
                it.name.endsWith(".msi", ignoreCase = true) ||
                it.name.endsWith(".exe", ignoreCase = true) ||
                it.name.endsWith(".deb", ignoreCase = true) ||
                it.name.endsWith(".dmg", ignoreCase = true)
            }
        }
    }

    private fun startDownload(info: UpdateInfo) {
        downloadJob?.cancel()
        downloadJob = scope.launch(Dispatchers.IO) {
            try {
                _downloadState.value = DownloadState.Preparing

                // Если ссылка не ведет на файл установщика (например, fallback на страницу релиза)
                if (!info.downloadUrl.contains("/download/")) {
                    openBrowser(info.downloadUrl)
                    _downloadState.value = DownloadState.Idle
                    return@launch
                }

                val updatesDir = File(System.getProperty("java.io.tmpdir"), "what_schedule_updates").apply { mkdirs() }
                val rawFileName = info.downloadUrl.substringAfterLast("/").substringBefore("?")
                val fileName = if (rawFileName.isNotBlank()) rawFileName else "update_${info.version}.bin"
                val destination = File(updatesDir, fileName)

                val downloadedFile = downloadFileWithProgress(info.downloadUrl, destination) { progress ->
                    val percent = if (progress.totalBytes > 0) {
                        (progress.downloadedBytes * 100 / progress.totalBytes).toInt().coerceIn(0, 100)
                    } else {
                        (progress.progress * 100).toInt().coerceIn(0, 100)
                    }
                    _downloadState.value = DownloadState.Downloading(percent)
                }

                if (downloadedFile.length() > 0) {
                    downloadedInstallerFile = downloadedFile
                    _downloadState.value = DownloadState.Completed(downloadedFile)

                    // Сразу запускаем обновление приложения на месте
                    installUpdate(downloadedFile)
                } else {
                    downloadedFile.delete()
                    _downloadState.value = DownloadState.Error("Загруженный файл пуст")
                }
            } catch (e: Exception) {
                Auditor.err("desktop_updater", "Ошибка при скачивании обновления: ${e.message}")
                _downloadState.value = DownloadState.Error(e.message ?: "Ошибка скачивания")
            }
        }
    }

    /**
     * Запускает системный процесс обновления на месте и завершает текущее приложение,
     * чтобы бинарные файлы не были заблокированы ОС.
     */
    private fun installUpdate(file: File) {
        Auditor.info("desktop_updater", "Запуск установки обновления на месте: ${file.absolutePath}")
        try {
            val fileName = file.name.lowercase()
            when {
                // Windows: запуск MSI или EXE установщика
                currentPlatform == DesktopPlatform.WINDOWS || fileName.endsWith(".msi") || fileName.endsWith(".exe") -> {
                    val process = if (fileName.endsWith(".msi")) {
                        ProcessBuilder("msiexec.exe", "/i", file.absolutePath).start()
                    } else {
                        ProcessBuilder(file.absolutePath).start()
                    }
                    if (process != null) {
                        // Завершаем текущий процесс приложения, чтобы установщик мог заменить файлы
                        exitProcess(0)
                    }
                }

                // Linux: запуск через xdg-open (системный Software Center) или установщик
                currentPlatform == DesktopPlatform.LINUX -> {
                    try {
                        ProcessBuilder("xdg-open", file.absolutePath).start()
                    } catch (_: Exception) {
                        if (fileName.endsWith(".deb")) {
                            ProcessBuilder("pkexec", "dpkg", "-i", file.absolutePath).start()
                        }
                    }
                    exitProcess(0)
                }

                // macOS: монтирование DMG
                currentPlatform == DesktopPlatform.MACOS || fileName.endsWith(".dmg") -> {
                    ProcessBuilder("open", file.absolutePath).start()
                    exitProcess(0)
                }

                else -> {
                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().open(file)
                    }
                }
            }
        } catch (e: Exception) {
            Auditor.err("desktop_updater", "Не удалось запустить установщик: ${e.message}")
            // В случае сбоя запуска установщика открываем папку с файлом в проводнике
            try {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(file.parentFile ?: file)
                }
            } catch (_: Exception) {}
        }
    }

    private suspend fun downloadFileWithProgress(
        downloadUrl: String,
        destination: File,
        onProgress: (DownloadProgress) -> Unit
    ): File = withContext(Dispatchers.IO) {
        var currentUrl = downloadUrl
        var connection: HttpURLConnection? = null
        try {
            var redirects = 0
            while (redirects < 5) {
                val url = URL(currentUrl)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "schedule-desktop-app")
                }
                connection.connect()

                val code = connection.responseCode
                if (code == HttpURLConnection.HTTP_MOVED_PERM ||
                    code == HttpURLConnection.HTTP_MOVED_TEMP ||
                    code == HttpURLConnection.HTTP_SEE_OTHER ||
                    code == 307 || code == 308
                ) {
                    val location = connection.getHeaderField("Location") ?: break
                    connection.disconnect()
                    currentUrl = if (location.startsWith("http")) location else URL(url, location).toString()
                    redirects++
                } else {
                    break
                }
            }

            if (connection == null || connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP ${connection?.responseCode ?: "No response"}")
            }

            val totalBytes = connection.contentLengthLong
            val buffer = ByteArray(8192)
            var downloadedBytes = 0L
            var lastReportedTime = 0L

            connection.inputStream.use { input ->
                FileOutputStream(destination).use { output ->
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val now = System.currentTimeMillis()
                        if (now - lastReportedTime > 150) {
                            lastReportedTime = now
                            val progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes else 0f
                            onProgress(DownloadProgress(progress = progress, downloadedBytes = downloadedBytes, totalBytes = totalBytes))
                        }
                    }
                }
            }

            onProgress(DownloadProgress(progress = 1f, downloadedBytes = downloadedBytes, totalBytes = totalBytes))
            destination
        } finally {
            connection?.disconnect()
        }
    }

    private fun openBrowser(url: String) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI(url))
            }
        } catch (_: Exception) {}
    }
}

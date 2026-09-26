package app.what.schedule.updater

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import app.what.domain.services.GitHubRelease
import app.what.foundation.services.AppLogger.Companion.Auditor
import app.what.foundation.services.auto_update.AppUpdateManager
import app.what.foundation.services.auto_update.DownloadProgress
import app.what.foundation.services.auto_update.DownloadState
import app.what.foundation.services.auto_update.UpdateConfig
import app.what.foundation.services.auto_update.UpdateInfo
import app.what.foundation.services.auto_update.UpdateResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class AppGitHubUpdateManager(
    private val context: Context,
    private val config: UpdateConfig,
    private val httpClient: HttpClient,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) : AppUpdateManager {

    override var updateInfo by mutableStateOf<UpdateInfo?>(null)
        private set

    private val _downloadState = mutableStateOf<DownloadState>(DownloadState.Idle)
    override val downloadState: DownloadState get() = _downloadState.value

    private var downloadJob: Job? = null
    private var lastCheckTime: Long = 0L
    private var lastResult: UpdateResult? = null

    init {
        scope.launch(Dispatchers.IO) {
            Auditor.debug("updater", "Инициализация фоновой проверки обновлений GitHub")
            try {
                checkForUpdates()
            } catch (e: Exception) {
                Auditor.debug("updater", "Ошибка при фоновой проверке обновлений: ${e.message}")
            }
        }
    }

    override suspend fun checkForUpdates(): UpdateResult {
        val now = System.currentTimeMillis()
        if (updateInfo != null) {
            return UpdateResult.Available(updateInfo!!)
        }
        val cached = lastResult
        if (cached != null && now - lastCheckTime < 15 * 60 * 1000L) {
            Auditor.debug("updater", "Пропуск проверки: кэшированный результат проверки (15 мин cooldown)")
            return cached
        }

        return try {
            Auditor.debug("updater", "Запрос релизов GitHub: ${config.githubOwner}/${config.githubRepo}")
            val releases = httpClient.get("https://api.github.com/repos/${config.githubOwner}/${config.githubRepo}/releases") {
                parameter("per_page", 10)
                header(HttpHeaders.UserAgent, "${config.githubOwner}/${config.githubRepo}")
            }.body<List<GitHubRelease>>()

            val latestRelease = releases
                .filter { !it.draft && !it.prerelease }
                .maxByOrNull { parseVersion(it.tagName) }

            if (latestRelease == null) {
                val res = UpdateResult.NotAvailable
                lastCheckTime = now
                lastResult = res
                return res
            }

            val latestVersion = parseVersion(latestRelease.tagName)
            val currentVersionParsed = parseVersion(config.currentVersion)

            Auditor.debug("updater", "Текущая: $currentVersionParsed (${config.currentVersion}), Последняя: $latestVersion (${latestRelease.tagName})")

            if (latestVersion > currentVersionParsed) {
                val matchedAsset = latestRelease.assets.firstOrNull { it.name.endsWith(".apk") }
                if (matchedAsset == null) {
                    val err = UpdateResult.Error("Обновление ${latestRelease.tagName} найдено, но APK отсутствует в релизе")
                    lastCheckTime = now
                    lastResult = err
                    return err
                }

                val info = UpdateInfo(
                    version = latestRelease.tagName,
                    fileSize = matchedAsset.size,
                    downloadUrl = matchedAsset.browserDownloadUrl,
                    releaseNotes = latestRelease.body ?: ""
                )
                updateInfo = info
                checkIfAlreadyDownloaded(info)
                val res = UpdateResult.Available(info)
                lastCheckTime = now
                lastResult = res
                res
            } else {
                val res = UpdateResult.NotAvailable
                lastCheckTime = now
                lastResult = res
                res
            }
        } catch (e: Exception) {
            Auditor.debug("updater", "Не удалось проверить обновления: ${e.message}")
            val err = UpdateResult.Error("Failed to check for updates: ${e.message}")
            lastCheckTime = now
            lastResult = err
            err
        }
    }

    private fun checkIfAlreadyDownloaded(info: UpdateInfo) {
        val file = getDownloadedFile(getFileName(info.version))
        if (file?.exists() == true && file.length() > 0 && isArchiveValid(file)) {
            _downloadState.value = DownloadState.Completed(file)
        }
    }

    private fun isArchiveValid(file: File): Boolean {
        return try {
            context.packageManager.getPackageArchiveInfo(file.absolutePath, 0) != null
        } catch (_: Exception) {
            false
        }
    }

    private fun getFileName(version: String) = "${config.githubRepo}-$version.apk"

    private fun getUpdatesDir(): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
        val updatesDir = File(dir, "updates")
        updatesDir.mkdirs()
        return updatesDir
    }

    private fun getDownloadedFile(fileName: String): File? {
        val file = File(getUpdatesDir(), fileName)
        return file.takeIf { it.exists() }
    }

    override fun handleAction() {
        val info = updateInfo ?: return
        when (val state = downloadState) {
            is DownloadState.Idle,
            is DownloadState.Error -> downloadUpdate(info)

            is DownloadState.Completed -> {
                (state.file as? File)?.let { installUpdate(it) }
            }

            else -> { /* Downloading or Preparing */ }
        }
    }

    private fun downloadUpdate(info: UpdateInfo) {
        downloadJob?.cancel()
        downloadJob = scope.launch(Dispatchers.IO) {
            try {
                _downloadState.value = DownloadState.Preparing

                val fileName = getFileName(info.version)
                val destination = File(getUpdatesDir(), fileName)

                val result = runCatching {
                    downloadFileWithProgress(
                        downloadUrl = info.downloadUrl,
                        destination = destination
                    ) { progress ->
                        val percent = if (progress.totalBytes > 0) {
                            (progress.downloadedBytes * 100 / progress.totalBytes).toInt().coerceIn(0, 100)
                        } else {
                            (progress.progress * 100).toInt().coerceIn(0, 100)
                        }
                        _downloadState.value = DownloadState.Downloading(percent)
                    }
                }

                _downloadState.value = result.fold(
                    onSuccess = { file ->
                        if (isArchiveValid(file)) {
                            DownloadState.Completed(file)
                        } else {
                            file.delete()
                            DownloadState.Error("Загруженный файл поврежден")
                        }
                    },
                    onFailure = { DownloadState.Error(it.message ?: "Ошибка загрузки") }
                )
            } catch (e: Exception) {
                _downloadState.value = DownloadState.Error(e.message ?: "Неизвестная ошибка")
            }
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
                    setRequestProperty("User-Agent", "schedule-app")
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
                throw Exception("HTTP error: ${connection?.responseCode}")
            }

            val contentLength = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                connection.contentLengthLong
            } else {
                connection.contentLength.toLong()
            }
            var downloadedBytes = 0L
            val buffer = ByteArray(8192)

            connection.inputStream.use { inputStream ->
                destination.outputStream().buffered().use { output ->
                    var bytesRead: Int
                    var iter = 0
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        ensureActive()
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val progressValue = if (contentLength <= 0) 0f
                        else downloadedBytes.toFloat() / contentLength

                        if (iter++ > 25) {
                            iter = 0
                            withContext(Dispatchers.Main) {
                                onProgress(
                                    DownloadProgress(
                                        progress = progressValue,
                                        downloadedBytes = downloadedBytes,
                                        totalBytes = contentLength
                                    )
                                )
                            }
                        }
                    }
                }
            }

            destination
        } finally {
            connection?.disconnect()
        }
    }

    private fun installUpdate(file: File) {
        if (!isArchiveValid(file)) {
            _downloadState.value = DownloadState.Error("Файл обновления поврежден")
            file.delete()
            return
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    override fun cancelDownload() {
        downloadJob?.cancel()
        _downloadState.value = DownloadState.Idle
    }

    override fun release() {
        downloadJob?.cancel()
        scope.cancel()
    }

    private fun parseVersion(versionString: String): Version {
        val cleanVersion = versionString.trim().removePrefix("v").removePrefix("V")
        try {
            val mainAndPreRelease = cleanVersion.split("-", limit = 2)
            val mainPart = mainAndPreRelease[0]

            val versionDigitsRegex = Regex("""\d+(\.\d+)*""")
            val matchedNumbers = versionDigitsRegex.find(mainPart)?.value ?: mainPart
            val mainParts = matchedNumbers.split(".").map { it.toIntOrNull() ?: 0 }

            var preReleaseType = PreReleaseType.STABLE
            var preReleaseNumber = 0

            if (mainAndPreRelease.size > 1) {
                val preReleasePart = mainAndPreRelease[1]
                val preReleaseParts = preReleasePart.split(".")

                preReleaseType = when (preReleaseParts[0].lowercase()) {
                    "alpha" -> PreReleaseType.ALPHA
                    "beta" -> PreReleaseType.BETA
                    "rc" -> PreReleaseType.RELEASE_CANDIDATE
                    "stable" -> PreReleaseType.STABLE
                    else -> PreReleaseType.STABLE
                }

                preReleaseNumber = preReleaseParts.getOrElse(1) { "0" }.toIntOrNull() ?: 0
            }

            return Version(
                major = mainParts.getOrElse(0) { 0 },
                minor = mainParts.getOrElse(1) { 0 },
                patch = mainParts.getOrElse(2) { 0 },
                preReleaseType = preReleaseType,
                preReleaseNumber = preReleaseNumber
            )
        } catch (_: Exception) {
            return Version(0, 0, 0, PreReleaseType.ALPHA, 0)
        }
    }

    private data class Version(
        val major: Int,
        val minor: Int,
        val patch: Int,
        val preReleaseType: PreReleaseType = PreReleaseType.STABLE,
        val preReleaseNumber: Int = 0
    ) : Comparable<Version> {
        override fun compareTo(other: Version): Int {
            return compareValuesBy(
                this, other,
                { it.major },
                { it.minor },
                { it.patch },
                { it.preReleaseType.ordinal },
                { it.preReleaseNumber }
            )
        }
    }

    private enum class PreReleaseType {
        ALPHA,
        BETA,
        RELEASE_CANDIDATE,
        STABLE
    }
}

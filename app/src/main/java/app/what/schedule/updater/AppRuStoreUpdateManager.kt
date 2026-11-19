package app.what.schedule.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.what.domain.services.GitHubRelease
import app.what.domain.services.ReleaseNotes
import app.what.domain.services.SemanticVersion
import app.what.foundation.services.AppLogger.Companion.Auditor
import app.what.foundation.services.auto_update.AppUpdateManager
import app.what.foundation.services.auto_update.DownloadState
import app.what.foundation.services.auto_update.UpdateConfig
import app.what.foundation.services.auto_update.UpdateInfo
import app.what.foundation.services.auto_update.UpdateResult
import app.what.schedule.data.local.settings.AppValues
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import ru.rustore.sdk.appupdate.manager.factory.RuStoreAppUpdateManagerFactory
import ru.rustore.sdk.appupdate.model.UpdateAvailability
import kotlin.coroutines.resume

class AppRuStoreUpdateManager(
    private val context: Context,
    private val config: UpdateConfig,
    private val httpClient: HttpClient,
    private val appValues: AppValues? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) : AppUpdateManager {

    override var updateInfo by mutableStateOf<UpdateInfo?>(null)
        private set

    private val _downloadState = mutableStateOf<DownloadState>(DownloadState.Idle)
    override val downloadState: DownloadState get() = _downloadState.value

    private val ruStoreManager by lazy {
        RuStoreAppUpdateManagerFactory.create(context)
    }

    private var lastCheckTime: Long = 0L
    private var lastResult: UpdateResult? = null

    init {
        scope.launch(Dispatchers.IO) {
            Auditor.debug("RuStoreUpdater", "Инициализация проверки обновлений RuStore")
            try {
                checkForUpdates()
            } catch (e: Exception) {
                Auditor.debug("RuStoreUpdater", "Ошибка при фоновой проверке RuStore: ${e.message}")
            }
        }
    }

    override suspend fun checkForUpdates(): UpdateResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (updateInfo != null) {
            return@withContext UpdateResult.Available(updateInfo!!)
        }
        val cached = lastResult
        if (cached != null && now - lastCheckTime < 15 * 60 * 1000L) {
            return@withContext cached
        }

        // Параллельно запрашиваем богатые release-notes с GitHub для отображения списка исправлений
        val releaseNotesData = fetchRichReleaseNotes()

        try {
            suspendCancellableCoroutine<UpdateResult> { continuation ->
                ruStoreManager.getAppUpdateInfo()
                    .addOnSuccessListener { appUpdateInfo ->
                        val isAvailable = appUpdateInfo.updateAvailability == UpdateAvailability.UPDATE_AVAILABLE
                        val availableVersion = appUpdateInfo.availableVersionName
                        Auditor.debug(
                            "RuStoreUpdater",
                            "RuStore результат: isAvailable=$isAvailable, version=$availableVersion"
                        )

                        if (isAvailable && !availableVersion.isNullOrBlank()) {
                            val info = UpdateInfo(
                                version = availableVersion,
                                fileSize = appUpdateInfo.fileSize,
                                downloadUrl = "https://www.rustore.ru/catalog/app/${context.packageName}",
                                releaseNotes = appUpdateInfo.whatsNew,
                                releaseNotesData = releaseNotesData
                            )
                            updateInfo = info
                            val res = UpdateResult.Available(info)
                            lastCheckTime = now
                            lastResult = res
                            continuation.resume(res)
                        } else {
                            val res = UpdateResult.UpToDate
                            lastCheckTime = now
                            lastResult = res
                            continuation.resume(res)
                        }
                    }
                    .addOnFailureListener { error ->
                        Auditor.debug("RuStoreUpdater", "RuStore SDK ошибка: ${error.message}")
                        // Фоллбэк: если RuStore SDK не отвечает (например, нет сервисов RuStore),
                        // проверяем через GitHub releases, чтобы пользователь не остался без критических обновлений
                        scope.launch(Dispatchers.IO) {
                            val fallbackResult = checkGitHubFallback(now, releaseNotesData)
                            continuation.resume(fallbackResult)
                        }
                    }
            }
        } catch (e: Exception) {
            val err = UpdateResult.Error(e.message ?: "Не удалось проверить обновления RuStore")
            lastCheckTime = now
            lastResult = err
            err
        }
    }

    private suspend fun fetchRichReleaseNotes(): ReleaseNotes? {
        return try {
            val releases = httpClient.get("https://api.github.com/repos/${config.githubOwner}/${config.githubRepo}/releases") {
                parameter("per_page", 5)
                header(HttpHeaders.UserAgent, "${config.githubOwner}/${config.githubRepo}")
            }.body<List<GitHubRelease>>()

            val latest = releases.firstOrNull { !it.draft && !it.prerelease }
            val jsonAsset = latest?.assets?.firstOrNull { it.name == "release-notes.json" }
            if (jsonAsset != null) {
                val jsonString = httpClient.get(jsonAsset.browserDownloadUrl).body<String>()
                appValues?.cachedReleaseNotes?.set(jsonString)
                ReleaseNotes.fromJson(jsonString)
            } else {
                null
            }
        } catch (e: Exception) {
            Auditor.debug("RuStoreUpdater", "Не удалось загрузить release-notes.json: ${e.message}")
            null
        }
    }

    private suspend fun checkGitHubFallback(now: Long, releaseNotes: ReleaseNotes?): UpdateResult {
        return try {
            val releases = httpClient.get("https://api.github.com/repos/${config.githubOwner}/${config.githubRepo}/releases") {
                parameter("per_page", 5)
                header(HttpHeaders.UserAgent, "${config.githubOwner}/${config.githubRepo}")
            }.body<List<GitHubRelease>>()

            val latest = releases.firstOrNull { !it.draft && !it.prerelease } ?: return UpdateResult.NotAvailable
            val latestVer = SemanticVersion.parse(latest.tagName)
            val currentVer = SemanticVersion.parse(config.currentVersion)

            if (latestVer > currentVer) {
                val info = UpdateInfo(
                    version = latest.tagName,
                    fileSize = 0L,
                    downloadUrl = "https://www.rustore.ru/catalog/app/${context.packageName}",
                    releaseNotes = latest.body,
                    releaseNotesData = releaseNotes
                )
                updateInfo = info
                val res = UpdateResult.Available(info)
                lastCheckTime = now
                lastResult = res
                res
            } else {
                val res = UpdateResult.UpToDate
                lastCheckTime = now
                lastResult = res
                res
            }
        } catch (e: Exception) {
            UpdateResult.Error("Fallback check failed: ${e.message}")
        }
    }

    override fun handleAction() {
        Auditor.info("RuStoreUpdater", "Перенаправление пользователя в магазин RuStore")
        val ruStoreUri = Uri.parse("rustore://apps.rustore.ru/app/${context.packageName}")
        val marketIntent = Intent(Intent.ACTION_VIEW, ruStoreUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(marketIntent)
        } catch (_: Exception) {
            val webIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://www.rustore.ru/catalog/app/${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(webIntent)
            } catch (e: Exception) {
                Auditor.debug("RuStoreUpdater", "Не удалось открыть веб-витрину RuStore: ${e.message}")
            }
        }
    }

    override fun cancelDownload() {}

    override fun release() {
        scope.cancel()
    }
}

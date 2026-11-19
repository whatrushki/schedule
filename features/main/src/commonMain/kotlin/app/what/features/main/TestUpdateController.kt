package app.what.schedule.features.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.what.domain.services.ReleaseHighlight
import app.what.domain.services.ReleaseNotes
import app.what.foundation.services.auto_update.AppUpdateManager
import app.what.foundation.services.auto_update.DownloadState
import app.what.foundation.services.auto_update.UpdateInfo
import app.what.foundation.services.auto_update.UpdateResult

object TestUpdateController {
    var showTestScreen by mutableStateOf(false)

    val testInfo = UpdateInfo(
        version = "1.6.0",
        fileSize = 48_234_496L,
        downloadUrl = "https://github.com/whatrushki/schedule/releases",
        releaseNotes = "Регулярное обновление с улучшением стабильности и исправлением ошибок.",
        releaseNotesData = ReleaseNotes(
            version = "1.6.0",
            title = "Критическое обновление расписания",
            shortDescription = "В этой версии полностью переработан механизм синхронизации с серверами вузов и устранена потеря пар.",
            highlights = listOf(
                ReleaseHighlight(
                    tag = "FIX",
                    icon = "school",
                    title = "Синхронизация РКСИ и РГЭУ РИНХ",
                    description = "Устранена коллизия инициалов при дедупликации преподавателей со схожими фамилиями. Добавлен надежный облачный фоллбэк при недоступности серверов."
                ),
                ReleaseHighlight(
                    tag = "NEW",
                    icon = "sync",
                    title = "Управляемые обязательные обновления",
                    description = "Критические патчи теперь доставляются мгновенно в реальном времени с автоматической установкой."
                )
            ),
            changelog = listOf(
                "РКСИ: Устранена потеря преподавателей с одинаковой фамилией",
                "РИНХ: Надежный облачный кэш при сбоях вузовских серверов",
                "Веб: Улучшена производительность и загрузка расписания"
            )
        )
    )

    class MockManager : AppUpdateManager {
        override var updateInfo by mutableStateOf<UpdateInfo?>(testInfo)
        override var downloadState by mutableStateOf<DownloadState>(DownloadState.Idle)

        override suspend fun checkForUpdates(): UpdateResult = UpdateResult.UpToDate
        override fun handleAction() {
            when (downloadState) {
                is DownloadState.Idle -> downloadState = DownloadState.Downloading(42)
                is DownloadState.Downloading -> downloadState = DownloadState.Completed()
                else -> downloadState = DownloadState.Idle
            }
        }
        override fun cancelDownload() {
            downloadState = DownloadState.Idle
        }
        override fun release() {}
    }

    val mockManager = MockManager()
}

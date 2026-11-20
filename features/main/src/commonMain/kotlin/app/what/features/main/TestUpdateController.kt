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
    var showTestOnboarding by mutableStateOf(false)

    val testInfo = UpdateInfo(
        version = "1.5.3",
        fileSize = 48_234_496L,
        downloadUrl = "https://github.com/whatrushki/schedule/releases",
        releaseNotes = "Регулярное обновление с улучшением стабильности и исправлением ошибок.",
        releaseNotesData = ReleaseNotes(
            version = "1.5.3",
            title = "Что нового",
            shortDescription = "Мы добавили гибкую настройку рекламы для поддержки разработки и улучшили синхронизацию расписания.",
            highlights = listOf(
                ReleaseHighlight(
                    tag = "NEW",
                    icon = "star",
                    title = "Поддержите проект",
                    description = "Нам очень нужна ваша поддержка, чтобы развивать приложение дальше. Будем искренне благодарны за помощь! Вы можете выбрать комфортный формат рекламы или отключить её в любой момент.",
                    settingKey = "support_ads_level"
                )
            ),
            changelog = listOf(
                "РКСИ: Исправлен поиск и дублирование преподавателей с одинаковой фамилией",
                "РИНХ: Надежный облачный кэш при сбоях вузовских серверов",
                "Веб: Устранена ошибка NoDefinitionFoundException в Wasm-версии"
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

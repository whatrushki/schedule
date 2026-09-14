package app.what.schedule.desktop.ui

import androidx.compose.runtime.Composable
import app.what.compose.App
import app.what.schedule.desktop.ui.components.UpdateBanner
import app.what.schedule.desktop.updater.DesktopAppUpdateManager
import io.ktor.client.HttpClient

import androidx.compose.foundation.gestures.DesktopScrollable_desktopKt
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun DesktopApp(httpClient: HttpClient, updateManager: DesktopAppUpdateManager) {
    CompositionLocalProvider(
        DesktopScrollable_desktopKt.getLocalScrollConfig() provides DesktopSmoothScrollConfig
    ) {
        App(
            headerBanner = {
                UpdateBanner(
                    updateInfo = updateManager.updateInfo,
                    onUpdateClick = { updateManager.handleAction() }
                )
            }
        )
    }
}

package app.what.schedule.desktop

import androidx.compose.runtime.remember
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import app.what.data.di.dataModule
import app.what.features.main.di.mainFeatureModule
import app.what.schedule.data.local.settings.AppValues
import app.what.schedule.desktop.ui.DesktopApp
import app.what.schedule.desktop.ui.DesktopWindowThemeEffect
import app.what.foundation.services.auto_update.AppUpdateManager
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatformTools

fun main() {
    System.setProperty("compose.scrolling.smooth.enabled", "false")
    startKoin {
        modules(dataModule, mainFeatureModule)
    }

    application {
        val koin = remember { KoinPlatformTools.defaultContext().get() }
        val settings = remember { koin.get<AppValues>() }
        val updateManager = remember { koin.get<AppUpdateManager>() }
        val appIcon = painterResource("icons/icon.png")

        Window(
            onCloseRequest = {
                updateManager.release()
                exitApplication()
            },
            title = "",
            icon = appIcon,
            state = WindowState(size = DpSize(1100.dp, 750.dp))
        ) {
            DesktopWindowThemeEffect(window, settings)
            DesktopApp()
        }
    }
}

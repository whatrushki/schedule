package app.what.schedule.wasm

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import app.what.compose.App
import app.what.data.di.dataModule
import app.what.features.main.di.mainFeatureModule
import org.koin.core.context.startKoin

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    startKoin {
        modules(dataModule, mainFeatureModule)
    }

    CanvasBasedWindow(title = "WHAT Schedule", canvasElementId = "ComposeTarget") {
        App()
    }
}

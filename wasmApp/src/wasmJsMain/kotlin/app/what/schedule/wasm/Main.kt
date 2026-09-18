package app.what.schedule.wasm

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.CanvasBasedWindow
import app.what.compose.App
import app.what.data.di.dataModule
import app.what.features.main.di.mainFeatureModule
import org.koin.core.context.startKoin

import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    startKoin {
        modules(dataModule, mainFeatureModule)
    }

    ComposeViewport(document.body!!) {
        App()
    }
}

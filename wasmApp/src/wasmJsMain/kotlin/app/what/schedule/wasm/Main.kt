package app.what.schedule.wasm

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalTextInputService
import androidx.compose.ui.text.input.CommitTextCommand
import androidx.compose.ui.text.input.DeleteSurroundingTextCommand
import androidx.compose.ui.text.input.EditCommand
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.PlatformTextInputService
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TextInputService
import androidx.compose.ui.window.CanvasBasedWindow
import app.what.compose.App
import app.what.data.di.dataModule
import app.what.features.main.di.mainFeatureModule
import kotlinx.browser.document
import kotlinx.browser.window
import org.koin.core.context.startKoin
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import org.w3c.dom.events.KeyboardEvent

private fun isMobileBrowser(): Boolean {
    val ua = window.navigator.userAgent.lowercase()
    return ua.contains("android") || ua.contains("iphone") || ua.contains("ipad") || ua.contains("mobile")
}

private class WasmMobileTextInputService : PlatformTextInputService {
    private var onEditCommandCallback: ((List<EditCommand>) -> Unit)? = null
    private var onImeActionPerformedCallback: ((ImeAction) -> Unit)? = null
    private var currentImeAction: ImeAction = ImeAction.Default
    private var currentValue: TextFieldValue = TextFieldValue()

    private val inputElement: HTMLInputElement?
        get() = document.getElementById("mobile-ime-input") as? HTMLInputElement

    init {
        inputElement?.addEventListener("input") { _: Event ->
            val input = inputElement ?: return@addEventListener
            val newText = input.value
            if (newText != currentValue.text) {
                currentValue = TextFieldValue(newText)
                onEditCommandCallback?.invoke(
                    listOf(
                        DeleteSurroundingTextCommand(1000, 1000),
                        CommitTextCommand(newText, 1)
                    )
                )
            }
        }

        inputElement?.addEventListener("keydown") { event: Event ->
            val keyEvent = event as? KeyboardEvent ?: return@addEventListener
            if (keyEvent.key == "Enter") {
                onImeActionPerformedCallback?.invoke(currentImeAction)
                inputElement?.blur()
            }
        }
    }

    override fun startInput(
        value: TextFieldValue,
        imeOptions: ImeOptions,
        onEditCommand: (List<EditCommand>) -> Unit,
        onImeActionPerformed: (ImeAction) -> Unit
    ) {
        currentValue = value
        currentImeAction = imeOptions.imeAction
        onEditCommandCallback = onEditCommand
        onImeActionPerformedCallback = onImeActionPerformed

        inputElement?.let { el ->
            el.value = value.text
            el.focus()
        }
    }

    override fun stopInput() {
        onEditCommandCallback = null
        onImeActionPerformedCallback = null
        inputElement?.blur()
    }

    override fun showSoftwareKeyboard() {
        inputElement?.focus()
    }

    override fun hideSoftwareKeyboard() {
        inputElement?.blur()
    }

    override fun updateState(oldValue: TextFieldValue?, newValue: TextFieldValue) {
        currentValue = newValue
        inputElement?.let { el ->
            if (el.value != newValue.text) {
                el.value = newValue.text
            }
        }
    }

    override fun notifyFocusedRect(rect: Rect) {
    }
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    startKoin {
        modules(dataModule, mainFeatureModule)
    }

    val isMobile = isMobileBrowser()
    val mobileInputService = if (isMobile) TextInputService(WasmMobileTextInputService()) else null

    CanvasBasedWindow(
        title = "WHAT Schedule",
        canvasElementId = "ComposeTarget",
        requestResize = null,
        applyDefaultStyles = true,
        content = {
            if (mobileInputService != null) {
                CompositionLocalProvider(
                    LocalTextInputService provides mobileInputService
                ) {
                    App()
                }
            } else {
                App()
            }
        }
    )
}

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
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.http.contentType
import io.ktor.http.takeFrom
import io.ktor.http.Url
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.decode.Decoder
import coil3.decode.DecodeResult
import coil3.decode.DecodeUtils
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.request.maxBitmapSize
import coil3.size.Precision
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.PlatformContext
import coil3.asImage
import coil3.util.DebugLogger
import okio.Buffer
import okio.use
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Image as SkiaImage
import org.jetbrains.skia.Rect as SkiaRect

private fun isMobileBrowser(): Boolean {
    val ua = window.navigator.userAgent.lowercase()
    val isTouch = window.navigator.maxTouchPoints > 0
    return ua.contains("android") || ua.contains("iphone") || ua.contains("ipad") || ua.contains("mobile") || isTouch
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

        val canvas = document.getElementById("ComposeTarget")
        canvas?.addEventListener("pointerdown") {
            if (onEditCommandCallback != null) {
                inputElement?.focus()
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
            try {
                el.setSelectionRange(value.selection.start, value.selection.end)
            } catch (_: Throwable) {}
        }
    }

    override fun stopInput() {
        onEditCommandCallback = null
        onImeActionPerformedCallback = null
        inputElement?.blur()
        inputElement?.let { el ->
            el.style.left = "0px"
            el.style.top = "0px"
            el.style.width = "1px"
            el.style.height = "1px"
        }
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
        inputElement?.let { el ->
            el.style.left = "${rect.left}px"
            el.style.top = "${rect.top}px"
            el.style.width = "${rect.width.coerceAtLeast(10f)}px"
            el.style.height = "${rect.height.coerceAtLeast(10f)}px"
        }
    }
}

private class WasmNetworkFetcher(
    private val url: String,
    private val client: HttpClient,
    private val options: Options
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val navTag = app.what.foundation.utils.buildTag(app.what.foundation.utils.LogScope.NETWORK, app.what.foundation.utils.LogCat.NET)
        app.what.foundation.services.AppLogger.Companion.Auditor.debug(navTag, "[WasmNetworkFetcher] Fetching: $url")
        val response = client.get(url)
        val bytes = response.body<ByteArray>()
        app.what.foundation.services.AppLogger.Companion.Auditor.debug(navTag, "[WasmNetworkFetcher] Status=${response.status}, bytes=${bytes.size} for $url")
        if (bytes.isEmpty()) {
            throw IllegalStateException("Empty response body for image: $url")
        }
        val buffer = Buffer().write(bytes)
        return SourceFetchResult(
            source = ImageSource(
                source = buffer,
                fileSystem = options.fileSystem
            ),
            mimeType = response.contentType()?.toString(),
            dataSource = DataSource.NETWORK
        )
    }

    class UriFactory(
        private val client: HttpClient
    ) : Fetcher.Factory<Uri> {
        override fun create(
            data: Uri,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher? {
            val scheme = data.scheme
            if (scheme != "http" && scheme != "https") return null
            return WasmNetworkFetcher(data.toString(), client, options)
        }
    }

    class StringFactory(
        private val client: HttpClient
    ) : Fetcher.Factory<String> {
        override fun create(
            data: String,
            options: Options,
            imageLoader: ImageLoader
        ): Fetcher? {
            if (!data.startsWith("http://") && !data.startsWith("https://")) return null
            return WasmNetworkFetcher(data, client, options)
        }
    }
}

private class WasmSkiaDecoder(
    private val source: ImageSource,
    private val options: Options
) : Decoder {
    override suspend fun decode(): DecodeResult {
        val navTag = app.what.foundation.utils.buildTag(app.what.foundation.utils.LogScope.NETWORK, app.what.foundation.utils.LogCat.NET)
        val bytes = source.source().use { it.readByteArray() }
        app.what.foundation.services.AppLogger.Companion.Auditor.debug(navTag, "[WasmSkiaDecoder] Decoding image: ${bytes.size} bytes")

        val skiaImage = SkiaImage.makeFromEncoded(bytes)
        val bitmap = try {
            Bitmap.makeFromImage(skiaImage).apply {
                setImmutable()
            }
        } finally {
            skiaImage.close()
        }

        app.what.foundation.services.AppLogger.Companion.Auditor.debug(navTag, "[WasmSkiaDecoder] Successfully decoded full-res image: ${bitmap.width}x${bitmap.height}")

        return DecodeResult(
            image = bitmap.asImage(),
            isSampled = false,
        )
    }

    class Factory : Decoder.Factory {
        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader
        ): Decoder {
            return WasmSkiaDecoder(result.source, options)
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Suppress("DEPRECATION", "DEPRECATION_ERROR")
fun main() {
    startKoin {
        modules(dataModule, mainFeatureModule)
    }

    val navTag = app.what.foundation.utils.buildTag(app.what.foundation.utils.LogScope.CORE, app.what.foundation.utils.LogCat.NAV)
    app.what.foundation.services.AppLogger.Companion.Auditor.debug(navTag, "Wasm main() started, Koin initialized")

    val imageHttpClient = HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 15 * 1000
            connectTimeoutMillis = 10 * 1000
            socketTimeoutMillis = 15 * 1000
        }
    }.apply {
        requestPipeline.intercept(io.ktor.client.request.HttpRequestPipeline.State) {
            val currentUrl = context.url.buildString()
            val host = try {
                Url(currentUrl).host.lowercase()
            } catch (_: Exception) { "" }
            val isDirect = host.endsWith("github.com") ||
                    host.endsWith("githubusercontent.com") ||
                    host.endsWith("jsdelivr.net") ||
                    host == "localhost" ||
                    host == "127.0.0.1"

            if (!isDirect && (currentUrl.startsWith("http://") || currentUrl.startsWith("https://"))) {
                if (!currentUrl.startsWith("https://schedule.whatrushki-tech.workers.dev")) {
                    context.url.takeFrom("https://schedule.whatrushki-tech.workers.dev/")
                    context.url.parameters.clear()
                    context.url.parameters.append("url", currentUrl)
                }
            }
        }
    }

    SingletonImageLoader.setSafe {
        ImageLoader.Builder(PlatformContext.INSTANCE)
            .components {
                add(WasmNetworkFetcher.StringFactory(imageHttpClient))
                add(WasmNetworkFetcher.UriFactory(imageHttpClient))
                add(WasmSkiaDecoder.Factory())
            }
            .logger(DebugLogger())
            .build()
    }

    val isMobile = isMobileBrowser()
    val mobileInputService = if (isMobile) TextInputService(WasmMobileTextInputService()) else null

    CanvasBasedWindow(
        title = "Schedule",
        canvasElementId = "ComposeTarget",
        requestResize = null,
        applyDefaultStyles = true,
        content = {
            androidx.compose.runtime.LaunchedEffect(Unit) {
                document.getElementById("app-loader")?.classList?.add("loaded")
            }
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

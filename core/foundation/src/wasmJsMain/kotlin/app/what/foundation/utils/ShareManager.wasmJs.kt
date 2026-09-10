package app.what.foundation.utils

import io.ktor.http.encodeURLParameter
import kotlinx.browser.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

@Composable
actual fun rememberPlatformContext(): Any? = null

@Composable
actual fun rememberShareManager(): ShareManager = remember { ShareManager(null) }

actual fun executePlatformShare(channel: ShareChannel, data: ShareData, context: Any?) {
    val text = when (data) {
        is ShareData.Text -> data.text
        is ShareData.Files -> data.text ?: data.title ?: ""
    }

    when (channel) {
        ShareChannel.Clipboard -> {
            window.navigator.clipboard.writeText(text)
        }
        ShareChannel.Telegram -> {
            window.open("https://t.me/share/url?url=" + text.encodeURLParameter(), "_blank")
        }
        ShareChannel.VK -> {
            window.open("https://vk.com/share.php?url=" + text.encodeURLParameter(), "_blank")
        }
        ShareChannel.WhatsApp -> {
            window.open("https://api.whatsapp.com/send?text=" + text.encodeURLParameter(), "_blank")
        }
        ShareChannel.SystemDefault -> {
            window.navigator.clipboard.writeText(text)
        }
    }
}

package app.what.foundation.utils

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.Desktop
import java.net.URI

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
            val selection = StringSelection(text)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
        }
        ShareChannel.Telegram -> {
            runCatching {
                Desktop.getDesktop().browse(URI("https://t.me/share/url?url=" + java.net.URLEncoder.encode(text, "UTF-8")))
            }
        }
        ShareChannel.VK -> {
            runCatching {
                Desktop.getDesktop().browse(URI("https://vk.com/share.php?url=" + java.net.URLEncoder.encode(text, "UTF-8")))
            }
        }
        ShareChannel.WhatsApp -> {
            runCatching {
                Desktop.getDesktop().browse(URI("https://api.whatsapp.com/send?text=" + java.net.URLEncoder.encode(text, "UTF-8")))
            }
        }
        ShareChannel.SystemDefault -> {
            val selection = StringSelection(text)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
        }
    }
}

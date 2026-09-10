package app.what.foundation.utils

import platform.UIKit.UIPasteboard
import platform.UIKit.UIApplication
import platform.Foundation.NSURL

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
            UIPasteboard.generalPasteboard.string = text
        }
        ShareChannel.Telegram -> {
            NSURL.URLWithString("tg://msg?text=" + text)?.let {
                UIApplication.sharedApplication.openURL(it)
            }
        }
        ShareChannel.WhatsApp -> {
            NSURL.URLWithString("whatsapp://send?text=" + text)?.let {
                UIApplication.sharedApplication.openURL(it)
            }
        }
        else -> {
            UIPasteboard.generalPasteboard.string = text
        }
    }
}

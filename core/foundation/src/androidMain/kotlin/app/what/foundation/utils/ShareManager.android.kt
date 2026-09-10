package app.what.foundation.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberPlatformContext(): Any? = LocalContext.current

@Composable
actual fun rememberShareManager(): ShareManager {
    val context = LocalContext.current
    return remember(context) { ShareManager(context) }
}

actual fun executePlatformShare(channel: ShareChannel, data: ShareData, context: Any?) {
    val ctx = context as? Context ?: return
    when (data) {
        is ShareData.Text -> {
            val text = data.text
            when (channel) {
                ShareChannel.Clipboard -> {
                    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText(data.title ?: "Shared Text", text)
                    clipboard.setPrimaryClip(clip)
                }
                ShareChannel.Telegram -> {
                    val uri = Uri.parse("tg://msg?text=" + Uri.encode(text))
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    runCatching { ctx.startActivity(intent) }.onFailure {
                        shareDefault(ctx, text, data.title)
                    }
                }
                ShareChannel.VK -> {
                    val uri = Uri.parse("https://vk.com/share.php?comment=" + Uri.encode(text))
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    runCatching { ctx.startActivity(intent) }.onFailure {
                        shareDefault(ctx, text, data.title)
                    }
                }
                ShareChannel.WhatsApp -> {
                    val uri = Uri.parse("https://api.whatsapp.com/send?text=" + Uri.encode(text))
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    runCatching { ctx.startActivity(intent) }.onFailure {
                        shareDefault(ctx, text, data.title)
                    }
                }
                ShareChannel.SystemDefault -> {
                    shareDefault(ctx, text, data.title)
                }
            }
        }
        is ShareData.Files -> {
            val uris = ArrayList(data.uris.filterIsInstance<Uri>())
            val intent = Intent(if (uris.size > 1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND).apply {
                type = data.mimeType
                if (uris.size > 1) {
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                } else if (uris.isNotEmpty()) {
                    putExtra(Intent.EXTRA_STREAM, uris.first())
                }
                data.text?.let { putExtra(Intent.EXTRA_TEXT, it) }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(intent, data.title ?: "Поделиться"))
        }
    }
}

private fun shareDefault(context: Context, text: String, title: String?) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, title ?: "Поделиться"))
}

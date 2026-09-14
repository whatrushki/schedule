package app.what.foundation.utils

import android.content.Context
import com.jakewharton.processphoenix.ProcessPhoenix

actual val isDesktop: Boolean = false

actual class AppUtils(private val context: Context) {
    actual fun restart() = ProcessPhoenix.triggerRebirth(context)
}
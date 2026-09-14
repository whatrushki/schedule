package app.what.foundation.utils

import kotlinx.browser.window

actual val isDesktop: Boolean = false

actual class AppUtils {
    actual fun restart() {
        window.location.reload()
    }
}

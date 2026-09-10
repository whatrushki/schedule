package app.what.foundation.utils

import kotlinx.browser.window

actual class AppUtils {
    actual fun restart() {
        window.location.reload()
    }
}

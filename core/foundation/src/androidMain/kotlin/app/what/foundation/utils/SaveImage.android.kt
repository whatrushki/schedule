package app.what.foundation.utils

import android.content.Context

actual fun saveImageToDevice(url: String, fileName: String, context: Any?) {
    (context as? Context)?.let {
        DownloadUtils.downloadImage(it, url, fileName)
    }
}

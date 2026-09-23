package app.what.foundation.services.auto_update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

suspend fun GitHubUpdateService.downloadUpdate(
    downloadUrl: String,
    destination: File,
    onProgress: ((DownloadProgress) -> Unit)? = null
): Result<File> {
    return withContext(Dispatchers.IO) {
        var currentUrl = downloadUrl
        var connection: HttpURLConnection? = null
        try {
            var redirects = 0
            while (redirects < 5) {
                val url = URL(currentUrl)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", "schedule-app")
                }
                connection.connect()

                val code = connection.responseCode
                if (code == HttpURLConnection.HTTP_MOVED_PERM ||
                    code == HttpURLConnection.HTTP_MOVED_TEMP ||
                    code == HttpURLConnection.HTTP_SEE_OTHER ||
                    code == 307 || code == 308
                ) {
                    val location = connection.getHeaderField("Location") ?: break
                    connection.disconnect()
                    currentUrl = if (location.startsWith("http")) location else URL(url, location).toString()
                    redirects++
                } else {
                    break
                }
            }

            if (connection == null || connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(Exception("HTTP error: ${connection?.responseCode}"))
            }

            val contentLength = connection.contentLength.toLong()
            val inputStream = connection.inputStream
            var downloadedBytes = 0L
            val buffer = ByteArray(8192)

            destination.outputStream().buffered().use { output ->
                var bytesRead: Int
                var iter = 0
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead

                    val progressValue = if (contentLength <= 0) 0f
                    else downloadedBytes.toFloat() / contentLength

                    if (iter++ > 30) {
                        iter = 0
                        withContext(Dispatchers.Main) {
                            onProgress?.invoke(
                                DownloadProgress(
                                    progress = progressValue,
                                    downloadedBytes = downloadedBytes,
                                    totalBytes = contentLength
                                )
                            )
                        }
                    }
                }
            }

            Result.success(destination)

        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }
}

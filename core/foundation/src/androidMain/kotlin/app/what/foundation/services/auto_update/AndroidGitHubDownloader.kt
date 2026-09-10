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
        var connection: HttpURLConnection? = null
        try {
            val url = URL(downloadUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(Exception("HTTP error: ${connection.responseCode}"))
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

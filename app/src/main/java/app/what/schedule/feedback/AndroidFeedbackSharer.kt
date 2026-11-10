package app.what.schedule.feedback
import app.what.schedule.utils.AppUserIdProvider

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import app.what.domain.services.AttachedFile
import app.what.domain.services.FeedbackSharer
import app.what.foundation.network.monitor.NetworkMonitor
import app.what.foundation.services.AppLogger.Companion.Auditor
import app.what.foundation.utils.CurrentActivityHolder
import app.what.schedule.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.content.PartData
import io.ktor.utils.io.core.ByteReadPacket
import java.io.File

class AndroidFeedbackSharer(
    private val context: Context,
    private val httpClient: HttpClient,
    private val endpointUrl: String = "https://what-assistant.whatrushki-tech.workers.dev/api/feedback"
) : FeedbackSharer {

    override fun pickFiles(onFilesPicked: (List<AttachedFile>) -> Unit) {
        val activity = CurrentActivityHolder.currentActivity as? ComponentActivity
        if (activity == null) {
            Auditor.err("FeedbackSharer", "Current activity is not ComponentActivity, cannot launch picker", null)
            return
        }

        val key = "feedback_picker_${System.currentTimeMillis()}"
        var launcher: androidx.activity.result.ActivityResultLauncher<String>? = null
        launcher = activity.activityResultRegistry.register(
            key,
            ActivityResultContracts.GetMultipleContents()
        ) { uris: List<Uri> ->
            val result = mutableListOf<AttachedFile>()
            val attachmentsDir = File(context.cacheDir, "feedback_attachments").apply { mkdirs() }

            for (uri in uris) {
                try {
                    var name = "attachment_${System.currentTimeMillis()}"
                    var size = 0L

                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (cursor.moveToFirst()) {
                            if (nameIndex != -1) name = cursor.getString(nameIndex) ?: name
                            if (sizeIndex != -1) size = cursor.getLong(sizeIndex)
                        }
                    }

                    val ext = name.substringAfterLast('.', "")
                    val tempFile = File.createTempFile("att_", if (ext.isNotBlank()) ".$ext" else "", attachmentsDir)
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    if (size == 0L) {
                        size = tempFile.length()
                    }

                    result.add(
                        AttachedFile(
                            name = name,
                            path = tempFile.absolutePath,
                            sizeBytes = size,
                            mimeType = context.contentResolver.getType(uri) ?: "*/*"
                        )
                    )
                } catch (e: Exception) {
                    Auditor.err("FeedbackSharer", "Failed to process attached file: ${e.message}", e)
                }
            }

            launcher?.unregister()
            onFilesPicked(result)
        }

        launcher.launch("*/*")
    }

    override suspend fun sendFeedback(
        isBug: Boolean,
        title: String,
        description: String,
        attachedFiles: List<AttachedFile>,
        userId: String
    ): Result<Unit> = runCatching {
        val resolvedUserId = userId.ifBlank {
            AppUserIdProvider.getOrCreateUserId(context)
        }
        val deviceInfo = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, SDK ${Build.VERSION.SDK_INT})"
        val appVersion = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

        val parts = mutableListOf<PartData>()

        fun addFormField(name: String, value: String) {
            parts.add(
                PartData.FormItem(
                    value = value,
                    dispose = {},
                    partHeaders = Headers.build {
                        append(HttpHeaders.ContentDisposition, "form-data; name=\"$name\"")
                    }
                )
            )
        }

        fun addFilePart(name: String, filename: String, contentType: String, bytes: ByteArray) {
            parts.add(
                PartData.BinaryItem(
                    provider = { ByteReadPacket(bytes) },
                    dispose = {},
                    partHeaders = Headers.build {
                        append(HttpHeaders.ContentDisposition, "form-data; name=\"$name\"; filename=\"$filename\"")
                        append(HttpHeaders.ContentType, contentType)
                    }
                )
            )
        }

        addFormField("type", if (isBug) "bug" else "feature")
        addFormField("title", title)
        addFormField("description", description)
        addFormField("device_info", deviceInfo)
        addFormField("app_version", appVersion)
        addFormField("user_id", resolvedUserId)

        if (isBug) {
            try {
                val auditLogs = Auditor.exportLogs().takeIf { it.isNotBlank() } ?: "Нет записей аудита"
                val netLogs = NetworkMonitor.exportRequests().takeIf { it.isNotBlank() } ?: "Нет сетевых логов"

                val combinedLogs = buildString {
                    appendLine("========================================")
                    appendLine("SCHEDULE DIAGNOSTIC LOGS")
                    appendLine("========================================")
                    appendLine("Версия: $appVersion")
                    appendLine("Устройство: $deviceInfo")
                    appendLine()
                    appendLine("========================================")
                    appendLine("ЖУРНАЛ АУДИТА (AUDIT LOGS)")
                    appendLine("========================================")
                    appendLine(auditLogs)
                    appendLine()
                    appendLine("========================================")
                    appendLine("СЕТЕВЫЕ ЗАПРОСЫ (NETWORK MONITOR)")
                    appendLine("========================================")
                    appendLine(netLogs)
                }

                addFilePart(
                    name = "diagnostic_logs",
                    filename = "schedule_diagnostic_logs.txt",
                    contentType = "text/plain; charset=utf-8",
                    bytes = combinedLogs.encodeToByteArray()
                )
            } catch (e: Exception) {
                Auditor.err("FeedbackSharer", "Failed to collect diagnostic logs: ${e.message}", e)
            }
        }

        // User-attached files
        for ((index, att) in attachedFiles.withIndex()) {
            val f = File(att.path)
            if (f.exists()) {
                val fieldName = "file_${index + 1}"
                addFilePart(
                    name = fieldName,
                    filename = att.name,
                    contentType = att.mimeType ?: "application/octet-stream",
                    bytes = f.readBytes()
                )
            }
        }

        val response = httpClient.post(endpointUrl) {
            setBody(MultiPartFormDataContent(parts))
        }

        if (response.status.value !in 200..299) {
            val body = response.bodyAsText()
            error("Ошибка отправки: HTTP ${response.status.value} ($body)")
        }
    }
}

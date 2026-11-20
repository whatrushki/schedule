package app.what.data.feedback

import app.what.domain.services.AttachedFile
import app.what.domain.services.FeedbackSharer
import app.what.foundation.services.AppLogger.Companion.Auditor
import app.what.foundation.network.monitor.NetworkMonitor
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
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

class DesktopFeedbackSharer(
    private val httpClient: HttpClient,
    private val endpointUrl: String = "https://what-assistant.whatrushki-tech.workers.dev/api/feedback"
) : FeedbackSharer {

    override fun pickFiles(onFilesPicked: (List<AttachedFile>) -> Unit) {
        SwingUtilities.invokeLater {
            try {
                val chooser = JFileChooser().apply {
                    isMultiSelectionEnabled = true
                    dialogTitle = "Выберите файлы для прикрепления"
                    fileFilter = FileNameExtensionFilter(
                        "Изображения и логи (*.png, *.jpg, *.jpeg, *.log, *.txt)",
                        "png", "jpg", "jpeg", "log", "txt"
                    )
                }
                val returnVal = chooser.showOpenDialog(null)
                if (returnVal == JFileChooser.APPROVE_OPTION) {
                    val files = chooser.selectedFiles.mapNotNull { file ->
                        if (file.exists() && file.isFile) {
                            AttachedFile(
                                name = file.name,
                                sizeBytes = file.length(),
                                path = file.absolutePath,
                                mimeType = when (file.extension.lowercase()) {
                                    "png" -> "image/png"
                                    "jpg", "jpeg" -> "image/jpeg"
                                    "txt", "log" -> "text/plain"
                                    else -> "application/octet-stream"
                                }
                            )
                        } else null
                    }
                    if (files.isNotEmpty()) {
                        onFilesPicked(files)
                    }
                }
            } catch (e: Exception) {
                Auditor.err("DesktopFeedbackSharer", "Ошибка выбора файлов: ${e.message}", e)
            }
        }
    }

    override suspend fun sendFeedback(
        isBug: Boolean,
        title: String,
        description: String,
        attachedFiles: List<AttachedFile>,
        userId: String
    ): Result<Unit> = runCatching {
        val resolvedUserId = userId.ifBlank { "desktop_user" }
        val osName = System.getProperty("os.name") ?: "Desktop"
        val osArch = System.getProperty("os.arch") ?: "unknown"
        val osVersion = System.getProperty("os.version") ?: ""
        val deviceInfo = "$osName ($osArch, $osVersion)"
        val appVersion = "Desktop 1.5.3"

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
                    appendLine("SCHEDULE DIAGNOSTIC LOGS (DESKTOP)")
                    appendLine("========================================")
                    appendLine("Версия: $appVersion")
                    appendLine("Система: $deviceInfo")
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
                Auditor.err("DesktopFeedbackSharer", "Failed to collect diagnostic logs: ${e.message}", e)
            }
        }

        for ((index, att) in attachedFiles.withIndex()) {
            val fileBytes = att.bytes ?: if (att.path.isNotBlank()) {
                val f = File(att.path)
                if (f.exists()) f.readBytes() else null
            } else null

            if (fileBytes != null) {
                val fieldName = "file_${index + 1}"
                addFilePart(
                    name = fieldName,
                    filename = att.name,
                    contentType = att.mimeType ?: "application/octet-stream",
                    bytes = fileBytes
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

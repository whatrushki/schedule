package app.what.data.feedback

import app.what.domain.services.AttachedFile
import app.what.domain.services.FeedbackSharer
import app.what.foundation.network.monitor.NetworkMonitor
import app.what.foundation.services.AppLogger.Companion.Auditor
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.content.PartData
import io.ktor.utils.io.core.ByteReadPacket
import kotlinx.browser.document
import kotlinx.browser.window
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get
import org.w3c.dom.HTMLInputElement
import org.w3c.files.FileReader

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
class WebFeedbackSharer(
    private val httpClient: HttpClient,
    private val endpointUrl: String = "https://what-assistant.whatrushki-tech.workers.dev/api/feedback"
) : FeedbackSharer {

    override fun pickFiles(onFilesPicked: (List<AttachedFile>) -> Unit) {
        try {
            val input = document.createElement("input") as HTMLInputElement
            input.type = "file"
            input.multiple = true
            input.accept = "image/*,.log,.txt"
            input.onchange = {
                val fileList = input.files
                if (fileList != null && fileList.length > 0) {
                    val count = fileList.length
                    val loadedFiles = mutableListOf<AttachedFile>()
                    var filesRemaining = count

                    for (i in 0 until count) {
                        val file = fileList.item(i) ?: continue
                        val reader = FileReader()
                        reader.onload = {
                            try {
                                val buffer = reader.result as ArrayBuffer
                                val int8Array = Int8Array(buffer)
                                val bytes = ByteArray(int8Array.length) { idx -> int8Array[idx] }
                                loadedFiles.add(
                                    AttachedFile(
                                        name = file.name,
                                        sizeBytes = file.size.toDouble().toLong(),
                                        mimeType = file.type.ifBlank { "application/octet-stream" },
                                        bytes = bytes
                                    )
                                )
                            } catch (e: Exception) {
                                Auditor.err("WebFeedbackSharer", "Ошибка чтения файла: ${e.message}", e)
                            } finally {
                                filesRemaining--
                                if (filesRemaining <= 0 && loadedFiles.isNotEmpty()) {
                                    onFilesPicked(loadedFiles)
                                }
                            }
                        }
                        reader.onerror = {
                            filesRemaining--
                            if (filesRemaining <= 0 && loadedFiles.isNotEmpty()) {
                                onFilesPicked(loadedFiles)
                            }
                        }
                        reader.readAsArrayBuffer(file)
                    }
                }
            }
            input.click()
        } catch (e: Exception) {
            Auditor.err("WebFeedbackSharer", "Ошибка выбора файлов в веб-версии: ${e.message}", e)
        }
    }

    override suspend fun sendFeedback(
        isBug: Boolean,
        title: String,
        description: String,
        attachedFiles: List<AttachedFile>,
        userId: String
    ): Result<Unit> = runCatching {
        val resolvedUserId = userId.ifBlank { "web_user" }
        val userAgent = window.navigator.userAgent
        val deviceInfo = "Web ($userAgent)"
        val appVersion = "Web 1.5.3"

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
                    appendLine("SCHEDULE DIAGNOSTIC LOGS (WEB)")
                    appendLine("========================================")
                    appendLine("Версия: $appVersion")
                    appendLine("Браузер: $deviceInfo")
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
                Auditor.err("WebFeedbackSharer", "Failed to collect diagnostic logs: ${e.message}", e)
            }
        }

        for ((index, att) in attachedFiles.withIndex()) {
            val fileBytes = att.bytes
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

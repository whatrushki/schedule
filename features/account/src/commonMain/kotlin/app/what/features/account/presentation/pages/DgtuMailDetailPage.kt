package app.what.schedule.features.insts.dgtu.presentation.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.what.data.remote.utils.fromHtml
import app.what.foundation.core.Listener
import app.what.foundation.data.RemoteState
import app.what.foundation.ui.Gap
import app.what.foundation.ui.bclick
import app.what.schedule.dgtu.models.DGTUApi
import app.what.schedule.features.insts.dgtu.domain.models.DgtuEvent
import app.what.schedule.features.insts.dgtu.domain.models.DgtuState
import app.what.schedule.ui.components.AsyncImageWithFallback
import app.what.schedule.ui.components.Fallback

@Composable
fun DgtuMailDetailPage(
    threadId: Int,
    messageId: Int,
    state: State<DgtuState>,
    listener: Listener<DgtuEvent>,
    onBack: () -> Unit
) {
    DisposableEffect(Unit) {
        onDispose {
            listener(DgtuEvent.CloseMailDetail)
        }
    }

    val uriHandler = LocalUriHandler.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.surface),
        contentAlignment = Alignment.TopCenter
    ) {
        when (state.value.mailDetailFetchState) {
            is RemoteState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = colorScheme.primary)
                }
            }

            is RemoteState.Error -> {
                Fallback(
                    "Не удалось загрузить сообщение",
                    Modifier.fillMaxSize(),
                    "Попробовать снова" to {
                        listener(DgtuEvent.MailOpened(threadId, messageId))
                    }
                )
            }

            else -> {
                val thread = state.value.mailDetail
                if (thread != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentWidth(Alignment.CenterHorizontally)
                            .widthIn(max = 860.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 18.dp, vertical = 16.dp)
                    ) {
                        Text(
                            text = thread.theme.ifBlank { "Без темы" },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface,
                            lineHeight = 28.sp
                        )

                        Gap(16)

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shapes.large)
                                .background(colorScheme.surfaceContainerLow)
                                .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.35f), shapes.large)
                                .padding(14.dp)
                        ) {
                            val photoUrl = if (thread.photoLinkUserID.isNotBlank()) {
                                "https://lk.donstu.ru${thread.photoLinkUserID}"
                            } else ""

                            AsyncImageWithFallback(
                                photoUrl,
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                            )

                            Gap(12)

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = formatName(thread.userIdFromMessage),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )

                                    Gap(8)

                                    Text(
                                        text = formatDateTime(thread.dispatchDate),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }

                                if (thread.userIdToMessage.isNotBlank()) {
                                    Gap(2)
                                    Text(
                                        text = "Кому: ${formatName(thread.userIdToMessage)}",
                                        fontSize = 12.sp,
                                        color = colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Gap(18)

                        val rawMessage = thread.message.message.takeIf { it.isNotBlank() }
                            ?: thread.message.markdownMessage
                            ?: thread.message.htmlMessage
                            ?: ""

                        val annotatedBody = cleanMailContent(rawMessage)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shapes.large)
                                .background(colorScheme.surfaceContainer)
                                .padding(16.dp)
                        ) {
                            Text(
                                text = annotatedBody,
                                style = MaterialTheme.typography.bodyLarge,
                                lineHeight = 22.sp,
                                fontSize = 15.sp,
                                color = colorScheme.onSurface
                            )
                        }

                        if (thread.files.isNotEmpty()) {
                            Gap(22)
                            Text(
                                text = "Прикрепленные файлы (${thread.files.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface
                            )
                            Gap(10)

                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                for (file in thread.files) {
                                    MailAttachmentItem(file) {
                                        val downloadUrl = "https://lk.donstu.ru${file.path}"
                                        uriHandler.openUri(downloadUrl)
                                    }
                                }
                            }
                        }

                        Gap(32)
                    }
                }
            }
        }
    }
}

@Composable
private fun MailAttachmentItem(
    file: DGTUApi.Models.File,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shapes.medium)
            .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.35f), shapes.medium)
            .background(colorScheme.surfaceContainerLow)
            .bclick { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(shapes.small)
                .background(colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.AttachFile,
                contentDescription = null,
                tint = colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }

        Gap(12)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.fileName,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Gap(2)
            Text(
                text = formatFileSize(file.size),
                fontSize = 12.sp,
                color = colorScheme.onSurfaceVariant
            )
        }

        Gap(8)

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ArrowDownward,
                contentDescription = "Скачать",
                tint = colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes <= 0 -> "0 Б"
    bytes < 1024 -> "$bytes Б"
    bytes < 1024 * 1024 -> "${bytes / 1024} КБ"
    else -> "${(bytes / (1024 * 1024 * 1.0f)).let { kotlin.math.round(it * 10) / 10 }} МБ"
}

private fun cleanMailContent(raw: String): AnnotatedString {
    var text = raw.trim()
    if (text.isEmpty()) return AnnotatedString("Нет содержимого")

    // Replace non-breaking spaces with standard space
    text = text.replace("&nbsp;", " ").replace("\u00A0", " ")

    // Remove empty paragraphs or divs like <p></p>, <p><br></p>, <p>&nbsp;</p>, <div><br></div>
    val emptyBlockRegex = Regex("""<(p|div)[^>]*>\s*(<br\s*/?>|\s)*</\1>""", RegexOption.IGNORE_CASE)
    while (emptyBlockRegex.containsMatchIn(text)) {
        text = text.replace(emptyBlockRegex, "")
    }

    // Collapse multiple consecutive <br> tags into single <br/>
    text = text.replace(Regex("""(<br\s*/?>\s*){2,}""", RegexOption.IGNORE_CASE), "<br/>")

    // Remove leading and trailing <br> tags
    text = text.replace(Regex("""^(\s*<br\s*/?>\s*)+""", RegexOption.IGNORE_CASE), "")
    text = text.replace(Regex("""(\s*<br\s*/?>\s*)+$""", RegexOption.IGNORE_CASE), "")

    val parsed = AnnotatedString.fromHtml(text.trim())

    // Trim AnnotatedString from start and end
    val trimmed = parsed.trimMailBody()
    val rawText = trimmed.text
    if (!rawText.contains("\n\n")) return trimmed

    // Collapse multiple consecutive newlines (\n\n, \n\n\n, etc.) into at most 1 newline
    val builder = AnnotatedString.Builder()
    var consecutiveNewlines = 0
    val oldToNewIndex = IntArray(rawText.length + 1)
    var newLen = 0

    for (i in rawText.indices) {
        val c = rawText[i]
        oldToNewIndex[i] = newLen
        if (c == '\n') {
            consecutiveNewlines++
            if (consecutiveNewlines <= 1) {
                builder.append('\n')
                newLen++
            }
        } else {
            consecutiveNewlines = 0
            builder.append(c)
            newLen++
        }
    }
    oldToNewIndex[rawText.length] = newLen

    for (styleRange in trimmed.spanStyles) {
        val newStart = oldToNewIndex[styleRange.start.coerceIn(0, rawText.length)]
        val newEnd = oldToNewIndex[styleRange.end.coerceIn(0, rawText.length)]
        if (newEnd > newStart) {
            builder.addStyle(styleRange.item, newStart, newEnd)
        }
    }

    for (linkRange in trimmed.getLinkAnnotations(0, rawText.length)) {
        val newStart = oldToNewIndex[linkRange.start.coerceIn(0, rawText.length)]
        val newEnd = oldToNewIndex[linkRange.end.coerceIn(0, rawText.length)]
        if (newEnd > newStart) {
            when (val item = linkRange.item) {
                is LinkAnnotation.Url -> builder.addLink(item, newStart, newEnd)
                is LinkAnnotation.Clickable -> builder.addLink(item, newStart, newEnd)
            }
        }
    }

    return builder.toAnnotatedString().trimMailBody()
}

private fun AnnotatedString.trimMailBody(): AnnotatedString {
    val raw = this.text
    var start = 0
    while (start < raw.length && (raw[start].isWhitespace() || raw[start] == '\u00A0')) {
        start++
    }
    var end = raw.length
    while (end > start && (raw[end - 1].isWhitespace() || raw[end - 1] == '\u00A0')) {
        end--
    }
    return if (start == 0 && end == raw.length) this
    else if (start >= end) AnnotatedString("")
    else this.subSequence(start, end)
}

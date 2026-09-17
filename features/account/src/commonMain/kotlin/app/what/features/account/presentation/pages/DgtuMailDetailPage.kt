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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    tint = colorScheme.onSurface
                )
            }
            Gap(8)
            Text(
                "Сообщение",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.5f))

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
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    ) {
                        Text(
                            text = thread.theme.ifBlank { "Без темы" },
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )

                        Gap(16)

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shapes.medium)
                                .background(colorScheme.surfaceContainer)
                                .padding(12.dp)
                        ) {
                            val photoUrl = if (thread.photoLinkUserID.isNotBlank()) {
                                "https://lk.donstu.ru${thread.photoLinkUserID}"
                            } else ""

                            AsyncImageWithFallback(
                                photoUrl,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                            )

                            Gap(12)

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = formatName(thread.userIdFromMessage),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = colorScheme.onSurface
                                )
                                if (thread.userIdToMessage.isNotBlank()) {
                                    Text(
                                        text = "Кому: ${formatName(thread.userIdToMessage)}",
                                        fontSize = 12.sp,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = formatDateTime(thread.dispatchDate),
                                    fontSize = 12.sp,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Gap(16)

                        val rawMessage = thread.message.message.takeIf { it.isNotBlank() }
                            ?: thread.message.markdownMessage
                            ?: thread.message.htmlMessage
                            ?: ""

                        val annotatedBody = if (rawMessage.isNotBlank()) {
                            AnnotatedString.fromHtml(rawMessage)
                        } else {
                            AnnotatedString("Нет содержимого")
                        }

                        Text(
                            text = annotatedBody,
                            style = MaterialTheme.typography.bodyLarge,
                            lineHeight = 24.sp,
                            color = colorScheme.onSurface
                        )

                        if (thread.files.isNotEmpty()) {
                            Gap(24)
                            Text(
                                text = "Прикрепленные файлы (${thread.files.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface
                            )
                            Gap(8)

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
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .background(colorScheme.surfaceContainerLow)
            .bclick { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.AttachFile,
            contentDescription = null,
            tint = colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )

        Gap(12)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.fileName,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatFileSize(file.size),
                fontSize = 12.sp,
                color = colorScheme.onSurfaceVariant
            )
        }

        Gap(8)

        Icon(
            imageVector = Icons.Default.ArrowDownward,
            contentDescription = "Скачать",
            tint = colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes <= 0 -> "0 Б"
    bytes < 1024 -> "$bytes Б"
    bytes < 1024 * 1024 -> "${bytes / 1024} КБ"
    else -> "${(bytes / (1024 * 1024 * 1.0f)).let { kotlin.math.round(it * 10) / 10 }} МБ"
}

package app.what.schedule.features.settings.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.what.domain.services.ReleaseNotes
import app.what.foundation.services.auto_update.AppUpdateManager
import app.what.foundation.services.auto_update.DownloadState
import app.what.foundation.services.auto_update.UpdateInfo
import app.what.foundation.ui.Gap
import app.what.foundation.ui.icons.WHATIcons
import app.what.foundation.ui.icons.filled.ApkInstall
import app.what.foundation.ui.icons.filled.Download
import app.what.foundation.ui.icons.filled.DownloadError
import app.what.foundation.ui.icons.filled.ReleaseAlert

@Composable
fun UpdateChangelogSheet(
    info: UpdateInfo,
    releaseNotes: ReleaseNotes?,
    manager: AppUpdateManager,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val downloadState = manager.downloadState
    val items = releaseNotes?.allChangelogItems()
        ?: info.releaseNotes?.lines()?.filter { it.isNotBlank() }
        ?: emptyList()
    val title = releaseNotes?.title ?: "Доступно обновление"
    val shortDesc = releaseNotes?.shortDescription
    val sizeStr = formatFileSize(info.fileSize)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Заголовок с кнопкой закрытия
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = WHATIcons.ReleaseAlert,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column {
                    Text(
                        text = title,
                        style = typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                    Text(
                        text = "Версия ${info.version}$sizeStr",
                        style = typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Закрыть",
                    tint = colorScheme.onSurfaceVariant
                )
            }
        }

        Gap(12)

        // Краткое описание
        if (!shortDesc.isNullOrBlank()) {
            Text(
                text = shortDesc,
                style = typography.bodyMedium,
                color = colorScheme.onSurfaceVariant
            )
            Gap(12)
        }

        // Список изменений
        Text(
            text = "Что нового:",
            style = typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onSurface
        )

        Gap(8)

        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .heightIn(max = 340.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (items.isEmpty()) {
                Text(
                    text = "Регулярное обновление с улучшением стабильности и исправлением ошибок.",
                    style = typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
            } else {
                items.forEach { change ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .background(colorScheme.surfaceContainerLow)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(colorScheme.primary)
                        )
                        Gap(10)
                        Text(
                            text = change,
                            style = typography.bodySmall,
                            color = colorScheme.onSurface,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }

        Gap(16)

        // Блок действия / прогресса загрузки
        UpdateActionBlock(
            info = info,
            state = downloadState,
            onAction = { manager.handleAction() }
        )
    }
}

@Composable
private fun UpdateActionBlock(
    info: UpdateInfo,
    state: DownloadState,
    onAction: () -> Unit
) {
    when (state) {
        is DownloadState.Idle -> {
            Button(
                onClick = onAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(WHATIcons.Download, null, modifier = Modifier.size(20.dp))
                Gap(8)
                Text("Скачать обновление")
            }
        }

        is DownloadState.Preparing -> {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape),
                    color = colorScheme.primary
                )
                Text(
                    text = "Подготовка файла к загрузке...",
                    style = typography.labelSmall,
                    color = colorScheme.primary
                )
            }
        }

        is DownloadState.Downloading -> {
            val percent = state.progress
            val fraction = (percent / 100f).coerceIn(0f, 1f)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(CircleShape),
                    color = colorScheme.primary,
                    trackColor = colorScheme.primary.copy(0.2f)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Загрузка обновления...",
                        style = typography.labelSmall,
                        color = colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$percent%",
                        style = typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.primary
                    )
                }
            }
        }

        is DownloadState.Completed -> {
            Button(
                onClick = onAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(WHATIcons.ApkInstall, null, modifier = Modifier.size(20.dp))
                Gap(8)
                Text("Установить обновление")
            }
        }

        is DownloadState.Error -> {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Ошибка: ${state.message}",
                    style = typography.bodySmall,
                    color = colorScheme.error
                )
                Button(
                    onClick = onAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(WHATIcons.DownloadError, null, modifier = Modifier.size(20.dp))
                    Gap(8)
                    Text("Попробовать снова")
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return ""
    val mb = bytes / (1024.0 * 1024.0)
    val formatted = (mb * 10).toInt() / 10.0
    return " • $formatted МБ"
}

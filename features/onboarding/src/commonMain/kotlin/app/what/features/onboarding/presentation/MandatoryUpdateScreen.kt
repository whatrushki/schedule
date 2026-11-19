package app.what.features.onboarding.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.what.domain.constants.AppConstants
import app.what.domain.services.ReleaseHighlight
import app.what.domain.services.ReleaseNotes
import app.what.foundation.services.auto_update.AppUpdateManager
import app.what.foundation.services.auto_update.DownloadState
import app.what.foundation.services.auto_update.UpdateInfo
import app.what.foundation.ui.Gap
import app.what.foundation.ui.PlatformBackHandler
import app.what.foundation.ui.icons.WHATIcons
import app.what.foundation.ui.icons.filled.ApkInstall
import app.what.foundation.ui.icons.filled.Download
import app.what.foundation.ui.icons.filled.DownloadError
import kotlin.math.roundToInt

@Composable
fun MandatoryUpdateScreen(
    info: UpdateInfo,
    manager: AppUpdateManager,
    isRuStore: Boolean,
    onExitApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    PlatformBackHandler(enabled = true) {
        onExitApp()
    }

    val releaseNotes = info.releaseNotesData
    val downloadState = manager.downloadState

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        // Gradient covers entire device screen edge-to-edge with no padding clipping
        MandatoryUpdateAmbientBackground()

        val isWide = maxWidth >= 640.dp

        if (isWide) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colorScheme.scrim.copy(alpha = 0.35f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 500.dp)
                        .fillMaxWidth(0.9f)
                        .heightIn(max = 700.dp)
                        .fillMaxHeight(0.88f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(colorScheme.surface)
                        .border(
                            1.dp,
                            colorScheme.outlineVariant.copy(alpha = 0.3f),
                            RoundedCornerShape(24.dp)
                        )
                        .padding(horizontal = 24.dp, vertical = 20.dp)
                ) {
                    MandatoryUpdateCardContent(
                        info = info,
                        releaseNotes = releaseNotes,
                        manager = manager,
                        downloadState = downloadState,
                        isRuStore = isRuStore,
                        onExitApp = onExitApp
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                MandatoryUpdateCardContent(
                    info = info,
                    releaseNotes = releaseNotes,
                    manager = manager,
                    downloadState = downloadState,
                    isRuStore = isRuStore,
                    onExitApp = onExitApp
                )
            }
        }
    }
}

@Composable
private fun MandatoryUpdateAmbientBackground() {
    val color1 = colorScheme.primaryContainer.copy(0.35f)
    val surface = colorScheme.surface

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(color1, surface),
                center = Offset(size.width / 2f, size.height * 0.2f),
                radius = size.maxDimension * 0.85f
            )
        )
    }
}

@Composable
private fun MandatoryUpdateCardContent(
    info: UpdateInfo,
    releaseNotes: ReleaseNotes?,
    manager: AppUpdateManager,
    downloadState: DownloadState,
    isRuStore: Boolean,
    onExitApp: () -> Unit
) {
    val items = releaseNotes?.allChangelogItems()
        ?: info.releaseNotes?.let { ReleaseNotes.parseMarkdownChangelog(it) }
        ?: emptyList()
    val highlights = releaseNotes?.highlights ?: emptyList()
    val sizeStr = formatFileSize(info.fileSize)
    val primaryColor = colorScheme.primary

    val glowBrush = Brush.radialGradient(
        colors = listOf(primaryColor.copy(0.25f), Color.Transparent)
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Gap(12)

            // Hero Icon with subtle ambient glow
            Box(
                modifier = Modifier.size(110.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(110.dp)) {
                    drawCircle(brush = glowBrush)
                }
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = WHATIcons.ApkInstall,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = primaryColor
                    )
                }
            }

            Gap(16)

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colorScheme.primary.copy(alpha = 0.12f))
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "Версия ${info.version}",
                    style = typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.primary
                )
            }

            Gap(12)

            Text(
                text = "Необходимо обновление",
                style = typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Gap(4)

            Text(
                text = "Для работы расписания установите актуальную версию$sizeStr",
                style = typography.bodyMedium,
                color = colorScheme.secondary,
                textAlign = TextAlign.Center
            )

            Gap(20)

            if (highlights.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    highlights.forEach { highlight ->
                        HighlightCard(highlight)
                    }
                }
            } else if (items.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items.forEach { item ->
                        ChangelogItem(item)
                    }
                }
            }

            Gap(16)
        }

        // Bottom action bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isRuStore) {
                RuStoreActionBlock(onAction = { manager.handleAction() })
            } else {
                GitHubActionBlock(
                    downloadState = downloadState,
                    onAction = { manager.handleAction() },
                    onCancel = { manager.cancelDownload() }
                )
            }

            TextButton(
                onClick = onExitApp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Закрыть приложение",
                    style = typography.labelLarge,
                    color = colorScheme.secondary
                )
            }
        }
    }
}

@Composable
private fun HighlightCard(highlight: ReleaseHighlight) {
    val icon = resolveHighlightIcon(highlight.icon)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(colorScheme.surfaceContainerLow)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = colorScheme.primary
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = highlight.title,
                style = typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface
            )
            if (highlight.description.isNotBlank()) {
                Gap(1)
                Text(
                    text = highlight.description,
                    style = typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun ChangelogItem(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(colorScheme.surfaceContainerLow.copy(alpha = 0.7f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(colorScheme.primary)
        )
        Text(
            text = text,
            style = typography.bodySmall,
            color = colorScheme.onSurface,
            lineHeight = 17.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun RuStoreActionBlock(onAction: () -> Unit) {
    Button(
        onClick = onAction,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Icon(WHATIcons.Download, contentDescription = null, modifier = Modifier.size(20.dp))
        Gap(8)
        Text("Обновить в RuStore", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun GitHubActionBlock(
    downloadState: DownloadState,
    onAction: () -> Unit,
    onCancel: () -> Unit
) {
    when (downloadState) {
        is DownloadState.Idle -> {
            Button(
                onClick = onAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(WHATIcons.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                Gap(8)
                Text("Скачать и обновить", fontWeight = FontWeight.Bold)
            }
        }

        is DownloadState.Preparing -> {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = colorScheme.primary
                )
                Text(
                    text = "Подготовка к загрузке...",
                    style = typography.labelMedium,
                    color = colorScheme.primary
                )
            }
        }

        is DownloadState.Downloading -> {
            val percent = downloadState.progress
            val fraction = (percent / 100f).coerceIn(0f, 1f)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = colorScheme.primary,
                    trackColor = colorScheme.primary.copy(alpha = 0.2f)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Загрузка: $percent%",
                        style = typography.labelSmall,
                        color = colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    TextButton(onClick = onCancel) {
                        Text("Отмена", style = typography.labelSmall, color = colorScheme.error)
                    }
                }
            }
        }

        is DownloadState.Completed -> {
            Button(
                onClick = onAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(WHATIcons.ApkInstall, contentDescription = null, modifier = Modifier.size(20.dp))
                Gap(8)
                Text("Установить обновление", fontWeight = FontWeight.Bold)
            }
        }

        is DownloadState.Error -> {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(colorScheme.errorContainer.copy(alpha = 0.4f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = WHATIcons.DownloadError,
                        contentDescription = null,
                        tint = colorScheme.error,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = downloadState.message,
                        style = typography.bodySmall,
                        color = colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedButton(
                    onClick = onAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("Попробовать снова")
                }
            }
        }
    }
}

private fun resolveHighlightIcon(iconName: String?): ImageVector {
    return when (iconName?.lowercase()?.trim()) {
        "sync", "refresh" -> Icons.Default.Sync
        "school", "academic", "teacher" -> Icons.Default.School
        "palette", "theme", "color" -> Icons.Default.Palette
        "security", "privacy", "lock" -> Icons.Default.Security
        "tune", "settings", "sliders" -> Icons.Default.Tune
        "widgets", "widget" -> Icons.Default.Widgets
        "star", "heart", "favorite" -> Icons.Default.Star
        "speed", "performance", "fast" -> Icons.Default.Speed
        "sparkles", "magic", "auto_awesome" -> Icons.Default.AutoAwesome
        else -> Icons.Default.AutoAwesome
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return ""
    val mb = bytes.toDouble() / (1024 * 1024)
    return " • ${(mb * 10).roundToInt() / 10.0} МБ"
}

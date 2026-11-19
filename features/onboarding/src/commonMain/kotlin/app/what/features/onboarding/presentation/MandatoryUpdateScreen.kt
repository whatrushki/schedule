package app.what.features.onboarding.presentation

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
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
import app.what.foundation.ui.icons.filled.ReleaseAlert
import kotlin.math.roundToInt

@Composable
fun MandatoryUpdateScreen(
    info: UpdateInfo,
    manager: AppUpdateManager,
    isRuStore: Boolean,
    onExitApp: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Перехватываем системную кнопку "Назад", чтобы экран нельзя было закрыть
    PlatformBackHandler(enabled = true) {
        onExitApp()
    }

    val releaseNotes = info.releaseNotesData
    val downloadState = manager.downloadState

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val isWide = maxWidth >= 640.dp

        if (isWide) {
            // Затемняющий фон (scrim) без закрытия по клику
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Намеренно пусто: клик мимо не закрывает обязательное обновление
                    )
            )

            // Центрированная карточка для десктопа / веба
            Box(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth(0.9f)
                    .heightIn(max = 760.dp)
                    .fillMaxHeight(0.9f)
                    .clip(RoundedCornerShape(28.dp))
                    .background(colorScheme.surface)
                    .border(
                        1.dp,
                        colorScheme.outlineVariant.copy(alpha = 0.35f),
                        RoundedCornerShape(28.dp)
                    )
                    .padding(24.dp)
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
        } else {
            // Полноэкранный вид для мобильных устройств
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colorScheme.surface)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
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
    val title = releaseNotes?.title ?: "Важное обновление приложения"
    val shortDesc = releaseNotes?.shortDescription
    val sizeStr = formatFileSize(info.fileSize)

    val infiniteTransition = rememberInfiniteTransition()
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse
        )
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Верхняя часть со скроллом (информация о версии и изменениях)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.Start
        ) {
            // Заголовок с пульсирующей иконкой и бейджем обязательности
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .scale(pulseScale)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colorScheme.errorContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = WHATIcons.ReleaseAlert,
                        contentDescription = null,
                        tint = colorScheme.error,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(colorScheme.error.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "ОБЯЗАТЕЛЬНОЕ ОБНОВЛЕНИЕ",
                            style = typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.error
                        )
                    }
                    Gap(4)
                    Text(
                        text = "Доступна версия ${info.version}",
                        style = typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                    Text(
                        text = "Ваша текущая версия: v${AppConstants.VERSION_NAME}$sizeStr",
                        style = typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }

            Gap(16)

            // Описание релиза
            Text(
                text = title,
                style = typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface
            )

            if (!shortDesc.isNullOrBlank()) {
                Gap(6)
                Text(
                    text = shortDesc,
                    style = typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            }

            Gap(16)

            // Блок "Что исправлено в следующей"
            Text(
                text = "Что исправлено и улучшено:",
                style = typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface
            )

            Gap(8)

            if (highlights.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    highlights.forEach { highlight ->
                        HighlightCard(highlight)
                    }
                }
            } else if (items.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items.forEach { change ->
                        ChangelogItem(change)
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colorScheme.surfaceContainerLow)
                        .padding(14.dp)
                ) {
                    Text(
                        text = "Данная версия содержит критические исправления для продолжения корректной работы расписания.",
                        style = typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }

            Gap(16)
        }

        // Нижняя часть (действия и статус загрузки)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isRuStore) {
                RuStoreActionBlock(onAction = { manager.handleAction() })
            } else {
                GitHubActionBlock(
                    state = downloadState,
                    onAction = { manager.handleAction() }
                )
            }

            // Кнопка выхода из приложения
            TextButton(
                onClick = onExitApp,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Text(
                    text = "Закрыть приложение",
                    style = typography.labelMedium,
                    color = colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun HighlightCard(highlight: ReleaseHighlight) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colorScheme.surfaceContainerLow)
            .border(0.5.dp, colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val tag = highlight.tag ?: "FIX"
            val (tagBg, tagFg) = when (tag.uppercase()) {
                "NEW" -> colorScheme.primaryContainer to colorScheme.primary
                "FIX" -> colorScheme.tertiaryContainer to colorScheme.tertiary
                else -> colorScheme.secondaryContainer to colorScheme.secondary
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(tagBg)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = tag.uppercase(),
                    style = typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = tagFg,
                    fontSize = 10.sp
                )
            }

            Text(
                text = highlight.title,
                style = typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onSurface
            )
        }

        Gap(4)

        Text(
            text = highlight.description,
            style = typography.bodySmall,
            color = colorScheme.onSurfaceVariant,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun ChangelogItem(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
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
            text = text,
            style = typography.bodySmall,
            color = colorScheme.onSurface,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun RuStoreActionBlock(onAction: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Button(
            onClick = onAction,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary)
        ) {
            Icon(WHATIcons.Download, contentDescription = null, modifier = Modifier.size(22.dp))
            Gap(10)
            Text(
                text = "Обновить в RuStore",
                style = typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = "Вы перейдете в магазин RuStore для быстрой загрузки новой версии",
            style = typography.labelSmall,
            color = colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun GitHubActionBlock(
    state: DownloadState,
    onAction: () -> Unit
) {
    when (state) {
        is DownloadState.Idle -> {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = onAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(WHATIcons.Download, contentDescription = null, modifier = Modifier.size(22.dp))
                    Gap(10)
                    Text(
                        text = "Скачать и обновить",
                        style = typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "Обновление загрузится и автоматически откроется для установки",
                    style = typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
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
                        .height(8.dp)
                        .clip(CircleShape),
                    color = colorScheme.primary
                )
                Text(
                    text = "Подготовка файла к загрузке...",
                    style = typography.labelMedium,
                    color = colorScheme.primary
                )
            }
        }

        is DownloadState.Downloading -> {
            val percent = state.progress
            val fraction = (percent / 100f).coerceIn(0f, 1f)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
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
                        text = "Загрузка обновления в реальном времени...",
                        style = typography.labelSmall,
                        color = colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$percent%",
                        style = typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.primary
                    )
                }
            }
        }

        is DownloadState.Completed -> {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = onAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colorScheme.tertiary)
                ) {
                    Icon(WHATIcons.ApkInstall, contentDescription = null, modifier = Modifier.size(22.dp))
                    Gap(10)
                    Text(
                        text = "Установить обновление",
                        style = typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "Загрузка завершена. Если системный установщик не открылся, нажмите кнопку",
                    style = typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
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
                        .clip(RoundedCornerShape(12.dp))
                        .background(colorScheme.errorContainer.copy(alpha = 0.5f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = WHATIcons.DownloadError,
                        contentDescription = null,
                        tint = colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = state.message,
                        style = typography.bodySmall,
                        color = colorScheme.onErrorContainer
                    )
                }

                OutlinedButton(
                    onClick = onAction,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Попробовать снова")
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return ""
    val mb = bytes.toDouble() / (1024 * 1024)
    return " • ${(mb * 10).roundToInt() / 10.0} МБ"
}

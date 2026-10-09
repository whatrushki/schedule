package app.what.features.onboarding.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.what.data.local.settings.findSettingComponent
import app.what.domain.services.ReleaseHighlight
import app.what.domain.services.ReleaseNotes
import app.what.foundation.ui.Gap
import app.what.schedule.data.local.settings.AppValues
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@Composable
fun UpdateOnboardingContent(
    releaseNotes: ReleaseNotes,
    appValues: AppValues,
    onDismiss: () -> Unit,
    onNavigate: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val changelogItems = releaseNotes.allChangelogItems()
    val hasChangelogPage = changelogItems.isNotEmpty()
    val totalPages = 1 + releaseNotes.highlights.size + (if (hasChangelogPage) 1 else 0)

    val pagerState = rememberPagerState(pageCount = { totalPages })
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.surface)
    ) {
        UpdateOnboardingBackground(pagerState.currentPage)

        Column(modifier = Modifier.fillMaxSize()) {
            // Header: Skip button at top right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = "Пропустить",
                        style = typography.labelLarge,
                        color = colorScheme.secondary
                    )
                }
            }

            // Pager content with scale & alpha transitions
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                userScrollEnabled = true
            ) { page ->
                val pageOffset =
                    (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                val scale = 1f - (0.1f * pageOffset.absoluteValue).coerceIn(0f, 0.2f)
                val alpha = 1f - (0.5f * pageOffset.absoluteValue).coerceIn(0f, 0.5f)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                        },
                    contentAlignment = Alignment.TopCenter
                ) {
                    when {
                        page == 0 -> {
                            UpdateIntroPage(releaseNotes = releaseNotes)
                        }
                        page <= releaseNotes.highlights.size -> {
                            val highlight = releaseNotes.highlights[page - 1]
                            UpdateHighlightPage(
                                highlight = highlight,
                                appValues = appValues,
                                onNavigate = onNavigate
                            )
                        }
                        else -> {
                            UpdateChangelogPage(
                                version = releaseNotes.version,
                                items = changelogItems
                            )
                        }
                    }
                }
            }

            // Bottom Navigation with animated pills and next/done button
            UpdateOnboardingNavigation(
                pagerState = pagerState,
                pagesCount = totalPages,
                onNext = {
                    scope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                },
                onFinish = onDismiss
            )
        }
    }
}

@Composable
private fun UpdateIntroPage(releaseNotes: ReleaseNotes) {
    val primaryColor = colorScheme.primary
    val glowBrush = Brush.radialGradient(
        colors = listOf(primaryColor.copy(0.25f), Color.Transparent)
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 24.dp)
    ) {
        Box(
            modifier = Modifier.size(160.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(160.dp)) {
                drawCircle(brush = glowBrush)
            }
            Icon(
                imageVector = Icons.Default.NewReleases,
                contentDescription = null,
                modifier = Modifier.size(90.dp),
                tint = primaryColor
            )
        }

        Gap(28)

        Text(
            text = releaseNotes.title ?: "Обновление",
            style = typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = colorScheme.onSurface
        )

        Gap(10)

        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(colorScheme.primary.copy(alpha = 0.12f))
                .padding(horizontal = 14.dp, vertical = 5.dp)
        ) {
            Text(
                text = "Версия ${releaseNotes.version}",
                style = typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.primary
            )
        }

        Gap(16)

        Text(
            text = releaseNotes.shortDescription
                ?: "Познакомьтесь с новыми возможностями и улучшениями в этом выпуске.",
            style = typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = colorScheme.secondary,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}

@Composable
private fun UpdateHighlightPage(
    highlight: ReleaseHighlight,
    appValues: AppValues,
    onNavigate: ((String) -> Unit)?
) {
    val primaryColor = colorScheme.primary
    val glowBrush = Brush.radialGradient(
        colors = listOf(primaryColor.copy(0.22f), Color.Transparent)
    )

    val icon = resolveHighlightIcon(highlight.icon)
    val settingComponents = highlight.allSettingKeys().mapNotNull { appValues.findSettingComponent(it) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 24.dp)
    ) {
        // Hero Icon with subtle glow - 160.dp matching UpdateIntroPage baseline
        Box(
            modifier = Modifier.size(160.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(160.dp)) {
                drawCircle(brush = glowBrush)
            }
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                    tint = primaryColor
                )
            }
        }

        Gap(28)

        Text(
            text = highlight.title,
            style = typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Gap(12)

        Text(
            text = highlight.description,
            style = typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = colorScheme.secondary,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        // Multiple settings without bulky card wrappers, edge-to-edge
        if (settingComponents.isNotEmpty()) {
            Gap(12)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                settingComponents.forEach { component ->
                    component.content(Modifier.fillMaxWidth())
                }
            }
        }

        // Navigate action button if route is provided
        val route = highlight.route
        if (route != null && onNavigate != null) {
            Gap(20)
            Button(
                onClick = { onNavigate(route) },
                modifier = Modifier.height(48.dp)
            ) {
                Text("Перейти к разделу")
                Gap(8)
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun UpdateChangelogPage(
    version: String,
    items: List<String>
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(34.dp)
            )
        }

        Gap(14)

        Text(
            text = "Все изменения",
            style = typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Gap(4)

        Text(
            text = "Версия $version • ${items.size} изменений",
            style = typography.bodyMedium,
            color = colorScheme.secondary,
            textAlign = TextAlign.Center
        )

        Gap(16)

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium),
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items) { change ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(colorScheme.surfaceContainerLow)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(colorScheme.primary)
                    )
                    Gap(12)
                    Text(
                        text = change,
                        style = typography.bodyMedium,
                        color = colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun UpdateOnboardingNavigation(
    pagerState: PagerState,
    pagesCount: Int,
    onNext: () -> Unit,
    onFinish: () -> Unit
) {
    val isLastPage = pagerState.currentPage == pagesCount - 1

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        // Page indicator dots
        Row(
            Modifier.align(Alignment.CenterStart),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(pagesCount) { iteration ->
                val color = if (pagerState.currentPage == iteration)
                    colorScheme.primary
                else
                    colorScheme.outlineVariant

                val width by animateDpAsState(
                    if (pagerState.currentPage == iteration) 24.dp else 8.dp,
                    label = "IndicatorWidth"
                )

                Box(
                    modifier = Modifier
                        .height(8.dp)
                        .width(width)
                        .clip(CircleShape)
                        .background(color)
                )
            }
        }

        // Action button (right)
        Box(Modifier.align(Alignment.CenterEnd)) {
            AnimatedContent(targetState = isLastPage, label = "ButtonAnim") { last ->
                if (last) {
                    Button(
                        onClick = onFinish,
                        modifier = Modifier.height(50.dp)
                    ) {
                        Text("Понятно")
                        Gap(8)
                        Icon(Icons.Default.Check, null)
                    }
                } else {
                    FilledIconButton(
                        onClick = onNext,
                        modifier = Modifier.size(50.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, null)
                    }
                }
            }
        }
    }
}

@Composable
fun UpdateOnboardingBackground(page: Int) {
    val color1 by animateColorAsState(
        when (page % 4) {
            0 -> colorScheme.primaryContainer.copy(0.4f)
            1 -> colorScheme.tertiaryContainer.copy(0.4f)
            2 -> colorScheme.secondaryContainer.copy(0.4f)
            3 -> colorScheme.primaryContainer.copy(0.25f)
            else -> colorScheme.surface
        },
        label = "BgAnim"
    )

    val surface = colorScheme.surface

    Canvas(modifier = Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(color1, surface),
                center = Offset(size.width / 2f, size.height * 0.25f),
                radius = size.maxDimension * 0.85f
            )
        )
    }
}

private fun resolveHighlightIcon(iconKey: String?): ImageVector {
    return when (iconKey?.lowercase()) {
        "palette", "theme" -> Icons.Default.Palette
        "bell", "notification" -> Icons.Default.Notifications
        "speed", "perf" -> Icons.Default.Speed
        "lock", "security", "privacy" -> Icons.Default.Security
        "widget", "widgets" -> Icons.Default.Widgets
        "tune", "settings" -> Icons.Default.Tune
        "star", "favorite" -> Icons.Default.Star
        else -> Icons.Filled.AutoAwesome
    }
}

suspend fun loadBundledReleaseNotes(): ReleaseNotes? {
    return try {
        val bytes = schedule.features.onboarding.generated.resources.Res.readBytes("files/release-notes.json")
        ReleaseNotes.fromJson(bytes.decodeToString())
    } catch (e: Exception) {
        null
    }
}

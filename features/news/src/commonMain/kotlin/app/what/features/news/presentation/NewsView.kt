package app.what.schedule.features.news.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.what.domain.models.NewListItem
import app.what.foundation.data.RemoteState
import app.what.foundation.ui.AppPullToRefresh
import app.what.foundation.ui.Gap
import app.what.foundation.ui.animations.rememberShimmer
import app.what.foundation.ui.bclick
import app.what.foundation.utils.Analytics
import app.what.foundation.utils.DateTimeUtils
import app.what.schedule.features.news.domain.models.NewsEvent
import app.what.schedule.features.news.domain.models.NewsState
import app.what.schedule.ui.components.AsyncImageWithFallback
import app.what.schedule.ui.components.Fallback

import androidx.compose.runtime.remember

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsView(
    state: NewsState,
    listener: (NewsEvent) -> Unit,
    modifier: Modifier = Modifier,
    isWide: Boolean = false,
    selectedNewId: String? = null,
    onSelectNew: ((NewListItem) -> Unit)? = null
) {
    val shimmer = rememberShimmer()
    val lazyGridState = rememberLazyGridState()

    LaunchedEffect(lazyGridState.canScrollForward) {
        if (!lazyGridState.canScrollForward && state.newsState != RemoteState.Loading && state.news.isNotEmpty())
            listener(NewsEvent.OnListEndingScrolled)
    }

    val columns = if (isWide) GridCells.Adaptive(240.dp) else GridCells.Fixed(1)
    val displayNews = remember(state.news) { state.news.distinctBy { it.id } }

    BoxWithConstraints(modifier = modifier) {
        val topSpacing = if (isWide) 0.dp else (maxHeight * 0.15f).coerceIn(36.dp, 130.dp)

        AppPullToRefresh(
            isRefreshing = state.newsState == RemoteState.Loading,
            onRefresh = { listener(NewsEvent.OnRefresh) },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyVerticalGrid(
                columns = columns,
                state = lazyGridState,
                modifier = Modifier.fillMaxSize().padding(horizontal = if (isWide) 0.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        if (topSpacing > 0.dp) {
                            Box(Modifier.height(topSpacing))
                        }
                    Text(
                        "Новости",
                        style = typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (isWide) 32.sp else 46.sp,
                        color = colorScheme.primary,
                        modifier = Modifier.padding(
                            start = if (isWide) 4.dp else 8.dp,
                            end = if (isWide) 4.dp else 8.dp,
                            top = 8.dp,
                            bottom = 0.dp
                        )
                    )
                }
            }

            when {
                state.newsState is RemoteState.Error && state.news.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    Fallback(
                        "Произошла непредвиденная ошибка",
                        Modifier.fillMaxSize(),
                        "Попробовать снова" to { listener(NewsEvent.OnRefresh) }
                    )
                }

                state.news.isEmpty() && state.newsState == RemoteState.Loading -> {
                    items(if (isWide) 6 else 4) {
                        NewListItemShimmer(shimmer = shimmer)
                    }
                }

                else -> items(displayNews, key = { it.id }) {
                    NewListItemView(
                        modifier = Modifier.animateItem(),
                        selected = selectedNewId == it.id,
                        item = it,
                        isWide = isWide,
                        onClick = {
                            Analytics.logNewsOpen(it.id, it.url, it.title)
                            if (isWide) {
                                onSelectNew?.invoke(it)
                            } else {
                                listener(NewsEvent.OnNewEnterClicked(it))
                            }
                        }
                    )
                }
            }
        }
    }
}
}

@Composable
fun NewListItemView(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    item: NewListItem,
    isWide: Boolean = false,
    onClick: () -> Unit
) {
    val borderModifier = if (selected) {
        Modifier.border(2.dp, colorScheme.primary, shapes.large)
    } else Modifier

    Box(
        modifier
            .fillMaxWidth()
            .clip(shapes.large)
            .background(colorScheme.surfaceContainer)
            .then(borderModifier)
            .bclick(block = onClick)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            AsyncImageWithFallback(
                item.bannerUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(shapes.large)
            )

            Gap(8)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
            ) {
                Text(
                    DateTimeUtils.formatDate(item.timestamp),
                    color = colorScheme.secondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )

                item.tags.firstOrNull()?.let { tag ->
                    Text(
                        tag.name,
                        color = colorScheme.primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }

            Gap(4)

            Text(
                text = item.title,
                color = colorScheme.onSurface,
                fontSize = 15.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.SemiBold,
                minLines = if (isWide) 2 else 1,
                maxLines = if (isWide) 2 else 3,
                overflow = TextOverflow.Ellipsis
            )

            val desc = item.description?.trim().orEmpty()
            if (isWide || desc.isNotBlank()) {
                Gap(4)

                Text(
                    text = desc,
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    fontWeight = FontWeight.Normal,
                    minLines = if (isWide) 2 else 1,
                    maxLines = if (isWide) 2 else 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun NewListItemShimmer(
    shimmer: Brush,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shapes.large)
            .background(colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(shapes.large)
                    .background(shimmer)
            )
            Gap(8)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
            ) {
                Box(
                    Modifier
                        .width(80.dp)
                        .height(14.dp)
                        .clip(shapes.small)
                        .background(shimmer)
                )
            }
            Gap(4)
            Column(
                modifier = Modifier.height(38.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(0.9f)
                        .height(16.dp)
                        .clip(shapes.small)
                        .background(shimmer)
                )
                Box(
                    Modifier
                        .fillMaxWidth(0.6f)
                        .height(16.dp)
                        .clip(shapes.small)
                        .background(shimmer)
                )
            }
            Gap(4)
            Column(
                modifier = Modifier.height(34.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(shapes.small)
                        .background(shimmer)
                )
                Box(
                    Modifier
                        .fillMaxWidth(0.75f)
                        .height(14.dp)
                        .clip(shapes.small)
                        .background(shimmer)
                )
            }
        }
    }
}
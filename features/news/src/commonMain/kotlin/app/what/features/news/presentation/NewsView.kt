package app.what.schedule.features.news.presentation

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Brush
import app.what.foundation.ui.AppPullToRefresh
import app.what.foundation.ui.animations.rememberShimmer
import app.what.foundation.utils.isDesktop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.what.foundation.data.RemoteState
import app.what.foundation.ui.Gap
import app.what.foundation.ui.bclick
import app.what.foundation.ui.useState
import app.what.domain.models.NewListItem
import app.what.schedule.features.news.domain.models.NewsEvent
import app.what.schedule.features.news.domain.models.NewsState
import app.what.schedule.ui.components.AsyncImageWithFallback
import app.what.schedule.ui.components.Fallback
import app.what.foundation.utils.Analytics
import app.what.foundation.utils.DateTimeUtils

import androidx.compose.foundation.layout.width
import app.what.foundation.ui.applyIf

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

    AppPullToRefresh(
        isRefreshing = state.newsState == RemoteState.Loading,
        onRefresh = { listener(NewsEvent.OnRefresh) },
        modifier = modifier
    ) {
        if (isWide) {
            val lazyGridState = rememberLazyGridState()

            LaunchedEffect(lazyGridState.canScrollForward) {
                if (!lazyGridState.canScrollForward && state.newsState != RemoteState.Loading)
                    listener(NewsEvent.OnListEndingScrolled)
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(240.dp),
                state = lazyGridState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Новости",
                            style = typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp,
                            color = colorScheme.primary,
                        )
                        if (isDesktop) {
                            IconButton(onClick = { listener(NewsEvent.OnRefresh) }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                            }
                        }
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
                        items(6) {
                            NewListItemShimmer(shimmer = shimmer, isWide = true)
                        }
                    }

                else -> items(state.news, key = { it.id }) {
                    NewListItemView(
                        modifier = Modifier.animateItem(),
                        selected = selectedNewId == it.id,
                        item = it,
                        isWide = true,
                        onClick = {
                            Analytics.logNewsOpen(it.id, it.url, it.title)
                            onSelectNew?.invoke(it)
                        },
                        onSelect = {
                            Analytics.logNewsOpen(it.id, it.url, it.title)
                            onSelectNew?.invoke(it)
                        }
                    )
                }
            }
        }
    } else {
        var localSelectedNewId by useState<String?>(null)
        val lazyListState = rememberLazyListState()

        LaunchedEffect(lazyListState.canScrollForward) {
            if (!lazyListState.canScrollForward && state.newsState != RemoteState.Loading)
                listener(NewsEvent.OnListEndingScrolled)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = lazyListState,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Box(
                    Modifier
                        .animateContentSize()
                        .height(116.dp)
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Новости",
                        style = typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        fontSize = 46.sp,
                        color = colorScheme.primary,
                    )
                    if (isDesktop) {
                        IconButton(onClick = { listener(NewsEvent.OnRefresh) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Обновить")
                        }
                    }
                }
            }

            when {
                state.newsState is RemoteState.Error && state.news.isEmpty() -> item {
                    Fallback(
                        "Произошла непредвиденная ошибка",
                        Modifier.fillMaxSize(),
                        "Попробовать снова" to { listener(NewsEvent.OnRefresh) }
                    )
                }

                state.news.isEmpty() && state.newsState == RemoteState.Loading -> {
                    items(4) {
                        NewListItemShimmer(shimmer = shimmer, isWide = false)
                    }
                }

                else -> items(state.news, key = { it.id }) {
                    NewListItemView(
                        modifier = Modifier.animateItem(),
                        selected = localSelectedNewId == it.id,
                        item = it,
                        isWide = false,
                        onClick = {
                            localSelectedNewId = if (localSelectedNewId != it.id) it.id else null
                        },
                        onSelect = {
                            Analytics.logNewsOpen(it.id, it.url, it.title)
                            listener(NewsEvent.OnNewEnterClicked(it))
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
    modifier: Modifier,
    selected: Boolean,
    item: NewListItem,
    isWide: Boolean = false,
    onClick: () -> Unit,
    onSelect: () -> Unit
) {
    val borderModifier = if (isWide && selected) {
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
        modifier = Modifier
            .padding(12.dp, 14.dp, 12.dp, 12.dp)
            .animateContentSize()
    ) {
        AsyncImageWithFallback(
            item.bannerUrl,
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isWide) 130.dp else 150.dp)
                .clip(shapes.large)
        )

        Gap(8)

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                DateTimeUtils.formatDate(item.timestamp),
                color = colorScheme.secondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )

            Row {
                item.tags.take(if (isWide) 1 else 3).forEach {
                    FilterChip(true, {}, label = {
                        Text(it.name)
                    })
                }
            }
        }

        Gap(4)

        Text(
            item.title,
            color = colorScheme.onSurface,
            fontSize = if (isWide) 16.sp else if (item.description?.isNotBlank() == true) 22.sp else 18.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = if (isWide) 2 else Int.MAX_VALUE,
            overflow = TextOverflow.Ellipsis
        )

        val description = item.description
        if (!description.isNullOrBlank()) {
            Gap(4)

            Text(
                description.trim(),
                color = colorScheme.onSurfaceVariant,
                maxLines = if (isWide) 2 else if (selected) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                fontSize = 14.sp,
                lineHeight = 18.sp
            )
        }

        if (!isWide && selected) {
            Gap(12)

            Button(onSelect) {
                Text("Перейти")
            }
        }
    }
}
}

@Composable
fun NewListItemShimmer(
    shimmer: Brush,
    isWide: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shapes.large)
            .background(colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier.padding(12.dp, 14.dp, 12.dp, 12.dp)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(if (isWide) 130.dp else 150.dp)
                    .clip(shapes.large)
                    .background(shimmer)
            )
            Gap(8)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    Modifier
                        .width(90.dp)
                        .height(16.dp)
                        .clip(shapes.small)
                        .background(shimmer)
                )
                Box(
                    Modifier
                        .width(60.dp)
                        .height(24.dp)
                        .clip(shapes.small)
                        .background(shimmer)
                )
            }
            Gap(6)
            Box(
                Modifier
                    .fillMaxWidth(0.9f)
                    .height(20.dp)
                    .clip(shapes.small)
                    .background(shimmer)
            )
            Gap(4)
            Box(
                Modifier
                    .fillMaxWidth(0.6f)
                    .height(20.dp)
                    .clip(shapes.small)
                    .background(shimmer)
            )
            Gap(6)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(shapes.small)
                    .background(shimmer)
            )
        }
    }
}
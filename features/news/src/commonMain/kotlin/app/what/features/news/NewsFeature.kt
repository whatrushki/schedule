package app.what.schedule.features.news

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.what.domain.models.NewListItem
import app.what.foundation.core.Feature
import app.what.foundation.data.RemoteState
import app.what.foundation.ui.useState
import app.what.navigation.core.NavComponent
import app.what.navigation.core.rememberNavigator
import app.what.schedule.features.news.domain.NewsController
import app.what.schedule.features.news.domain.models.NewsAction
import app.what.schedule.features.news.domain.models.NewsEvent
import app.what.schedule.features.news.navigation.NewsProvider
import app.what.schedule.features.news.presentation.NewsView
import app.what.schedule.features.newsDetail.NewsDetailFeature
import app.what.schedule.features.newsDetail.navigation.NewsDetailProvider
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class NewsFeature(
    override val data: NewsProvider
) : Feature<NewsController, NewsEvent>(), NavComponent<NewsProvider>, KoinComponent {
    override val controller: NewsController by inject()
    
    @Composable
    override fun content(modifier: Modifier) = BoxWithConstraints(
        modifier.fillMaxSize()
    ) {
        val isWide = maxWidth >= 760.dp
        val viewState by controller.collectStates()
        val viewAction by controller.collectActions()
        val navigator = rememberNavigator()
        var selectedItem by useState<NewListItem?>(null)
        
        LaunchedEffect(Unit) {
            listener(NewsEvent.Init)
        }

        LaunchedEffect(viewState.news) {
            if (selectedItem == null && viewState.news.isNotEmpty()) {
                selectedItem = viewState.news.firstOrNull()
            }
        }
        
        if (isWide) {
            Row(Modifier.fillMaxSize()) {
                // Левая панель: сетка новостей
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(end = 8.dp)
                ) {
                    NewsView(
                        state = viewState,
                        listener = listener,
                        isWide = true,
                        selectedNewId = selectedItem?.id,
                        onSelectNew = { selectedItem = it }
                    )
                }

                // Правая панель: детальная новость
                Box(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight()
                        .clip(shapes.large)
                        .background(colorScheme.surface)
                ) {
                    val activeItem = selectedItem ?: viewState.news.firstOrNull()
                    if (activeItem != null) {
                        key(activeItem.id) {
                            NewsDetailFeature(
                                NewsDetailProvider(
                                    activeItem.id,
                                    activeItem.url,
                                    activeItem.bannerUrl,
                                    activeItem.title,
                                    activeItem.description
                                )
                            ).content(Modifier.fillMaxSize())
                        }
                    } else if (viewState.newsState == RemoteState.Loading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Выберите новость для просмотра",
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                NewsView(viewState, listener)
            }
        }
        
        when (viewAction) {
            null -> Unit
            is NewsAction.NavigateToNewsDetail -> {
                if (!isWide) {
                    (viewAction as NewsAction.NavigateToNewsDetail).item.let {
                        navigator.parent!!.c.navigate(
                            NewsDetailProvider(
                                it.id,
                                it.url,
                                it.bannerUrl,
                                it.title,
                                it.description
                            )
                        )
                    }
                } else {
                    selectedItem = (viewAction as NewsAction.NavigateToNewsDetail).item
                }
                controller.clearAction()
            }
        }
    }
}
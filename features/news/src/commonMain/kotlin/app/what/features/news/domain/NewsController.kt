package app.what.schedule.features.news.domain

import androidx.lifecycle.viewModelScope
import app.what.foundation.core.UIController
import app.what.foundation.data.RemoteState
import app.what.foundation.services.AppLogger.Companion.Auditor
import app.what.foundation.utils.launchSafe
import app.what.schedule.data.local.settings.AppValues
import app.what.domain.models.NewListItem
import app.what.domain.repositories.NewsRepository
import app.what.schedule.features.news.domain.models.NewsAction
import app.what.schedule.features.news.domain.models.NewsEvent
import app.what.schedule.features.news.domain.models.NewsState
import app.what.foundation.utils.LogCat
import app.what.foundation.utils.LogScope
import app.what.foundation.utils.buildTag
import kotlinx.coroutines.Job

class NewsController(
    private val apiRepository: NewsRepository,
    private val settings: AppValues
) : UIController<NewsState, NewsAction, NewsEvent>(
    NewsState()
) {
    private var loadJob: Job? = null

    override fun obtainEvent(viewEvent: NewsEvent) {
        when (viewEvent) {
            NewsEvent.Init -> {
                if (viewState.news.isEmpty() && viewState.newsState !is RemoteState.Loading) {
                    requestNextPage(rollback = true)
                }
            }
            NewsEvent.OnListEndingScrolled -> requestNextPage()
            NewsEvent.OnRefresh -> requestNextPage(true)
            is NewsEvent.OnNewEnterClicked -> selectNew(viewEvent.value)
        }
    }
    
    init {
        viewModelScope.launchSafe(debug = debugMode) {
            settings.institution.observe().collect {
                requestNextPage(rollback = true)
            }
        }
    }
    
    val debugMode: Boolean
        get() = settings.debugMode.get() == true
    
    private fun selectNew(item: NewListItem) {
        setAction(NewsAction.NavigateToNewsDetail(item))
    }
    
    private fun requestNextPage(rollback: Boolean = false) {
        if (!rollback && (loadJob?.isActive == true || viewState.newsState == RemoteState.Loading)) {
            return
        }
        if (rollback) {
            loadJob?.cancel()
        }

        val newsTag = buildTag(LogScope.NEWS, LogCat.NET)
        val page = if (rollback) 1 else viewState.page
        Auditor.debug(newsTag, "Запрос новостей, страница: $page")
        
        updateState {
            copy(
                newsState = RemoteState.Loading,
                page = page,
                news = if (rollback) emptyList() else viewState.news
            )
        }
        
        loadJob = viewModelScope.launchSafe(
            debug = debugMode, onFailure = {
                Auditor.err(newsTag, "Ошибка загрузки новостей", it)
                updateState { copy(newsState = RemoteState.Error(it)) }
            }
        ) {
            val data = apiRepository.getNews(page)
            Auditor.debug(newsTag, "Новости загружены, количество: ${data.size}")
            
            updateState {
                val combinedNews = if (rollback) data else (viewState.news + data).distinctBy { it.id }
                copy(
                    newsState = RemoteState.Success,
                    news = combinedNews,
                    page = page + 1
                )
            }
        }
    }
}
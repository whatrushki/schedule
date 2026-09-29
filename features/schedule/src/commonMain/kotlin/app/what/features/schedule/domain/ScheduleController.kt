package app.what.schedule.features.schedule.domain

import androidx.lifecycle.viewModelScope
import app.what.foundation.core.UIController
import app.what.foundation.data.RemoteState
import app.what.foundation.services.AppLogger.Companion.Auditor
import app.what.foundation.utils.launchIO
import app.what.foundation.utils.launchSafe
import app.what.schedule.data.local.settings.AppValues
import app.what.domain.models.ScheduleResponse
import app.what.domain.models.ScheduleSearch
import app.what.domain.models.toScheduleSearch
import app.what.domain.repositories.ScheduleRepository
import app.what.schedule.features.schedule.domain.models.ScheduleAction
import app.what.schedule.features.schedule.domain.models.ScheduleEvent
import app.what.schedule.features.schedule.domain.models.ScheduleState
import app.what.foundation.utils.LogCat
import app.what.foundation.utils.LogScope
import app.what.foundation.utils.buildTag
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class ScheduleController(
    private val apiRepository: ScheduleRepository,
    private val settings: AppValues
) : UIController<ScheduleState, ScheduleAction, ScheduleEvent>(
    ScheduleState()
) {
    override fun obtainEvent(viewEvent: ScheduleEvent) = when (viewEvent) {
        ScheduleEvent.Init -> {}
        ScheduleEvent.UpdateSchedule -> syncSchedule(viewState.selectedSearch)
        ScheduleEvent.OnCloudSync -> syncSchedule(
            viewState.selectedSearch,
            useCache = false,
            cloudSync = true
        )
        
        ScheduleEvent.OnRefresh -> syncSchedule(viewState.selectedSearch, useCache = false, forceLive = true)
        ScheduleEvent.OnRefreshSearches -> updateSearches(showLoading = true, forceReload = true)
        is ScheduleEvent.OnSearchClicked -> {
            val isSameGroup = viewEvent.value == viewState.selectedSearch
            syncSchedule(viewEvent.value, useCache = !isSameGroup, forceLive = isSameGroup)
        }
        is ScheduleEvent.OnSearchLongPressed -> toggleFavorites(viewEvent.value)
    }
    
    init {
        init()
        viewModelScope.launch {
            settings.institution.observe().drop(1).collect {
                settings.lastSearch.set(null)
                updateState {
                    copy(
                        selectedSearch = null,
                        scheduleState = RemoteState.Idle,
                        schedules = emptyList(),
                        scheduleSearches = emptyList(),
                        scheduleSearchesState = RemoteState.Loading
                    )
                }
                updateSearches(showLoading = true, forceReload = true)
            }
        }
    }
    
    val debugMode: Boolean
        get() = settings.debugMode.get() == true
    
    private fun init() {
        val lastSearch = settings.lastSearch.get()
        
        updateState {
            if (lastSearch == null) copy(scheduleState = RemoteState.Idle)
            else copy(selectedSearch = lastSearch)
        }
        
        updateSearches()
        syncSchedule(viewState.selectedSearch, true)
    }
    
    private fun toggleFavorites(value: ScheduleSearch) {
        val toggledValue = when (value) {
            is ScheduleSearch.Group -> value.copy(favorite = !value.favorite)
            is ScheduleSearch.Teacher -> value.copy(favorite = !value.favorite)
        }
        updateState {
            copy(
                scheduleSearches = scheduleSearches.map { item ->
                    if (item.id == value.id && item::class == value::class) {
                        toggledValue
                    } else {
                        item
                    }
                }
            )
        }
        viewModelScope.launchIO {
            when (value) {
                is ScheduleSearch.Group -> apiRepository.toggleFavorites(value)
                is ScheduleSearch.Teacher -> apiRepository.toggleFavorites(value)
            }
        }.invokeOnCompletion {
            updateSearches(showLoading = false)
            val scheduleTag = buildTag(LogScope.SCHEDULE, LogCat.STATE)
            Auditor.debug(scheduleTag, "Избранное обновлено")
        }
    }
    
    private fun syncSchedule(
        search: ScheduleSearch?,
        useCache: Boolean = true,
        cloudSync: Boolean = false,
        forceLive: Boolean = false
    ) {
        val scheduleTag = buildTag(LogScope.SCHEDULE, LogCat.STATE)
        Auditor.debug(scheduleTag, "Синхронизация расписания: $search, кеш: $useCache, forceLive: $forceLive")
        
        val groupChanged = search != null && search != viewState.selectedSearch
        if (search != null && (groupChanged || viewState.schedules.isEmpty())) {
            updateState {
                copy(
                    selectedSearch = search,
                    schedules = if (groupChanged) emptyList() else viewState.schedules,
                    scheduleState = RemoteState.Loading
                )
            }
        }
        
        viewModelScope.launchSafe(
            debug = debugMode,
            onFailure = {
                Auditor.err(scheduleTag, "Ошибка синхронизации расписания", it)
                updateState { copy(scheduleState = RemoteState.Error(it)) }
            }
        ) {
            val searchId = apiRepository.findSearchId(search) ?: search?.id?.takeIf { it.isNotEmpty() }
            if (search != null && searchId != null)
                updateSchedule(search.copy(id = searchId), useCache, cloudSync, forceLive)
        }
    }
    
    private suspend fun updateSchedule(
        search: ScheduleSearch?,
        useCache: Boolean,
        cloudSync: Boolean = false,
        forceLive: Boolean = false
    ) {
        search ?: return
        val currentInstitutionId = settings.institution.get()
        val effectiveSearch = if (search.institutionId == null && currentInstitutionId != null) {
            search.copy(institutionId = currentInstitutionId)
        } else {
            search
        }
        val scheduleTag = buildTag(LogScope.SCHEDULE, LogCat.STATE)
        val groupChanged = settings.lastSearch.get() != effectiveSearch
        
        settings.lastSearch.set(effectiveSearch)
        
        // 1. If cache is enabled, immediately display cached schedule with Loading state
        if (useCache) {
            val cached = apiRepository.getSchedule(
                effectiveSearch,
                useCache = true,
                requiresData = false,
                cloudSync = false
            )
            if (cached is ScheduleResponse.Available && cached.schedules.isNotEmpty()) {
                updateState {
                    copy(
                        selectedSearch = effectiveSearch,
                        schedules = cached.schedules,
                        scheduleState = RemoteState.Loading,
                        lastModified = cached.lastModified
                    )
                }
            } else {
                updateState {
                    copy(
                        selectedSearch = effectiveSearch,
                        schedules = if (groupChanged) emptyList() else viewState.schedules,
                        scheduleState = RemoteState.Loading
                    )
                }
            }
        } else {
            updateState {
                copy(
                    selectedSearch = effectiveSearch,
                    schedules = if (groupChanged) emptyList() else viewState.schedules,
                    scheduleState = RemoteState.Loading
                )
            }
        }
        
        // 2. Fetch latest schedule with replacements from network
        val data = apiRepository.getSchedule(
            effectiveSearch,
            useCache = false,
            requiresData = viewState.schedules.isEmpty() || groupChanged,
            cloudSync = cloudSync,
            forceLive = forceLive
        )
        
        when (data) {
            is ScheduleResponse.Available -> {
                Auditor.debug(
                    scheduleTag,
                    "Расписание успешно получено, дней: ${data.schedules.size}"
                )
            }
            ScheduleResponse.Empty -> Auditor.debug(scheduleTag, "Расписание пустое")
            ScheduleResponse.UpToDate -> Auditor.debug(scheduleTag, "Расписание актуально")
            else -> Auditor.debug(scheduleTag, "Не удалось получить расписание")
        }
        
        updateState {
            when (data) {
                ScheduleResponse.UpToDate -> copy(
                    scheduleState = RemoteState.Success,
                    isOffline = false
                )
                is ScheduleResponse.Available -> copy(
                    scheduleState = RemoteState.Success,
                    schedules = data.schedules,
                    lastModified = data.lastModified,
                    isOffline = false
                )
                is ScheduleResponse.Error -> {
                    val availableSchedules = data.cachedSchedules?.takeIf { it.isNotEmpty() }
                        ?: viewState.schedules.takeIf { it.isNotEmpty() }
                    copy(
                        scheduleState = if (availableSchedules != null) RemoteState.Success else RemoteState.Error(data.exception),
                        schedules = availableSchedules ?: emptyList(),
                        lastModified = data.lastModified ?: viewState.lastModified,
                        isOffline = availableSchedules != null
                    )
                }
                ScheduleResponse.Empty -> copy(
                    scheduleState = RemoteState.Empty,
                    schedules = emptyList(),
                    isOffline = false
                )
            }
        }
    }
    
    private fun updateSearches(showLoading: Boolean = true, forceReload: Boolean = false) {
        viewModelScope.launchSafe(
            retryCount = 0,
            onFailure = {
                val scheduleTag = buildTag(LogScope.SCHEDULE, LogCat.STATE)
                Auditor.err(scheduleTag, "Ошибка загрузки групп/преподавателей", it)
                updateState {
                    copy(scheduleSearchesState = RemoteState.Error(it))
                }
            }
        ) {
            if (showLoading) {
                updateState { copy(scheduleSearchesState = RemoteState.Loading) }
            }
            
            val currentInstitutionId = settings.institution.get()
            val (teachers, groups) = coroutineScope {
                val ut = async { apiRepository.getTeachers(institutionId = currentInstitutionId, forceReload = forceReload).map { it.toScheduleSearch() } }
                val ug = async { apiRepository.getGroups(institutionId = currentInstitutionId, forceReload = forceReload).map { it.toScheduleSearch() } }
                ut.await() to ug.await()
            }
            val data = (teachers + groups).distinctBy { "${it::class.simpleName}_${it.name.trim()}" }
            if (data.isEmpty()) {
                throw Exception("Не удалось загрузить список групп")
            }
            
            updateState {
                copy(
                    scheduleSearches = data,
                    scheduleSearchesState = RemoteState.Success
                )
            }
        }
    }
}
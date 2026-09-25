package app.what.schedule.features.schedule.domain.models

import app.what.foundation.data.RemoteState
import app.what.domain.models.DaySchedule
import app.what.domain.models.ScheduleSearch
import app.what.schedule.ui.components.ScheduleSearchData
import kotlinx.datetime.LocalDateTime

data class ScheduleState(
    val schedules: List<DaySchedule> = emptyList(),
    val scheduleState: RemoteState = RemoteState.Empty,
    val isOffline: Boolean = false,
    val lastModified: LocalDateTime? = null,
    override val scheduleSearches: List<ScheduleSearch> = emptyList(),
    override val scheduleSearchesState: RemoteState = RemoteState.Idle,
    override val selectedSearch: ScheduleSearch? = null
) : ScheduleSearchData



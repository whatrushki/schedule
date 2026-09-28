package app.what.schedule.features.insts.rksi.domain.models

import app.what.domain.models.ScheduleSearch

sealed interface RksiAction {
    data object OpenAuth : RksiAction
    data object OpenMain : RksiAction
    data class OpenSchedule(val search: ScheduleSearch.Group) : RksiAction
    data object OpenNews : RksiAction
    data class OpenNewDetail(
        val id: String,
        val url: String,
        val bannerUrl: String,
        val title: String,
        val description: String?
    ) : RksiAction
}


package app.what.schedule.features.insts.sfedu.domain.models

import app.what.domain.models.ScheduleSearch

sealed interface SfeduAction {
    data object OpenAuth : SfeduAction
    data object OpenMain : SfeduAction
    data class OpenSchedule(val search: ScheduleSearch.Group) : SfeduAction
    data object OpenNews : SfeduAction
    data class OpenNewDetail(
        val id: String,
        val url: String,
        val bannerUrl: String,
        val title: String,
        val description: String?
    ) : SfeduAction
}

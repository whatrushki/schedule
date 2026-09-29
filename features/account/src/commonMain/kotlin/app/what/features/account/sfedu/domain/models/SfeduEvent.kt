package app.what.schedule.features.insts.sfedu.domain.models

sealed interface SfeduEvent {
    data class TokenSubmitted(
        val token: String,
        val name: String? = null,
        val group: String? = null
    ) : SfeduEvent

    data object LogoutClicked : SfeduEvent
    data object MainOpened : SfeduEvent

    // Grades / BRS
    data class SemesterSelected(val semesterId: Int) : SfeduEvent
    data class DisciplineClicked(val disciplineId: Int) : SfeduEvent
    data object DisciplineDetailClosed : SfeduEvent

    // News
    data object OnShowAllNewsClicked : SfeduEvent
    data class OnNewClicked(val id: String) : SfeduEvent
    data object RefreshNews : SfeduEvent

    // Schedule / Profile
    data object OnGroupClicked : SfeduEvent
    data class SaveProfile(val name: String, val group: String, val direction: String? = null) : SfeduEvent

    data object RetryClicked : SfeduEvent
}

package app.what.schedule.features.insts.rksi.domain.models

sealed interface RksiEvent {
    data class AuthClicked(val login: String, val pass: String) : RksiEvent
    data object LogoutClicked : RksiEvent
    data object MainOpened : RksiEvent
    data object OnGroupClicked : RksiEvent
    data object OnShowAllNewsClicked : RksiEvent
    data class OnNewClicked(val id: String) : RksiEvent

    data object LoadEnquiries : RksiEvent
    data class OrderEnquiryClicked(val typeId: String, val params: Map<String, String>) : RksiEvent

    data object LoadReAttestations : RksiEvent

    data object LoadPaymentQr : RksiEvent
    data class SelectQrTarget(val targetId: String) : RksiEvent
    data class SelectQrYear(val yearId: String) : RksiEvent
    data class SelectQrPeriod(val periodId: String) : RksiEvent

    data object LoadProfileEditor : RksiEvent
    data class SaveProfileClicked(val fields: Map<String, String>) : RksiEvent
    data class SaveSocialStatusClicked(val checkedIds: Set<String>, val allIds: List<String>) : RksiEvent
    data object ClearProfileSaveStatus : RksiEvent
}

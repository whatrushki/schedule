package app.what.schedule.features.insts.rksi.domain.models

import app.what.foundation.data.RemoteState
import app.what.schedule.core.models.AccountProfileDto
import app.what.schedule.core.models.EnquiryItemDto
import app.what.schedule.core.models.EnquiryTypeDto
import app.what.schedule.core.models.PaymentQrConfigDto
import app.what.schedule.core.models.ProfileSectionDto
import app.what.schedule.core.models.ReAttestationItemDto
import app.what.schedule.core.models.SocialStatusOptionDto

data class RksiState(
    val isAuthorized: Boolean = false,
    val isLoggingIn: Boolean = false,
    val loginError: String? = null,

    val profileFetchState: RemoteState = RemoteState.Idle,
    val profile: AccountProfileDto? = null,

    val enquiriesFetchState: RemoteState = RemoteState.Idle,
    val enquiries: List<EnquiryItemDto> = emptyList(),
    val enquiryTypes: List<EnquiryTypeDto> = emptyList(),
    val isOrderingEnquiry: Boolean = false,
    val orderEnquirySuccess: Boolean? = null,

    val reAttestationsFetchState: RemoteState = RemoteState.Idle,
    val reAttestations: List<ReAttestationItemDto> = emptyList(),

    val qrConfigFetchState: RemoteState = RemoteState.Idle,
    val qrConfig: PaymentQrConfigDto? = null,
    val selectedQrTarget: String? = null,
    val selectedQrYear: String? = null,
    val selectedQrPeriod: String? = null,
    val qrImageUrl: String? = null,
    val qrImageBytes: ByteArray? = null,
    val isQrLoading: Boolean = false,

    val profileSectionsFetchState: RemoteState = RemoteState.Idle,
    val profileSections: List<ProfileSectionDto> = emptyList(),
    val socialStatusFetchState: RemoteState = RemoteState.Idle,
    val socialStatusOptions: List<SocialStatusOptionDto> = emptyList(),
    val isSavingProfile: Boolean = false,
    val saveProfileSuccess: Boolean? = null,

    val newsFetchState: RemoteState = RemoteState.Idle,
    val news: List<app.what.domain.models.NewListItem> = emptyList()
)


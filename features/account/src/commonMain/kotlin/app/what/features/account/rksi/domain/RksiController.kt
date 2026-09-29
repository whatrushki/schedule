package app.what.schedule.features.insts.rksi.domain

import androidx.lifecycle.viewModelScope
import app.what.domain.models.ScheduleSearch
import app.what.foundation.core.UIController
import app.what.foundation.data.RemoteState
import app.what.foundation.utils.launchSafe
import app.what.schedule.data.local.settings.AppValues
import app.what.schedule.data.remote.api.InstitutionManager
import app.what.schedule.features.insts.rksi.domain.models.RksiAction
import app.what.schedule.features.insts.rksi.domain.models.RksiEvent
import app.what.schedule.features.insts.rksi.domain.models.RksiState
import app.what.schedule.rksi.RKSIAccountClient
import app.what.schedule.rksi.RksiSessionExpiredException
import kotlinx.coroutines.launch

class RksiController(
    private val institutionManager: InstitutionManager,
    private val accountClient: RKSIAccountClient,
    private val appValues: AppValues
) : UIController<RksiState, RksiAction, RksiEvent>(RksiState()) {

    private val debug get() = appValues.debugMode.get() == true

    private val newsService
        get() = institutionManager.getSavedInstitution()?.newsService

    init {
        val savedCookies = appValues.rksiCookies.get()
        if (!savedCookies.isNullOrBlank()) {
            accountClient.sessionCookies = savedCookies.split(";;").filter { it.isNotBlank() }
            updateState { copy(isAuthorized = true) }
            loadProfile()
            loadNews()
        } else {
            val login = appValues.rksiLogin.get()
            val pass = appValues.rksiPassword.get()
            if (!login.isNullOrBlank() && !pass.isNullOrBlank()) {
                viewModelScope.launchSafe(debug = debug) {
                    performLogin(login, pass)
                }
            }
        }
    }

    fun loadNews() {
        if (viewState.newsFetchState is RemoteState.Loading) return
        val service = newsService ?: return
        updateState { copy(newsFetchState = RemoteState.Loading) }
        viewModelScope.launchSafe(
            debug = debug,
            onFailure = {
                updateState { copy(newsFetchState = RemoteState.Error(it)) }
            }
        ) {
            val response = service.getNews(1).take(6)
            updateState { copy(newsFetchState = RemoteState.Success, news = response) }
        }
    }

    override fun obtainEvent(viewEvent: RksiEvent) {
        when (viewEvent) {
            is RksiEvent.AuthClicked -> {
                viewModelScope.launchSafe(debug = debug) {
                    performLogin(viewEvent.login, viewEvent.pass)
                }
            }
            RksiEvent.LogoutClicked -> logout()
            RksiEvent.MainOpened -> {
                if (viewState.isAuthorized) {
                    if (viewState.profile == null) loadProfile()
                    if (viewState.news.isEmpty()) loadNews()
                }
            }
            RksiEvent.OnGroupClicked -> {
                val groupName = viewState.profile?.group
                if (!groupName.isNullOrBlank()) {
                    setAction(RksiAction.OpenSchedule(ScheduleSearch.Group(name = groupName, id = groupName)))
                }
            }
            RksiEvent.OnShowAllNewsClicked -> setAction(RksiAction.OpenNews)
            is RksiEvent.OnNewClicked -> {
                val new = viewState.news.firstOrNull { it.id == viewEvent.id } ?: return
                setAction(RksiAction.OpenNewDetail(new.id, new.url, new.bannerUrl, new.title, new.description))
            }
            RksiEvent.LoadEnquiries -> loadEnquiries()
            is RksiEvent.OrderEnquiryClicked -> orderEnquiry(viewEvent.typeId, viewEvent.params)
            RksiEvent.LoadReAttestations -> loadReAttestations()
            RksiEvent.LoadPaymentQr -> loadPaymentQr()
            is RksiEvent.SelectQrTarget -> selectQrTarget(viewEvent.targetId)
            is RksiEvent.SelectQrYear -> selectQrYear(viewEvent.yearId)
            is RksiEvent.SelectQrPeriod -> selectQrPeriod(viewEvent.periodId)
            RksiEvent.LoadProfileEditor -> loadProfileEditor()
            is RksiEvent.SaveProfileClicked -> saveProfile(viewEvent.fields)
            is RksiEvent.SaveSocialStatusClicked -> saveSocialStatus(viewEvent.checkedIds, viewEvent.allIds)
            RksiEvent.ClearProfileSaveStatus -> updateState { copy(saveProfileSuccess = null) }
        }
    }

    private suspend fun performLogin(login: String, pass: String) {
        updateState { copy(isLoggingIn = true, loginError = null) }
        try {
            val success = accountClient.login(login, pass)
            if (success) {
                val cookieStr = accountClient.sessionCookies.joinToString(";;")
                appValues.rksiCookies.set(cookieStr)
                appValues.rksiLogin.set(login)
                appValues.rksiPassword.set(pass)
                updateState { copy(isAuthorized = true, isLoggingIn = false, loginError = null) }
                setAction(RksiAction.OpenMain)
                loadProfile()
                loadNews()
            } else {
                updateState {
                    copy(
                        isLoggingIn = false,
                        loginError = "Неверный логин или пароль"
                    )
                }
            }
        } catch (e: Exception) {
            updateState {
                copy(
                    isLoggingIn = false,
                    loginError = "Ошибка подключения к сети. Проверьте интернет."
                )
            }
        }
    }

    private suspend fun reLoginIfNeeded(): Boolean {
        val login = appValues.rksiLogin.get()
        val pass = appValues.rksiPassword.get()
        if (!login.isNullOrBlank() && !pass.isNullOrBlank()) {
            val success = accountClient.login(login, pass)
            if (success) {
                appValues.rksiCookies.set(accountClient.sessionCookies.joinToString(";;"))
                return true
            }
        }
        return false
    }

    private suspend fun <T> withReLogin(block: suspend () -> T): T {
        return try {
            block()
        } catch (e: RksiSessionExpiredException) {
            val reloginSuccess = try {
                reLoginIfNeeded()
            } catch (netEx: Exception) {
                // Network error during re-login attempt: DO NOT unauthorize the user!
                throw netEx
            }
            if (reloginSuccess) {
                block()
            } else {
                updateState { copy(isAuthorized = false) }
                appValues.rksiCookies.set(null)
                throw e
            }
        }
    }

    fun loadProfile() {
        if (viewState.profileFetchState is RemoteState.Loading) return
        updateState { copy(profileFetchState = RemoteState.Loading) }
        viewModelScope.launchSafe(
            debug = debug,
            onFailure = { error ->
                updateState { copy(profileFetchState = RemoteState.Error(error)) }
            }
        ) {
            val prof = withReLogin { accountClient.getProfile() }
            updateState { copy(profileFetchState = RemoteState.Success, profile = prof) }
        }
    }

    private fun logout() {
        appValues.rksiCookies.set(null)
        appValues.rksiLogin.set(null)
        appValues.rksiPassword.set(null)
        accountClient.sessionCookies = emptyList()
        updateState { RksiState() }
        setAction(RksiAction.OpenAuth)
    }

    fun loadEnquiries() {
        updateState { copy(enquiriesFetchState = RemoteState.Loading) }
        viewModelScope.launchSafe(
            debug = debug,
            onFailure = { error ->
                updateState { copy(enquiriesFetchState = RemoteState.Error(error)) }
            }
        ) {
            val (ordered, types) = withReLogin { accountClient.getEnquiries() }
            updateState {
                copy(
                    enquiriesFetchState = RemoteState.Success,
                    enquiries = ordered,
                    enquiryTypes = types
                )
            }
        }
    }

    fun orderEnquiry(typeId: String, params: Map<String, String>) {
        updateState { copy(isOrderingEnquiry = true, orderEnquirySuccess = null) }
        viewModelScope.launchSafe(
            debug = debug,
            onFailure = {
                updateState { copy(isOrderingEnquiry = false, orderEnquirySuccess = false) }
            }
        ) {
            val success = withReLogin { accountClient.orderEnquiry(typeId, params) }
            updateState { copy(isOrderingEnquiry = false, orderEnquirySuccess = success) }
            if (success) {
                loadEnquiries()
            }
        }
    }

    fun loadReAttestations() {
        updateState { copy(reAttestationsFetchState = RemoteState.Loading) }
        viewModelScope.launchSafe(
            debug = debug,
            onFailure = { error ->
                updateState { copy(reAttestationsFetchState = RemoteState.Error(error)) }
            }
        ) {
            val list = withReLogin { accountClient.getReAttestations() }
            updateState { copy(reAttestationsFetchState = RemoteState.Success, reAttestations = list) }
        }
    }

    fun loadPaymentQr() {
        updateState { copy(qrConfigFetchState = RemoteState.Loading) }
        viewModelScope.launchSafe(
            debug = debug,
            onFailure = { error ->
                updateState { copy(qrConfigFetchState = RemoteState.Error(error)) }
            }
        ) {
            val config = withReLogin { accountClient.getPaymentQrConfig() }
            val target = config.targets.firstOrNull()?.id
            val year = config.years.firstOrNull()?.id
            val period = config.periods.firstOrNull()?.id
            val qrUrl = if (target != null && year != null && period != null) {
                accountClient.buildPaymentQrImageUrl(target, year, period)
            } else null

            updateState {
                copy(
                    qrConfigFetchState = RemoteState.Success,
                    qrConfig = config,
                    selectedQrTarget = target,
                    selectedQrYear = year,
                    selectedQrPeriod = period,
                    qrImageUrl = qrUrl
                )
            }
            loadQrBytes(target, year, period)
        }
    }

    private fun loadQrBytes(targetId: String?, yearId: String?, periodId: String?) {
        if (targetId == null || yearId == null || periodId == null) return
        updateState { copy(isQrLoading = true) }
        viewModelScope.launchSafe(
            debug = debug,
            onFailure = {
                updateState { copy(isQrLoading = false) }
            }
        ) {
            val bytes = withReLogin { accountClient.getPaymentQrImageBytes(targetId, yearId, periodId) }
            updateState { copy(isQrLoading = false, qrImageBytes = bytes) }
        }
    }

    private fun selectQrTarget(targetId: String) {
        val year = viewState.selectedQrYear
        val period = viewState.selectedQrPeriod
        val newUrl = if (year != null && period != null) {
            accountClient.buildPaymentQrImageUrl(targetId, year, period)
        } else viewState.qrImageUrl
        updateState {
            copy(selectedQrTarget = targetId, qrImageUrl = newUrl)
        }
        loadQrBytes(targetId, year, period)
    }

    private fun selectQrYear(yearId: String) {
        val target = viewState.selectedQrTarget
        val period = viewState.selectedQrPeriod
        val newUrl = if (target != null && period != null) {
            accountClient.buildPaymentQrImageUrl(target, yearId, period)
        } else viewState.qrImageUrl
        updateState {
            copy(selectedQrYear = yearId, qrImageUrl = newUrl)
        }
        loadQrBytes(target, yearId, period)
    }

    private fun selectQrPeriod(periodId: String) {
        val target = viewState.selectedQrTarget
        val year = viewState.selectedQrYear
        val newUrl = if (target != null && year != null) {
            accountClient.buildPaymentQrImageUrl(target, year, periodId)
        } else viewState.qrImageUrl
        updateState {
            copy(selectedQrPeriod = periodId, qrImageUrl = newUrl)
        }
        loadQrBytes(target, year, periodId)
    }

    fun loadProfileEditor() {
        updateState {
            copy(
                profileSectionsFetchState = RemoteState.Loading,
                socialStatusFetchState = RemoteState.Loading,
                saveProfileSuccess = null
            )
        }
        viewModelScope.launchSafe(
            debug = debug,
            onFailure = { error ->
                updateState {
                    copy(
                        profileSectionsFetchState = RemoteState.Error(error),
                        socialStatusFetchState = RemoteState.Error(error)
                    )
                }
            }
        ) {
            val sections = withReLogin { accountClient.getProfileSections() }
            val socialOptions = withReLogin { accountClient.getSocialStatus() }
            updateState {
                copy(
                    profileSectionsFetchState = RemoteState.Success,
                    profileSections = sections,
                    socialStatusFetchState = RemoteState.Success,
                    socialStatusOptions = socialOptions
                )
            }
        }
    }

    private fun saveProfile(fields: Map<String, String>) {
        updateState { copy(isSavingProfile = true, saveProfileSuccess = null) }
        viewModelScope.launchSafe(
            debug = debug,
            onFailure = {
                updateState { copy(isSavingProfile = false, saveProfileSuccess = false) }
            }
        ) {
            val success = withReLogin { accountClient.saveProfile(fields) }
            updateState { copy(isSavingProfile = false, saveProfileSuccess = success) }
            if (success) {
                loadProfile()
                loadProfileEditor()
            }
        }
    }

    private fun saveSocialStatus(checkedIds: Set<String>, allIds: List<String>) {
        updateState { copy(isSavingProfile = true, saveProfileSuccess = null) }
        viewModelScope.launchSafe(
            debug = debug,
            onFailure = {
                updateState { copy(isSavingProfile = false, saveProfileSuccess = false) }
            }
        ) {
            val success = withReLogin { accountClient.saveSocialStatus(checkedIds, allIds) }
            updateState { copy(isSavingProfile = false, saveProfileSuccess = success) }
            if (success) {
                loadProfile()
                loadProfileEditor()
            }
        }
    }
}

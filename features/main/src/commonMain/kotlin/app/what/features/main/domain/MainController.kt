package app.what.schedule.features.main.domain

import androidx.lifecycle.viewModelScope
import app.what.foundation.core.UIController
import app.what.schedule.data.local.settings.AppValues
import app.what.schedule.data.remote.api.InstitutionManager
import app.what.schedule.features.main.domain.models.MainAction
import app.what.schedule.features.main.domain.models.MainEvent
import app.what.schedule.features.main.domain.models.MainState
import kotlinx.coroutines.launch

class MainController(
    private val institutionManager: InstitutionManager,
    private val settings: AppValues
) : UIController<MainState, MainAction, MainEvent>(
    MainState()
) {
    init {
        updateAccountFeature()
        viewModelScope.launch {
            settings.institution.observe().collect {
                updateAccountFeature()
            }
        }
    }

    private fun updateAccountFeature() {
        val accountService = institutionManager.getSavedInstitution()?.accountFeature
        updateState { copy(hasProfilePage = accountService != null, ui = accountService) }
    }
    
    override fun obtainEvent(viewEvent: MainEvent) = when (viewEvent) {
        else -> {}
    }
}
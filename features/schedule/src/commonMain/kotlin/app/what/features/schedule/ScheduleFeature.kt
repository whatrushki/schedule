package app.what.schedule.features.schedule

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import app.what.foundation.core.Feature
import app.what.foundation.services.AppLogger.Companion.Auditor
import app.what.navigation.core.NavComponent
import app.what.schedule.features.schedule.domain.ScheduleController
import app.what.schedule.features.schedule.domain.models.ScheduleEvent
import app.what.schedule.features.schedule.navigation.ScheduleProvider
import app.what.schedule.features.schedule.presentation.ScheduleView
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class ScheduleFeature(
    override val data: ScheduleProvider
) : Feature<ScheduleController, ScheduleEvent>(),
    NavComponent<ScheduleProvider>,
    KoinComponent {
    override val controller: ScheduleController by inject()
    
    init {
        val navTag = app.what.foundation.utils.buildTag(app.what.foundation.utils.LogScope.CORE, app.what.foundation.utils.LogCat.NAV)
        Auditor.debug(navTag, "ScheduleFeature init: data=$data, search=${data.search}")
        val search = data.search
        if (search != null)
            listener(ScheduleEvent.OnSearchClicked(search))
    }
    
    @Composable
    override fun content(modifier: Modifier) {
        val navTag = app.what.foundation.utils.buildTag(app.what.foundation.utils.LogScope.CORE, app.what.foundation.utils.LogCat.NAV)
        Auditor.debug(navTag, "ScheduleFeature.content() composed! modifier=$modifier, data=$data")
        Column(
            modifier.fillMaxSize()
        ) {
            val viewState = controller.collectStates()
            Auditor.debug(navTag, "ScheduleFeature state collected: scheduleState=${viewState.value.scheduleState}, schedulesCount=${viewState.value.schedules.size}, selected=${viewState.value.selectedSearch}")
            
            LaunchedEffect(Unit) {
                Auditor.debug(navTag, "ScheduleFeature LaunchedEffect(Unit) dispatching ScheduleEvent.Init")
                listener(ScheduleEvent.Init)
            }
            
            ScheduleView(viewState, listener)
        }
    }
}
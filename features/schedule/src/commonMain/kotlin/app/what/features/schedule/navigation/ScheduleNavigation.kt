package app.what.schedule.features.schedule.navigation

import app.what.navigation.core.NavProvider
import app.what.navigation.core.Registry
import app.what.navigation.core.register
import app.what.domain.models.ScheduleSearch
import app.what.foundation.services.AppLogger.Companion.Auditor
import app.what.schedule.features.schedule.ScheduleFeature
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable

@Serializable
data class ScheduleProvider(
    val searchName: String? = null,
    val searchId: String? = null,
    val isGroup: Boolean = true
) : NavProvider() {
    val search
        get() = if (searchId != null && searchName != null) {
            if (isGroup) ScheduleSearch.Group(searchName, searchId)
            else ScheduleSearch.Teacher(searchName, searchId)
        } else null
}

val scheduleRegistry: Registry = {
    composable<ScheduleProvider> { backStackEntry ->
        val provider = backStackEntry.toRoute<ScheduleProvider>()
        val screen = remember(provider) { ScheduleFeature(provider) }
        screen.content(Modifier.fillMaxSize())
    }
}


package app.what.schedule.features.news.navigation

import app.what.navigation.core.NavProvider
import app.what.navigation.core.Registry
import app.what.navigation.core.register
import app.what.foundation.services.AppLogger.Companion.Auditor
import app.what.schedule.features.news.NewsFeature
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable
object NewsProvider : NavProvider()

val newsRegistry: Registry = {
    val navTag = app.what.foundation.utils.buildTag(app.what.foundation.utils.LogScope.CORE, app.what.foundation.utils.LogCat.NAV)
    Auditor.debug(navTag, "newsRegistry executing")
    composable<NewsProvider> {
        val screen = remember { NewsFeature(NewsProvider) }
        screen.content(Modifier.fillMaxSize())
    }
}


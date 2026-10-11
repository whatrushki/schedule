package app.what.schedule.features.insts.rksi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.compose.composable
import app.what.foundation.core.Feature
import app.what.navigation.core.NavigationHost
import app.what.navigation.core.rememberHostNavigator
import app.what.navigation.core.rememberNavigator
import app.what.schedule.features.insts.rksi.domain.RksiController
import app.what.schedule.features.insts.rksi.domain.models.RksiAction
import app.what.schedule.features.insts.rksi.domain.models.RksiEvent
import app.what.schedule.features.insts.rksi.navigation.RKSIAuthProvider
import app.what.schedule.features.insts.rksi.navigation.RKSIMainProvider
import app.what.schedule.features.insts.rksi.presentation.pages.RksiLoginScreen
import app.what.schedule.features.insts.rksi.presentation.pages.RksiMainScreen
import app.what.schedule.features.schedule.navigation.ScheduleProvider
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

import app.what.foundation.services.AppLogger.Companion.Auditor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import app.what.foundation.core.UIComponent

class RksiFeature : Feature<RksiController, RksiEvent>(), KoinComponent {
    init {
        Auditor.debug("RksiFeature", ">>> RksiFeature init! hashCode=${hashCode()}")
    }

    override val controller: RksiController by inject()

    @Composable
    override fun content(modifier: Modifier) {
        RksiAccountContent(modifier, controller, listener)
    }
}

@Composable
fun RksiAccountContent(
    modifier: Modifier = Modifier,
    controller: RksiController = org.koin.compose.koinInject(),
    listener: (RksiEvent) -> Unit = { controller.obtainEvent(it) }
) {
    Auditor.debug("RksiFeature", ">>> STEP 1: RksiAccountContent ENTERED with modifier=$modifier")

    val action = controller.collectActions()
    val state = controller.collectStates()
    val nav = rememberHostNavigator()
    val midNav = rememberNavigator(1)
    val globalNav = rememberNavigator(2)

    Auditor.debug("RksiFeature", ">>> STEP 2: Navigators resolved: nav=$nav, midNav=$midNav, globalNav=$globalNav, isAuthorized=${state.value.isAuthorized}")

    NavigationHost(
        modifier,
        start = if (state.value.isAuthorized) RKSIMainProvider else RKSIAuthProvider,
        navigator = nav,
    ) {
        composable<RKSIAuthProvider> {
            Auditor.debug("RksiFeature", ">>> Composing RKSIAuthProvider screen")
            RksiLoginScreen(state, listener)
        }

        composable<RKSIMainProvider> {
            Auditor.debug("RksiFeature", ">>> Composing RKSIMainProvider screen")
            RksiMainScreen(state, listener)
        }
    }

    LaunchedEffect(action.value) {
        val ac = action.value ?: return@LaunchedEffect

        when (ac) {
            is RksiAction.OpenSchedule -> midNav.c.navigate(
                ScheduleProvider(
                    ac.search.name,
                    ac.search.id,
                    true
                )
            )

            is RksiAction.OpenNews -> midNav.c.navigate(app.what.schedule.features.news.navigation.NewsProvider)

            is RksiAction.OpenNewDetail -> globalNav.c.navigate(
                app.what.schedule.features.newsDetail.navigation.NewsDetailProvider(
                    ac.id, ac.url, ac.bannerUrl, ac.title, ac.description
                )
            )

            RksiAction.OpenAuth -> nav.c.navigate(RKSIAuthProvider) {
                popUpTo(0) { inclusive = true }
            }

            RksiAction.OpenMain -> nav.c.navigate(RKSIMainProvider) {
                popUpTo(0) { inclusive = true }
            }
        }

        controller.clearAction()
    }
}


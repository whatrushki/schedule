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

class RksiFeature : Feature<RksiController, RksiEvent>(), KoinComponent {
    override val controller: RksiController by inject()

    @Composable
    override fun content(modifier: Modifier) {
        val action = controller.collectActions()
        val state = controller.collectStates()
        val nav = rememberHostNavigator()
        val midNav = rememberNavigator(1)
        val globalNav = rememberNavigator(2)

        NavigationHost(
            modifier,
            start = if (state.value.isAuthorized) RKSIMainProvider else RKSIAuthProvider,
            navigator = nav,
        ) {
            composable<RKSIAuthProvider> {
                RksiLoginScreen(state, listener)
            }

            composable<RKSIMainProvider> {
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
}

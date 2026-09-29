package app.what.schedule.features.insts.sfedu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation.compose.composable
import app.what.foundation.core.Feature
import app.what.navigation.core.NavigationHost
import app.what.navigation.core.rememberHostNavigator
import app.what.navigation.core.rememberNavigator
import app.what.schedule.features.insts.sfedu.domain.SfeduController
import app.what.schedule.features.insts.sfedu.domain.models.SfeduAction
import app.what.schedule.features.insts.sfedu.domain.models.SfeduEvent
import app.what.schedule.features.insts.sfedu.navigation.SFEDUAuthProvider
import app.what.schedule.features.insts.sfedu.navigation.SFEDUMainProvider
import app.what.schedule.features.insts.sfedu.presentation.pages.SfeduLoginScreen
import app.what.schedule.features.insts.sfedu.presentation.pages.SfeduMainScreen
import app.what.schedule.features.schedule.navigation.ScheduleProvider
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SfeduFeature : Feature<SfeduController, SfeduEvent>(), KoinComponent {
    override val controller: SfeduController by inject()

    @Composable
    override fun content(modifier: Modifier) {
        val action = controller.collectActions()
        val state = controller.collectStates()
        val nav = rememberHostNavigator()
        val midNav = rememberNavigator(1)
        val globalNav = rememberNavigator(2)

        NavigationHost(
            Modifier,
            start = if (state.value.isAuthorized) SFEDUMainProvider else SFEDUAuthProvider,
            navigator = nav,
        ) {
            composable<SFEDUAuthProvider> {
                SfeduLoginScreen(state, listener)
            }

            composable<SFEDUMainProvider> {
                SfeduMainScreen(state, listener)
            }
        }

        LaunchedEffect(action.value) {
            val ac = action.value ?: return@LaunchedEffect

            when (ac) {
                is SfeduAction.OpenSchedule -> midNav.c.navigate(
                    ScheduleProvider(
                        ac.search.name,
                        ac.search.id,
                        true
                    )
                )

                is SfeduAction.OpenNews -> midNav.c.navigate(app.what.schedule.features.news.navigation.NewsProvider)

                is SfeduAction.OpenNewDetail -> globalNav.c.navigate(
                    app.what.schedule.features.newsDetail.navigation.NewsDetailProvider(
                        ac.id, ac.url, ac.bannerUrl, ac.title, ac.description
                    )
                )

                SfeduAction.OpenAuth -> nav.c.navigate(SFEDUAuthProvider) {
                    popUpTo(0) { inclusive = true }
                }

                SfeduAction.OpenMain -> nav.c.navigate(SFEDUMainProvider) {
                    popUpTo(0) { inclusive = true }
                }
            }

            controller.clearAction()
        }
    }
}

package app.what.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.what.foundation.services.AppLogger.Companion.Auditor
import app.what.foundation.utils.Analytics
import app.what.navigation.core.NavigationHost
import app.what.navigation.core.ProvideGlobalDialog
import app.what.navigation.core.ProvideGlobalSheet
import app.what.navigation.core.rememberHostNavigator
import app.what.schedule.data.local.settings.AppValues
import app.what.schedule.data.local.settings.ProvideGLobalAppValues
import app.what.schedule.features.main.navigation.MainProvider
import app.what.schedule.features.main.navigation.mainRegistry
import app.what.schedule.features.newsDetail.navigation.newsDetailRegistry
import app.what.schedule.features.onboarding.navigation.OnboardingProvider
import app.what.schedule.features.onboarding.navigation.onboardingRegistry
import app.what.schedule.ui.theme.AppTheme
import org.koin.compose.koinInject

import androidx.compose.foundation.layout.Column

@Composable
fun App(
    headerBanner: @Composable () -> Unit = {}
) {
    val navigator = rememberHostNavigator()
    val settings = koinInject<AppValues>()

    LaunchedEffect(Unit) {
        navigator.c.addOnDestinationChangedListener { _, destination, _ ->
            Auditor.debug("Nav", "Навигация: ${destination.route}")
            Analytics.logScreenView(destination.route ?: "no route")
        }
    }

    ProvideGLobalAppValues(settings) {
        AppTheme(settings) {
            ProvideGlobalDialog {
                ProvideGlobalSheet {
                    Column {
                        headerBanner()
                        NavigationHost(
                            start = if (settings.isFirstLaunch.get() == true) OnboardingProvider
                            else MainProvider
                        ) {
                            mainRegistry()
                            newsDetailRegistry()
                            onboardingRegistry()
                        }
                    }
                }
            }
        }
    }
}

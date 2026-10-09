package app.what.schedule.features.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import app.what.foundation.utils.isDesktop
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.navigation.compose.composable
import app.what.foundation.core.Feature
import app.what.foundation.services.AppLogger.Companion.Auditor
import app.what.foundation.services.LocalNotificationService
import app.what.foundation.services.rememberAppNotificationService
import app.what.foundation.ui.animations.AnimatedEnter
import app.what.foundation.ui.applyIf
import app.what.navigation.core.NavComponent
import app.what.navigation.core.NavProvider
import app.what.navigation.core.NavigationHost
import app.what.navigation.core.Registry
import androidx.compose.runtime.setValue
import app.what.foundation.ui.useState
import app.what.navigation.core.bottom_navigation.BottomNavBar
import app.what.navigation.core.bottom_navigation.SideNavBar
import app.what.navigation.core.bottom_navigation.NavAction
import app.what.navigation.core.bottom_navigation.NavItem
import app.what.navigation.core.bottom_navigation.navItem
import app.what.navigation.core.rememberHostNavigator
import app.what.schedule.data.local.settings.rememberAppValues
import app.what.schedule.features.dev.navigation.DevProvider
import app.what.schedule.features.dev.navigation.devRegistry
import app.what.schedule.features.main.domain.MainController
import app.what.schedule.features.main.domain.models.MainEvent
import app.what.schedule.features.main.navigation.MainProvider
import app.what.schedule.features.news.navigation.NewsProvider
import app.what.schedule.features.news.navigation.newsRegistry
import app.what.schedule.features.schedule.navigation.ScheduleProvider
import app.what.schedule.features.schedule.navigation.scheduleRegistry
import app.what.schedule.features.settings.navigation.SettingsProvider
import app.what.schedule.features.settings.navigation.settingsRegistry
import app.what.foundation.ui.icons.WHATIcons
import app.what.foundation.ui.icons.filled.FrameBug
import app.what.foundation.ui.icons.filled.News
import kotlinx.coroutines.flow.drop
import app.what.foundation.ui.icons.filled.Person
import app.what.foundation.utils.Analytics
import app.what.foundation.utils.LogCat
import app.what.foundation.utils.LogScope
import app.what.foundation.utils.buildTag
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Serializable
object AccountProvider : NavProvider()

class MainFeature(
    override val data: MainProvider
) : Feature<MainController, MainEvent>(),
    NavComponent<MainProvider>,
    KoinComponent {
    
    override val controller: MainController by inject()
    
    val children: List<NavItem>
        get() = buildList {
            add(navItem("Новости", WHATIcons.News, NewsProvider))
            add(navItem("Расписание", Icons.Default.DateRange, ScheduleProvider()))
            add(navItem("Настройки", Icons.Default.Settings, SettingsProvider))
            if (controller.getState().hasProfilePage) {
                add(0, navItem("Профиль", WHATIcons.Person, AccountProvider))
            }
        }
    
    val childrenRegistry: Registry = {
        settingsRegistry()
        newsRegistry()
        scheduleRegistry()
        devRegistry()
        composable<AccountProvider> {
            val mainState by controller.collectStates()
            val ui = mainState.ui
            if (ui != null) {
                ui.content(Modifier.fillMaxSize())
            } else {
                app.what.schedule.ui.components.Fallback(
                    text = "Профиль недоступен для выбранного учебного заведения",
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
    
    @Composable
    override fun content(modifier: Modifier) {
        val state = controller.collectStates()
        val navigator = rememberHostNavigator()
        val appValues = rememberAppValues()
        val devFeaturesEnabled by appValues.devPanelEnabled.collect()
        val currentInstitution by appValues.institution.collect()
        val dgtuToken by appValues.dgtuToken.collect()
        val rksiCookies by appValues.rksiCookies.collect()
        val sfeduGradeToken by appValues.sfeduGradeToken.collect()
        val enableProfileTab by appValues.enableProfileTab.collect()
        val isAccountAuthorized = state.value.hasProfilePage && enableProfileTab != false && when (currentInstitution?.lowercase()) {
            "dgtu" -> !dgtuToken.isNullOrBlank()
            "rksi" -> !rksiCookies.isNullOrBlank()
            "sfedu" -> !sfeduGradeToken.isNullOrBlank()
            else -> false
        }

        val screens = remember(isAccountAuthorized, state.value.hasProfilePage, enableProfileTab) {
            buildList {
                if (!isAccountAuthorized) {
                    add(navItem("Новости", WHATIcons.News, NewsProvider))
                }
                add(navItem("Расписание", Icons.Default.DateRange, ScheduleProvider()))
                add(navItem("Настройки", Icons.Default.Settings, SettingsProvider))
                if (state.value.hasProfilePage && enableProfileTab != false) {
                    add(0, navItem("Профиль", WHATIcons.Person, AccountProvider))
                }
            }
        }
//        var showBottomNavBar by useState(true)
        
        LaunchedEffect(Unit) {
            navigator.c.addOnDestinationChangedListener { _, destination, _ ->
                val navTag = buildTag(LogScope.CORE, LogCat.NAV)
                Auditor.debug(navTag, "Навигация: ${destination.route}")
                Analytics.logScreenView(destination.route ?: "no route")
            }
        }

        LaunchedEffect(Unit) {
            appValues.institution.observe().drop(1).collect {
                navigator.c.navigate(ScheduleProvider()) {
                    popUpTo(navigator.c.graph.startDestinationId) {
                        inclusive = false
                    }
                    launchSingleTop = true
                }
            }
        }
        val dialog = app.what.foundation.ui.controllers.rememberDialogController()

        LaunchedEffect(Unit) {
            val lastSeen = appValues.lastSeenVersion.get() ?: ""
            val currentVersion = app.what.domain.constants.AppConstants.VERSION_NAME
            val isFirstLaunch = appValues.isFirstLaunch.get() ?: true

            if (!isFirstLaunch && lastSeen.isNotBlank() && lastSeen != currentVersion) {
                val bundledNotes = app.what.features.onboarding.presentation.loadBundledReleaseNotes()
                val cachedJson = appValues.cachedReleaseNotes.get()
                val cachedNotes = cachedJson?.let { app.what.domain.services.ReleaseNotes.fromJson(it) }

                // Пытаемся получить объединенные заметки через GitHubUpdateService с таймаутом
                val fetchedMergedNotes = try {
                    kotlinx.coroutines.withTimeoutOrNull(2500) {
                        val gitHubService = getKoin().getOrNull<app.what.foundation.services.auto_update.GitHubUpdateService>()
                            ?: app.what.foundation.services.auto_update.GitHubUpdateService(getKoin().get())
                        gitHubService.fetchMergedReleaseNotes(
                            owner = "whatrushki",
                            repo = "schedule",
                            fromVersion = lastSeen,
                            toVersion = currentVersion
                        )
                    }
                } catch (_: Exception) {
                    null
                }

                val releaseNotes = fetchedMergedNotes
                    ?: when {
                        bundledNotes != null && cachedNotes != null -> bundledNotes.mergeWith(cachedNotes)
                        bundledNotes != null && bundledNotes.version == currentVersion -> bundledNotes
                        cachedNotes != null && cachedNotes.version == currentVersion -> cachedNotes
                        else -> bundledNotes ?: cachedNotes
                    }

                if (releaseNotes != null) {
                    dialog.open(full = true) {
                        app.what.features.onboarding.presentation.UpdateOnboardingContent(
                            releaseNotes = releaseNotes,
                            appValues = appValues,
                            onDismiss = {
                                appValues.lastSeenVersion.set(currentVersion)
                                dialog.close()
                            },
                            onNavigate = { route ->
                                appValues.lastSeenVersion.set(currentVersion)
                                dialog.close()
                                when (route.lowercase()) {
                                    "profile" -> if (isAccountAuthorized) navigator.c.navigate(AccountProvider)
                                    "settings" -> navigator.c.navigate(SettingsProvider)
                                    else -> Unit
                                }
                            }
                        )
                    }
                } else {
                    appValues.lastSeenVersion.set(currentVersion)
                }
            } else if (lastSeen.isBlank()) {
                appValues.lastSeenVersion.set(currentVersion)
            }
        }
        
        val notificationService = rememberAppNotificationService()

        CompositionLocalProvider(
            LocalNotificationService provides notificationService
        ) {
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .background(colorScheme.background)
            ) {
                val isLandscape = maxWidth > maxHeight && maxWidth >= 480.dp
                val isWideScreen = isDesktop || maxWidth >= 760.dp || isLandscape

                if (isWideScreen) {
                    Row(
                        Modifier
                            .fillMaxSize()
                            .displayCutoutPadding()
                            .systemBarsPadding()
                    ) {
                        SideNavBar(
                            navigator = navigator,
                            screens = screens,
                            modifier = Modifier.padding(start = 8.dp, end = 8.dp)
                        ) {
                            if (!devFeaturesEnabled!!) null
                            else NavAction("Для разработчиков", WHATIcons.FrameBug) {
                                Analytics.logDevPanelOpen()
                                navigator.c.navigate(DevProvider)
                            }
                        }

                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            NavigationHost(
                                modifier = Modifier.fillMaxSize(),
                                navigator = navigator,
                                start = ScheduleProvider(),
                                registry = childrenRegistry
                            )
                        }
                    }
                } else {
                    Box(Modifier.fillMaxSize()) {
                        NavigationHost(
                            modifier = Modifier.fillMaxSize(),
                            navigator = navigator,
                            start = ScheduleProvider(),
                            registry = childrenRegistry
                        )

                        AnimatedEnter(
                            modifier = Modifier.align(Alignment.BottomCenter)
                        ) {
                            BottomNavBar(
                                navigator = navigator,
                                screens = screens,
                            ) {
                                if (!devFeaturesEnabled!!) null
                                else NavAction("Для разработчиков", WHATIcons.FrameBug) {
                                    Analytics.logDevPanelOpen()
                                    navigator.c.navigate(DevProvider)
                                }
                            }
                        }
                    }
                }

                notificationService.content(
                    Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 8.dp)
                        .zIndex(99f)
                )
            }
        }
    }
}
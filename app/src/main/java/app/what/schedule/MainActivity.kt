package app.what.schedule

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import app.what.foundation.services.AppLogger.Companion.Auditor
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
import app.what.schedule.utils.Analytics
import app.what.schedule.utils.LogCat
import app.what.schedule.utils.LogScope
import android.content.res.Configuration
import app.what.schedule.launcher.AppIconManager
import app.what.schedule.utils.buildTag
import com.google.firebase.crashlytics.FirebaseCrashlytics
import org.koin.android.ext.android.inject
import org.koin.compose.koinInject

open class MainActivity : ComponentActivity() {
    private val crashlytics = FirebaseCrashlytics.getInstance()
    private val appValues: AppValues by inject()
    
    private val notificationPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            val tag = buildTag(LogScope.CORE, LogCat.STATE)
            Auditor.info(tag, "Пользователь отклонил разрешение на отправку уведомлений")
            appValues.enableReplacementNotifications.set(false)
            appValues.enableUniversityNotifications.set(false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !shouldShowRequestPermissionRationale(android.Manifest.permission.POST_NOTIFICATIONS)
            ) {
                android.widget.Toast.makeText(
                    this,
                    "Уведомления отключены в настройках устройства",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        } else {
            val tag = buildTag(LogScope.CORE, LogCat.STATE)
            Auditor.info(tag, "Разрешение на отправку уведомлений предоставлено")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val uiTag = buildTag(LogScope.UI, LogCat.INIT)
        Auditor.info(uiTag, "MainActivity создана")
        AppIconManager.updateIcon(this, appValues)
        
        enableEdgeToEdge()
        setContent {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.setNavigationBarContrastEnforced(false)
            }
            
            val navigator = rememberHostNavigator()
            val settings = koinInject<AppValues>()
            
            LaunchedEffect(Unit) {
                navigator.c.addOnDestinationChangedListener { _, destination, _ ->
                    val navTag = buildTag(LogScope.CORE, LogCat.NAV)
                    Auditor.debug(navTag, "Навигация: ${destination.route}")
                    Analytics.logScreenView(destination.route ?: "no route")
                    crashlytics.setCustomKey("current_screen", destination.route ?: "unknown")
                }
            }

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    kotlinx.coroutines.flow.combine(
                        settings.enableReplacementNotifications.observe(),
                        settings.enableUniversityNotifications.observe()
                    ) { repl, univ -> repl == true || univ == true }
                        .collect { enabled ->
                            if (enabled && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                }
            }
            
            ProvideGLobalAppValues(settings) {
                AppTheme {
                    ProvideGlobalDialog {
                        ProvideGlobalSheet {
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

    override fun onResume() {
        super.onResume()
        AppIconManager.updateIcon(this, appValues)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        AppIconManager.updateIcon(this, appValues)
    }
}

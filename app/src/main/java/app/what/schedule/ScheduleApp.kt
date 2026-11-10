package app.what.schedule

import android.app.Application
import androidx.room.Room
import app.what.foundation.data.settings.PreferenceStorage
import app.what.foundation.services.AppLogger
import app.what.foundation.services.AppLogger.Companion.Auditor
import app.what.foundation.services.initialize
import app.what.foundation.services.auto_update.AppUpdateManager
import app.what.foundation.services.auto_update.InstallSource
import app.what.foundation.services.auto_update.RuStoreUpdateManager
import app.what.foundation.services.auto_update.UpdateConfig
import app.what.foundation.services.auto_update.getInstallSource
import app.what.foundation.services.crash.CrashHandler
import app.what.schedule.data.local.database.AppDatabase
import app.what.data.di.dataModule
import app.what.features.main.di.mainFeatureModule
import app.what.domain.repositories.ScheduleRepository
import app.what.schedule.data.local.settings.AppValues
import app.what.schedule.utils.AppUtils
import app.what.schedule.utils.LogCat
import app.what.schedule.utils.LogScope
import app.what.schedule.utils.buildTag
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.analytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import io.ktor.client.HttpClient
import org.koin.android.ext.android.getKoin
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.singleOf
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.datastore.preferences.core.longPreferencesKey
import app.what.schedule.features.widget.ScheduleWidget
import app.what.schedule.notifications.FirstLessonScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import androidx.work.Configuration
import app.what.schedule.notifications.NotificationHelper
import app.what.schedule.notifications.ScheduleWorkManager
import com.jakewharton.processphoenix.ProcessPhoenix
import android.content.res.Configuration as AndroidConfiguration
import android.util.Log
import app.what.data.managers.PlatformFileStorageCleaner
import app.what.domain.services.FeedbackSharer
import app.what.foundation.auth.client.WhatAuthManager
import app.what.foundation.auth.di.createAndroidAuthPreferences
import app.what.foundation.auth.di.createAndroidSsoBridge
import app.what.foundation.auth.model.WhatAuthConfig
import app.what.foundation.utils.CurrentActivityHolder
import app.what.schedule.feedback.AndroidFeedbackSharer
import app.what.schedule.launcher.AppIconManager
import app.what.schedule.notifications.university.UniversityNotificationManager
import app.what.schedule.notifications.university.checkers.DgtuNotificationChecker
import app.what.schedule.notifications.university.checkers.SfeduNotificationChecker
import app.what.schedule.updater.AppGitHubUpdateManager
import app.what.schedule.utils.AndroidPlatformFileStorageCleaner
import app.what.schedule.utils.AppUserIdProvider
import com.yandex.mobile.ads.common.YandexAds
import org.koin.dsl.module
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ScheduleApp : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()

        if (ProcessPhoenix.isPhoenixProcess(this)) {
            return
        }
        
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this)
        }
        
        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.setCustomKey("app_version", BuildConfig.VERSION_NAME)
        crashlytics.setCustomKey("app_version_code", BuildConfig.VERSION_CODE)
        
        AppLogger.initialize(applicationContext)
        CrashHandler.initialize(applicationContext, CrashActivity::class.java)
            .setSideEffect(crashlytics::recordException)
        
        CurrentActivityHolder.register(this)
        
        val initTag = buildTag(LogScope.CORE, LogCat.INIT)
        try {
            YandexAds.initialize(this) {
                Auditor.info(initTag, "Yandex Mobile Ads initialized")
            }
        } catch (e: Exception) {
            Auditor.warn(initTag, "Failed to initialize Yandex Mobile Ads: ${e.message}")
        }
        Auditor.info(initTag, "Приложение запущено")
        
        startKoin {
            allowOverride(true)
            androidContext(this@ScheduleApp)
            modules(dataModule, mainFeatureModule, appModule)
        }
        
        val koin = getKoin()
        
        val appValues = koin.get<AppValues>()
        val stableUserId = AppUserIdProvider.getOrCreateUserId(this, appValues.userId.get())
        if (appValues.userId.get() != stableUserId) {
            appValues.userId.set(stableUserId)
            Auditor.debug(initTag, "Установлен стабильный ID пользователя: $stableUserId")
        } else {
            Auditor.debug(initTag, "Пользователь уже существует: $stableUserId")
        }
        crashlytics.setUserId(stableUserId)
        Firebase.analytics.setUserId(stableUserId)
        
        SingletonImageLoader.setSafe {
            ImageLoader.Builder(this)
                .crossfade(true)
                .components {
                    add(KtorNetworkFetcherFactory({ koin.get<HttpClient>() }))
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(cacheDir.resolve("image_cache"))
                        .maxSizeBytes(50L * 1024 * 1024)
                        .build()
                }
                .build()
        }
        
        val source = getInstallSource(this)
        
        when (source) {
            InstallSource.APK -> Auditor.debug("d", "install source Apk")
            InstallSource.RuStore -> Auditor.debug("d", "install source RuStore")
        }

        // Автоматическое обновление виджетов при смене темы пользователем в приложении
        val appScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        @OptIn(FlowPreview::class)
        appScope.launch {
            combine(
                appValues.themeType.observe(),
                appValues.themeStyle.observe(),
                appValues.themeColor.observe()
            ) { _, _, _ -> }
                .drop(1)
                .debounce(300.milliseconds)
                .collect {
                    try {
                        val manager = GlanceAppWidgetManager(this@ScheduleApp)
                        val glanceIds = manager.getGlanceIds(ScheduleWidget::class.java)
                        for (glanceId in glanceIds) {
                            try {
                                updateAppWidgetState(this@ScheduleApp, glanceId) { prefs ->
                                    prefs[longPreferencesKey("theme_timestamp")] = System.currentTimeMillis()
                                }
                            } catch (_: Exception) {}
                        }
                        ScheduleWidget.instance.updateAll(this@ScheduleApp)
                    } catch (e: Exception) {
                        Auditor.debug(initTag, "Не удалось обновить виджеты при смене темы: ${e.message}")
                    }
                }
        }

        // Автоматическое переключение иконки приложения (алиаса) при смене темы в настройках
        AppIconManager.init(this@ScheduleApp, appValues)
        appScope.launch {
            combine(
                appValues.themeType.observe(),
                appValues.themeStyle.observe(),
                appValues.themeColor.observe()
            ) { _, _, _ -> }
                .collect {
                    AppIconManager.updateIcon(this@ScheduleApp, appValues)
                }
        }

        val scheduleRepository = koin.get<ScheduleRepository>()

        // Автоматическое обновление виджетов при получении свежего расписания из сети
        appScope.launch {
            scheduleRepository.scheduleUpdates
                .debounce(500.milliseconds)
                .collect {
                    try {
                        ScheduleWidget.instance.updateAll(this@ScheduleApp)
                        Auditor.debug(initTag, "Виджеты успешно обновлены после получения нового расписания")
                    } catch (e: Exception) {
                        Auditor.debug(initTag, "Не удалось обновить виджеты при получении расписания: ${e.message}")
                    }
                }
        }

        // Автоматическое обновление виджетов при смене выбранной группы в приложении
        appScope.launch {
            appValues.lastSearch.observe()
                .drop(1)
                .debounce(350)
                .collect {
                    try {
                        ScheduleWidget.instance.updateAll(this@ScheduleApp)
                    } catch (e: Exception) {
                        Auditor.debug(initTag, "Не удалось обновить виджеты при смене группы: ${e.message}")
                    }
                }
        }

        // Инициализация канала уведомлений
        NotificationHelper.createNotificationChannel(this)

        // Планирование / отмена периодической проверки замен и уведомлений универа в фоне
        var isFirstCollection = true
        appScope.launch {
            combine(
                appValues.enableReplacementNotifications.observe(),
                appValues.enableUniversityNotifications.observe(),
                appValues.replacementNotificationsPeriod.observe()
            ) { repEnabled, uniEnabled, period -> Triple(repEnabled == true, uniEnabled == true, period) }
                .collect { (repEnabled, uniEnabled, period) ->
                    val isFirst = isFirstCollection
                    isFirstCollection = false
                    if (repEnabled || uniEnabled) {
                        val hours = period?.hours ?: 3
                        ScheduleWorkManager.schedulePeriodicCheck(this@ScheduleApp, hours, forceUpdate = !isFirst)
                        if (!isFirst) {
                            ScheduleWorkManager.triggerImmediateCheck(this@ScheduleApp)
                        }
                    } else {
                        ScheduleWorkManager.cancelPeriodicCheck(this@ScheduleApp)
                    }
                }
        }

        // Автоматическое планирование напоминания о первой паре за 40 минут
        appScope.launch {
            combine(
                appValues.enableFirstLessonNotification.observe(),
                appValues.lastSearch.observe()
            ) { _, _ -> }
                .collect {
                    try {
                        FirstLessonScheduler.scheduleNext(this@ScheduleApp)
                    } catch (e: Exception) {
                        Auditor.debug(initTag, "Не удалось запланировать напоминание о первой паре: ${e.message}")
                    }
                }
        }
    }

    override fun onConfigurationChanged(newConfig: AndroidConfiguration) {
        super.onConfigurationChanged(newConfig)
        try {
            val appValues = getKoin().getOrNull<AppValues>()
            if (appValues != null) {
                AppIconManager.updateIcon(this, appValues)
            }
        } catch (_: Exception) {
        }
    }
}

val appModule = module {
    singleOf(::AppUtils)
    single<PlatformFileStorageCleaner> { AndroidPlatformFileStorageCleaner(androidContext()) }
    single<FeedbackSharer> { AndroidFeedbackSharer(androidContext(), get()) }
    single { createAndroidAuthPreferences(androidContext()) }
    single { createAndroidSsoBridge(androidContext()) }
    single {
        val config = WhatAuthConfig(
            serverUrl = "https://what-id.whatrushki-tech.workers.dev",
            clientId = "what-schedule",
            redirectUri = "app.what.auth://callback",
            appName = "WHAT Schedule"
        )
        WhatAuthManager(
            config = config,
            httpClient = get(),
            prefs = get(),
            ssoBridge = get()
        )
    }
    
    single<AppUpdateManager> {
        val context = androidContext()
        val isRuStore = BuildConfig.FLAVOR == "rustore" || getInstallSource(context) == InstallSource.RuStore
        
        if (isRuStore) {
            RuStoreUpdateManager(context, get())
        } else {
            AppGitHubUpdateManager(
                context = androidContext(),
                config = UpdateConfig(
                    BuildConfig.APP_GITHUB_URL.split("/").reversed()[1],
                    BuildConfig.APP_GITHUB_URL.split("/").reversed()[0],
                    BuildConfig.VERSION_NAME
                ),
                httpClient = get(),
                appValues = get()
            )
        }
    }

    single {
        UniversityNotificationManager(
            listOf(
                DgtuNotificationChecker(get(), get()),
                SfeduNotificationChecker(get(), get())
            )
        )
    }
}
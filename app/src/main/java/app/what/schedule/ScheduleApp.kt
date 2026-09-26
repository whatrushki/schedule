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
import androidx.glance.appwidget.updateAll
import app.what.schedule.features.widget.ScheduleWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import app.what.schedule.notifications.NotificationHelper
import app.what.schedule.notifications.ScheduleWorkManager
import org.koin.dsl.module
import java.util.UUID

class ScheduleApp : Application() {
    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        
        if (FirebaseApp.getApps(this).isEmpty()) {
            FirebaseApp.initializeApp(this)
        }
        
        val crashlytics = FirebaseCrashlytics.getInstance()
        crashlytics.setCustomKey("app_version", BuildConfig.VERSION_NAME)
        crashlytics.setCustomKey("app_version_code", BuildConfig.VERSION_CODE)
        
        AppLogger.initialize(applicationContext)
        CrashHandler.initialize(applicationContext, CrashActivity::class.java)
            .setSideEffect(crashlytics::recordException)
        
        app.what.foundation.utils.CurrentActivityHolder.register(this)
        
        val initTag = buildTag(LogScope.CORE, LogCat.INIT)
        Auditor.info(initTag, "Приложение запущено")
        
        startKoin {
            androidContext(this@ScheduleApp)
            modules(dataModule, mainFeatureModule, appModule)
        }
        
        val koin = getKoin()
        
        val appValues = koin.get<AppValues>()
        if (appValues.userId.get() == null) {
            val userId = UUID.randomUUID().toString()
            crashlytics.setUserId(userId)
            Firebase.analytics.setUserId(userId)
            appValues.userId.set(userId)
            Auditor.debug(initTag, "Создан новый пользователь: $userId")
        } else {
            Auditor.debug(initTag, "Пользователь уже существует: ${appValues.userId.get()}")
        }
        
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
                .debounce(350)
                .collect {
                    try {
                        ScheduleWidget.instance.updateAll(this@ScheduleApp)
                    } catch (e: Exception) {
                        Auditor.debug(initTag, "Не удалось обновить виджеты при смене темы: ${e.message}")
                    }
                }
        }

        val scheduleRepository = koin.get<ScheduleRepository>()

        // Автоматическое обновление виджетов при получении свежего расписания из сети
        appScope.launch {
            scheduleRepository.scheduleUpdates
                .debounce(500)
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

        // Планирование / отмена периодической проверки замен в фоне
        appScope.launch {
            combine(
                appValues.enableReplacementNotifications.observe(),
                appValues.replacementNotificationsPeriod.observe()
            ) { enabled, period -> enabled to period }
                .collect { (enabled, period) ->
                    if (enabled == true) {
                        val hours = period?.hours ?: 3
                        ScheduleWorkManager.schedulePeriodicCheck(this@ScheduleApp, hours)
                    } else {
                        ScheduleWorkManager.cancelPeriodicCheck(this@ScheduleApp)
                    }
                }
        }
    }
}

val appModule = module {
    singleOf(::AppUtils)
    
    single<AppUpdateManager> {
        val context = androidContext()
        val source = getInstallSource(context)
        
        when (source) {
            InstallSource.RuStore -> RuStoreUpdateManager(context, get())
            InstallSource.APK -> app.what.schedule.updater.AppGitHubUpdateManager(
                context = androidContext(),
                config = UpdateConfig(
                    BuildConfig.APP_GITHUB_URL.split("/").reversed()[1],
                    BuildConfig.APP_GITHUB_URL.split("/").reversed()[0],
                    BuildConfig.VERSION_NAME
                ),
                httpClient = get()
            )
        }
    }
}
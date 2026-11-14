package app.what.data.di

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.what.data.cache.JvmFileCache
import app.what.data.libs.GoogleDriveParser
import app.what.data.managers.CacheStorageManager
import app.what.data.managers.CacheStorageManagerImpl
import app.what.data.managers.NoOpPlatformFileStorageCleaner
import app.what.data.managers.PlatformFileStorageCleaner
import app.what.data.network.traffic.DefaultNetworkTypeProvider
import app.what.data.network.traffic.NetworkTrafficManager
import app.what.data.network.traffic.NetworkTrafficManagerImpl
import app.what.data.network.traffic.NetworkTypeProvider
import app.what.data.network.traffic.TrafficTrackerPlugin
import app.what.data.repositories.NewsRepositoryImpl
import app.what.data.repositories.ScheduleRepositoryImpl
import app.what.domain.constants.AppConstants
import app.what.domain.repositories.NewsRepository
import app.what.domain.repositories.ScheduleRepository
import app.what.domain.services.FeedbackSharer
import app.what.domain.services.NoOpFeedbackSharer
import app.what.domain.services.NetworkConnectivity
import app.what.foundation.data.settings.JvmKeyValueStorage
import app.what.foundation.data.settings.KeyValueStorage
import app.what.foundation.data.settings.PreferenceStorage
import app.what.foundation.network.monitor.NetworkMonitorPlugin
import app.what.foundation.services.AppLogger.Companion.Auditor
import app.what.foundation.services.auto_update.AppUpdateManager
import app.what.foundation.services.auto_update.DesktopUpdateManager
import app.what.foundation.services.auto_update.GitHubUpdateService
import app.what.foundation.services.auto_update.UpdateConfig
import app.what.foundation.utils.AppUtils
import app.what.foundation.utils.LogCat
import app.what.foundation.utils.LogScope
import app.what.foundation.utils.buildTag
import app.what.schedule.core.cache.FileCache
import app.what.schedule.data.local.database.AppDatabase
import app.what.schedule.data.local.database.AppDatabaseSource
import app.what.schedule.data.local.settings.AppValues
import app.what.schedule.data.remote.api.InstitutionManager
import app.what.schedule.dgtu.DGTUAccountClient
import app.what.schedule.rksi.RKSIAccountClient
import app.what.schedule.rksi.parser.JvmXlsxReader
import app.what.schedule.rksi.parser.XlsxReader
import app.what.schedule.sfedu.grade.SfeduGradeClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import java.awt.Desktop
import java.io.File
import java.net.ConnectException
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

val dataModule = module {
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    
    single<KeyValueStorage> {
        JvmKeyValueStorage()
    }
    single { AppValues(get<KeyValueStorage>()) } bind PreferenceStorage::class
    single<NetworkTypeProvider> {
        DefaultNetworkTypeProvider()
    }
    single<NetworkTrafficManager> {
        NetworkTrafficManagerImpl(get(), get())
    }
    singleOf(::GoogleDriveParser)
    single<FileCache> { JvmFileCache() }
    single { DGTUAccountClient(get()) }
    single { RKSIAccountClient(get()) }
    single { SfeduGradeClient(get()) }
    single { InstitutionManager(get(), get()) }
    singleOf(::AppUtils)
    single<XlsxReader> { JvmXlsxReader() }
    
    single<AppUpdateManager> {
        DesktopUpdateManager(
            gitHubService = GitHubUpdateService(get()),
            config = UpdateConfig(
                githubOwner = "whatrushki",
                githubRepo = "schedule",
                currentVersion = AppConstants.VERSION_NAME
            ),
            openUrl = { url ->
                try {
                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().browse(URI(url))
                    }
                } catch (_: Exception) {}
            },
            scope = get()
        )
    }
    
    single<NetworkConnectivity> {
        object : NetworkConnectivity {
            override fun isConnected(): Boolean {
                return try {
                    val interfaces = NetworkInterface.getNetworkInterfaces()
                    interfaces?.asSequence()?.any { it.isUp && !it.isLoopback } ?: true
                } catch (_: Exception) {
                    true
                }
            }
        }
    }
    
    single<ScheduleRepository> {
        ScheduleRepositoryImpl(get(), get(), get(), getOrNull())
    }
    
    single<NewsRepository> {
        NewsRepositoryImpl(get(), getOrNull())
    }
    
    single {
        val dbFile = File(System.getProperty("user.home"), ".what_schedule/schedule.db")
        dbFile.parentFile?.mkdirs()
        Room.databaseBuilder<AppDatabase>(
            name = dbFile.absolutePath,
        )
            .setDriver(BundledSQLiteDriver())
            .fallbackToDestructiveMigration(true)
            .build()
    } bind AppDatabaseSource::class
    
    single {
        HttpClient(CIO) {
            followRedirects = true
            install(HttpRequestRetry) {
                maxRetries = 3
                retryOnExceptionIf { _, cause ->
                    cause is UnknownHostException ||
                            cause is ConnectException ||
                            cause is SocketTimeoutException
                }
                retryOnServerErrors(maxRetries = 2)
                exponentialDelay(baseDelayMs = 300L, maxDelayMs = 4000L)
            }
            
            install(Logging) {
                logger = object : Logger {
                    override fun log(message: String) {
                        Auditor.debug(buildTag(LogScope.NETWORK, LogCat.NET), message)
                    }
                }
            }
            
            install(NetworkMonitorPlugin)
            
            val trafficManager = get<NetworkTrafficManager>()
            val networkTypeProvider = get<NetworkTypeProvider>()
            install(TrafficTrackerPlugin) {
                this.trafficManager = trafficManager
                this.networkTypeProvider = networkTypeProvider
            }
            
            install(ContentNegotiation) {
                json(Json {
                    classDiscriminator = "type"
                    ignoreUnknownKeys = true
                    prettyPrint = false
                    isLenient = true
                    explicitNulls = false
                    coerceInputValues = true
                })
            }
            
            install(HttpTimeout) {
                this@HttpClient.expectSuccess = false
                requestTimeoutMillis = 45 * 1000
                connectTimeoutMillis = 15 * 1000
                socketTimeoutMillis = 30 * 1000
            }
            
            defaultRequest {
                header(HttpHeaders.UserAgent, "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36")
                header(HttpHeaders.Accept, "*/*")
                header(HttpHeaders.AcceptLanguage, "ru-RU,ru;q=0.9,en-US;q=0.8,en;q=0.7")
            }
            
            engine {
                endpoint.apply {
                    pipelineMaxSize = 1
                }
                https {
                    trustManager = object : X509TrustManager {
                        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                    }
                }
            }
        }
    }

    single<PlatformFileStorageCleaner> { NoOpPlatformFileStorageCleaner() }
    single<FeedbackSharer> { NoOpFeedbackSharer() }
    single<CacheStorageManager> {
        CacheStorageManagerImpl(
            scheduleRepository = get(),
            appValues = get(),
            platformCleaner = get(),
            storage = getOrNull<KeyValueStorage>()
        )
    }
}

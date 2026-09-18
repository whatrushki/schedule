package app.what.data.di

import app.what.data.libs.GoogleDriveParser
import app.what.data.repositories.NewsRepositoryImpl
import app.what.domain.repositories.NewsRepository
import app.what.domain.repositories.ScheduleRepository
import app.what.foundation.data.settings.KeyValueStorage
import app.what.foundation.data.settings.PreferenceStorage
import app.what.foundation.data.settings.WasmKeyValueStorage
import app.what.foundation.services.AppLogger.Companion.Auditor
import app.what.foundation.utils.AppUtils
import app.what.foundation.utils.LogCat
import app.what.foundation.utils.LogScope
import app.what.foundation.utils.buildTag
import app.what.schedule.core.cache.FileCache
import app.what.schedule.core.cache.InMemoryFileCache
import app.what.schedule.data.local.settings.AppValues
import app.what.schedule.data.remote.api.InstitutionManager
import app.what.schedule.dgtu.DGTUAccountClient
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

import app.what.schedule.data.local.database.AppDatabaseSource
import app.what.schedule.data.local.database.InMemoryAppDatabaseSource
import app.what.data.repositories.ScheduleRepositoryImpl

import app.what.data.adapters.AdaptedScheduleService
import app.what.data.remote.WebScheduleClient
import app.what.domain.models.MetaInfo
import app.what.foundation.services.auto_update.AppUpdateManager
import app.what.foundation.services.auto_update.UpdateConfig
import app.what.foundation.services.auto_update.GitHubUpdateService
import app.what.foundation.services.auto_update.WebUpdateManager
import app.what.schedule.data.remote.api.Institution
import app.what.schedule.data.remote.api.NewsService
import app.what.schedule.data.remote.api.ScheduleService
import app.what.schedule.data.remote.api.insts

class WebInstitutionFactory(
    private val base: Institution.Factory,
    private val httpClient: HttpClient
) : Institution.Factory {
    override val metadata: MetaInfo get() = base.metadata

    override fun create(): Institution {
        val baseInst = base.create()
        val webClient = WebScheduleClient(
            institutionId = metadata.id,
            httpClient = httpClient
        )
        return object : Institution by baseInst {
            override val scheduleService: ScheduleService = AdaptedScheduleService(webClient)
            override val newsService: NewsService = object : NewsService {}
        }
    }
}

val dataModule = module {
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    single<KeyValueStorage> { WasmKeyValueStorage() }
    single { AppValues(get<KeyValueStorage>()) } bind PreferenceStorage::class

    singleOf(::GoogleDriveParser)
    single<FileCache> { InMemoryFileCache() }
    single { DGTUAccountClient(get()) }
    
    single {
        val httpClient: HttpClient = get()
        val webFactories = insts.map { WebInstitutionFactory(it, httpClient) }
        InstitutionManager(get(), get(), webFactories)
    }
    
    singleOf(::AppUtils)

    single<AppUpdateManager> {
        WebUpdateManager(
            gitHubService = GitHubUpdateService(get()),
            config = UpdateConfig(
                githubOwner = "whatrushki",
                githubRepo = "schedule",
                currentVersion = "1.3.4"
            ),
            onReload = {
                kotlinx.browser.window.location.reload()
            },
            scope = get()
        )
    }

    single<AppDatabaseSource> { InMemoryAppDatabaseSource() }

    single<ScheduleRepository> {
        ScheduleRepositoryImpl(get(), get(), get())
    }

    single<NewsRepository> {
        NewsRepositoryImpl(get())
    }

    single {
        HttpClient {
            install(Logging) {
                logger = object : Logger {
                    override fun log(message: String) {
                        Auditor.debug(buildTag(LogScope.NETWORK, LogCat.NET), message)
                    }
                }
            }

            install(app.what.foundation.network.monitor.NetworkMonitorPlugin)

            install(ContentNegotiation) {
                json(Json {
                    classDiscriminator = "type"
                    ignoreUnknownKeys = true
                    prettyPrint = false
                    isLenient = true
                    explicitNulls = false
                })
            }

            install(HttpTimeout) {
                this@HttpClient.expectSuccess = false
                requestTimeoutMillis = 45 * 1000
                connectTimeoutMillis = 15 * 1000
                socketTimeoutMillis = 30 * 1000
            }
        }
    }
}

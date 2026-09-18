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

val dataModule = module {
    single<CoroutineScope> { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    single<KeyValueStorage> { WasmKeyValueStorage() }
    single { AppValues(get<KeyValueStorage>()) } bind PreferenceStorage::class

    singleOf(::GoogleDriveParser)
    single<FileCache> { InMemoryFileCache() }
    single { DGTUAccountClient(get()) }
    singleOf(::InstitutionManager)
    singleOf(::AppUtils)

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

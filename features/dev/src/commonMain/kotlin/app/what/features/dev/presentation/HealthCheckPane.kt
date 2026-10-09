package app.what.schedule.features.dev.presentation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import app.what.domain.models.ScheduleSearch
import app.what.domain.repositories.NewsRepository
import app.what.domain.repositories.ScheduleRepository
import app.what.foundation.healthcheck.model.HealthCategory
import app.what.foundation.healthcheck.model.HealthResult
import app.what.foundation.healthcheck.registry.EndpointAvailabilityCheck
import app.what.foundation.healthcheck.registry.HealthCheck
import app.what.foundation.healthcheck.registry.HealthRegistry
import app.what.foundation.healthcheck.registry.NetworkConnectivityCheck
import app.what.foundation.healthcheck.ui.HealthCheckScreen
import app.what.foundation.utils.ShareData
import app.what.foundation.utils.rememberShareManager
import app.what.schedule.data.local.database.AppDatabaseSource
import app.what.schedule.data.local.settings.AppValues
import io.ktor.client.HttpClient
import org.koin.compose.koinInject

@Composable
fun HealthCheckPane(
    modifier: Modifier = Modifier
) {
    val httpClient: HttpClient = koinInject()
    val scheduleRepository: ScheduleRepository = koinInject()
    val newsRepository: NewsRepository = koinInject()
    val appValues: AppValues = koinInject()
    val databaseSource: AppDatabaseSource = koinInject()
    val shareManager = rememberShareManager()

    val registry = remember(httpClient, scheduleRepository, newsRepository, appValues, databaseSource) {
        HealthRegistry().apply {
            registerAll(
                // --- 1. СЕТЬ И ПОДКЛЮЧЕНИЕ ---
                NetworkConnectivityCheck(httpClient),

                // --- 2. ХРАНИЛИЩЕ И БАЗА ДАННЫХ ---
                object : HealthCheck {
                    override val id: String = "storage_database_integrity"
                    override val title: String = "Целостность базы данных Room (schedule.db)"
                    override val category: HealthCategory = HealthCategory.STORAGE

                    override suspend fun run(): HealthResult {
                        return try {
                            val requests = databaseSource.requestsDao.selectAll()
                            HealthResult.Passed(message = "Таблицы БД доступны, записей запросов: ${requests.size}")
                        } catch (e: Exception) {
                            HealthResult.Failed(message = "Сбой доступа к БД: ${e.message}", error = e)
                        }
                    }
                },

                object : HealthCheck {
                    override val id: String = "storage_preferences_encryption"
                    override val title: String = "Зашифрованные настройки (AppValues / Keystore)"
                    override val category: HealthCategory = HealthCategory.STORAGE

                    override suspend fun run(): HealthResult {
                        return try {
                            val testKey = "healthcheck_test_${app.what.foundation.utils.currentTimeMillis()}"
                            appValues.userId.set(appValues.userId.get() ?: "dev_user")
                            val read = appValues.userId.get()
                            if (!read.isNullOrEmpty()) {
                                HealthResult.Passed(message = "Чтение/запись шифрованных префов работает корректно")
                            } else {
                                HealthResult.Warning(message = "Префы доступны, но userId пустой")
                            }
                        } catch (e: Exception) {
                            HealthResult.Failed(message = "Сбой доступа к хранилищу префов: ${e.message}", error = e)
                        }
                    }
                },

                // --- 3. СЕРВЕРЫ ПРОВАЙДЕРОВ ---
                EndpointAvailabilityCheck(
                    id = "check_rksi",
                    title = "Сервер РКСИ (rksi.ru)",
                    endpointUrl = "https://rksi.ru",
                    httpClient = httpClient
                ),
                EndpointAvailabilityCheck(
                    id = "check_dgtu",
                    title = "API ДГТУ (edu.donstu.ru)",
                    endpointUrl = "https://edu.donstu.ru/api/Rasp/ListYears",
                    httpClient = httpClient
                ),
                EndpointAvailabilityCheck(
                    id = "check_sfedu",
                    title = "API ЮФУ (schedule.sfedu.ru)",
                    endpointUrl = "https://schedule.sfedu.ru/APIv1/week",
                    httpClient = httpClient
                ),
                EndpointAvailabilityCheck(
                    id = "check_rgups",
                    title = "Сайт РГУПС (rgups.ru)",
                    endpointUrl = "https://www.rgups.ru",
                    httpClient = httpClient
                ),
                EndpointAvailabilityCheck(
                    id = "check_rinh",
                    title = "API РИНХ (rasp-api.rsue.ru)",
                    endpointUrl = "https://rasp-api.rsue.ru/api/v1/schedule/search/?format=json",
                    httpClient = httpClient
                ),
                EndpointAvailabilityCheck(
                    id = "check_tvgu",
                    title = "API ТвГУ (timetable.tversu.ru)",
                    endpointUrl = "https://timetable.tversu.ru/api/v3/groups",
                    httpClient = httpClient
                ),

                // --- 4. РАБОТОСПОСОБНОСТЬ ПАРСЕРОВ И ДАННЫХ ---
                object : HealthCheck {
                    override val id: String = "parser_groups_load"
                    override val title: String = "Парсинг списка групп (текущий вуз)"
                    override val category: HealthCategory = HealthCategory.PROVIDER

                    override suspend fun run(): HealthResult {
                        return try {
                            val start = app.what.foundation.utils.currentTimeMillis()
                            val currentInst = appValues.institution.get()
                            val groups = scheduleRepository.getGroups(currentInst, forceReload = false)
                            val duration = app.what.foundation.utils.currentTimeMillis() - start
                            if (groups.isNotEmpty()) {
                                HealthResult.Passed(
                                    message = "Успешно получено ${groups.size} групп (${duration}ms)",
                                    durationMs = duration
                                )
                            } else {
                                HealthResult.Warning(message = "Список групп пуст для текущего вуза ($currentInst)")
                            }
                        } catch (e: Exception) {
                            val message = e.message.orEmpty()
                            val isNetworkError = e is io.ktor.utils.io.errors.IOException ||
                                    message.contains("UnknownHost", ignoreCase = true) ||
                                    message.contains("SocketTimeout", ignoreCase = true) ||
                                    message.contains("Unable to resolve host", ignoreCase = true) ||
                                    message.contains("ConnectException", ignoreCase = true) ||
                                    message.contains("timeout", ignoreCase = true)

                            if (isNetworkError) {
                                HealthResult.Failed(message = "Нет подключения к сети (проверьте интернет)", error = e)
                            } else {
                                HealthResult.Failed(message = "Ошибка парсинга групп: $message", error = e)
                            }
                        }
                    }
                },

                object : HealthCheck {
                    override val id: String = "parser_news_feed"
                    override val title: String = "Парсинг ленты новостей вуза"
                    override val category: HealthCategory = HealthCategory.PROVIDER

                    override suspend fun run(): HealthResult {
                        return try {
                            val start = app.what.foundation.utils.currentTimeMillis()
                            val news = newsRepository.getNews(1)
                            val duration = app.what.foundation.utils.currentTimeMillis() - start
                            if (news.isNotEmpty()) {
                                HealthResult.Passed(
                                    message = "Успешно получено ${news.size} новостей (${duration}ms)",
                                    durationMs = duration
                                )
                            } else {
                                HealthResult.Passed(message = "Лента новостей не поддерживается для текущего вуза")
                            }
                        } catch (e: Exception) {
                            val message = e.message.orEmpty()
                            val isNetworkError = e is io.ktor.utils.io.errors.IOException ||
                                    message.contains("UnknownHost", ignoreCase = true) ||
                                    message.contains("SocketTimeout", ignoreCase = true) ||
                                    message.contains("Unable to resolve host", ignoreCase = true) ||
                                    message.contains("ConnectException", ignoreCase = true) ||
                                    message.contains("timeout", ignoreCase = true)

                            if (isNetworkError) {
                                HealthResult.Failed(message = "Нет подключения к сети (проверьте интернет)", error = e)
                            } else {
                                HealthResult.Failed(message = "Ошибка парсинга новостей: $message", error = e)
                            }
                        }
                    }
                },

                // --- 5. ОБЛАЧНАЯ СИНХРОНИЗАЦИЯ (GH-PAGES) ---
                object : HealthCheck {
                    override val id: String = "cloud_schedule_sync_status"
                    override val title: String = "Синхронизация расписания (GitHub Pages)"
                    override val category: HealthCategory = HealthCategory.PROVIDER

                    override suspend fun run(): HealthResult {
                        return try {
                            val currentInst = appValues.institution.get() ?: "rksi"
                            val cloudClient = app.what.data.remote.CloudScheduleClient(currentInst, httpClient)
                            val meta = cloudClient.getMeta()
                            if (meta == null) {
                                HealthResult.Warning(message = "Метаданные синхронизации для $currentInst не найдены в gh-pages")
                            } else if (meta.status.equals("FAILED", ignoreCase = true)) {
                                HealthResult.Failed(
                                    message = "Синхронизация для $currentInst завершилась сбоем: ${meta.errorMessage ?: "Неизвестная ошибка"} (последняя попытка: ${meta.lastSync})",
                                    error = Exception(meta.errorMessage)
                                )
                            } else if (!cloudClient.isSyncHealthy()) {
                                HealthResult.Warning(
                                    message = "Данные синхронизации устарели (последний синк: ${meta.lastSync}, групп: ${meta.groupCount}). Приложение переключилось на ручной парсер."
                                )
                            } else {
                                HealthResult.Passed(
                                    message = "Синхронизация активна (${meta.lastSync}), групп: ${meta.groupCount}, преподавателей: ${meta.teacherCount}"
                                )
                            }
                        } catch (e: Exception) {
                            HealthResult.Failed(message = "Сбой проверки облачной синхронизации: ${e.message}", error = e)
                        }
                    }
                },

                // --- 6. ЭКОСИСТЕМА И СЕРВИСЫ ДОСТАВКИ ---
                EndpointAvailabilityCheck(
                    id = "check_delivery",
                    title = "Центр уведомлений (GitHub Raw delivery)",
                    endpointUrl = "https://raw.githubusercontent.com/whatrushki/delivery/main/notifications/active.json",
                    httpClient = httpClient
                )
            )
        }
    }

    HealthCheckScreen(
        registry = registry,
        onExportJson = { jsonReport ->
            shareManager.share(
                ShareData.Text(
                    text = jsonReport,
                    title = "Отчёт тестирования компонентов"
                )
            )
        },
        modifier = modifier.fillMaxSize()
    )
}

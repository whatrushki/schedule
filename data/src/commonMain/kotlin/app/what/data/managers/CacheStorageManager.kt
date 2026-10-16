package app.what.data.managers

import androidx.compose.ui.graphics.Color
import app.what.domain.repositories.ScheduleRepository
import app.what.foundation.data.settings.KeyValueStorage
import app.what.schedule.data.local.settings.AppValues
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class CacheCategoryType(val defaultTitle: String, val defaultDescription: String) {
    IMAGES("Изображения", "Кэш Coil и обложки новостей"),
    SCHEDULE("Расписание", "Локальная база данных Room"),
    NEWS("Новости", "Сохраненные списки статей"),
    REPLACEMENTS("Замены и планшетки", "Сигнатуры и временные данные"),
    LOGS("Логи и отладка", "Журнал событий приложения")
}

data class CacheCategory(
    val type: CacheCategoryType,
    val title: String,
    val description: String,
    val color: Color,
    val sizeBytes: Long
)

interface CacheStorageManager {
    suspend fun getCategories(): List<CacheCategory>
    suspend fun clearCategories(types: Set<CacheCategoryType>): List<CacheCategory>
}

class CacheStorageManagerImpl(
    private val scheduleRepository: ScheduleRepository,
    private val appValues: AppValues,
    private val platformCleaner: PlatformFileStorageCleaner,
    private val storage: KeyValueStorage? = null
) : CacheStorageManager {

    override suspend fun getCategories(): List<CacheCategory> = withContext(Dispatchers.Default) {
        val imagesSize = platformCleaner.getImageCacheSizeBytes()
        val logsSize = platformCleaner.getLogsCacheSizeBytes()

        // Расчет размера базы расписания
        val scheduleSize = try {
            val dbSize = platformCleaner.getDatabaseSizeBytes()
            if (dbSize > 0) dbSize else 0L
        } catch (_: Exception) {
            0L
        }

        // Расчет размера кэша новостей
        val newsSize = try {
            val newsKeys = listOf("cached_news_rksi_1", "cached_news_dgtu_1", "cached_news_sfedu_1", "cached_news_iubip_1")
            var size = 0L
            newsKeys.forEach { key ->
                val str = storage?.getString(key, "") ?: ""
                size += str.encodeToByteArray().size
            }
            size
        } catch (_: Exception) {
            0L
        }

        // Сигнатуры замен
        val replacementsRaw = appValues.lastNotifiedReplacementsHash.get() ?: ""
        val replacementsSize = replacementsRaw.encodeToByteArray().size.toLong()

        listOf(
            CacheCategory(
                type = CacheCategoryType.IMAGES,
                title = "Изображения",
                description = "Кэш Coil и обложки новостей",
                color = Color(0xFFD084D0), // Светло-фиолетовый
                sizeBytes = imagesSize
            ),
            CacheCategory(
                type = CacheCategoryType.SCHEDULE,
                title = "Расписание",
                description = "Локальная база данных Room",
                color = Color(0xFF8E4E98), // Насыщенный пурпурный
                sizeBytes = scheduleSize
            ),
            CacheCategory(
                type = CacheCategoryType.NEWS,
                title = "Новости",
                description = "Сохраненные списки статей",
                color = Color(0xFFBA68C8), // Лиловый
                sizeBytes = newsSize
            ),
            CacheCategory(
                type = CacheCategoryType.REPLACEMENTS,
                title = "Замены и планшетки",
                description = "Сигнатуры и временные данные",
                color = Color(0xFFCE93D8), // Нежно-лиловый
                sizeBytes = replacementsSize
            ),
            CacheCategory(
                type = CacheCategoryType.LOGS,
                title = "Логи и отладка",
                description = "Журнал событий приложения",
                color = Color(0xFFE1BEE7), // Пастельно-лавандовый
                sizeBytes = logsSize
            )
        )
    }

    override suspend fun clearCategories(types: Set<CacheCategoryType>): List<CacheCategory> {
        if (types.contains(CacheCategoryType.IMAGES)) {
            platformCleaner.clearImageCache()
        }
        if (types.contains(CacheCategoryType.SCHEDULE)) {
            scheduleRepository.clearScheduleCache()
        }
        if (types.contains(CacheCategoryType.NEWS)) {
            val newsKeys = listOf("cached_news_rksi_1", "cached_news_dgtu_1", "cached_news_sfedu_1", "cached_news_iubip_1")
            newsKeys.forEach { key -> storage?.putString(key, "") }
        }
        if (types.contains(CacheCategoryType.REPLACEMENTS)) {
            appValues.lastNotifiedReplacementsHash.set("")
        }
        if (types.contains(CacheCategoryType.LOGS)) {
            platformCleaner.clearLogsCache()
        }
        return getCategories()
    }
}

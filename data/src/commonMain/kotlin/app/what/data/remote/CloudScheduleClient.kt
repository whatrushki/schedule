package app.what.data.remote

import app.what.foundation.services.AppLogger.Companion.Auditor
import app.what.foundation.utils.LogCat
import app.what.foundation.utils.LogScope
import app.what.foundation.utils.buildTag
import app.what.schedule.core.clients.ScheduleClient
import app.what.schedule.core.models.DayScheduleDto
import app.what.schedule.core.models.GroupDto
import app.what.schedule.core.models.TeacherDto
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import kotlinx.datetime.toInstant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class CloudInstitutionMeta(
    val lastSync: String? = null,
    val institution: String? = null,
    val status: String = "SUCCESS",
    val errorMessage: String? = null,
    val groupCount: Int = 0,
    val teacherCount: Int = 0
) {
    /**
     * Проверяет валидность и актуальность синхронизации.
     * Если статус FAILED или данных нет — синхронизация считается нездоровой.
     */
    fun isHealthy(maxAgeHours: Long = 24): Boolean {
        if (status.equals("FAILED", ignoreCase = true)) return false
        if (groupCount == 0) return false
        if (lastSync.isNullOrBlank()) return false

        return try {
            val now = kotlinx.datetime.Clock.System.now()
            val syncInstant = try {
                kotlinx.datetime.Instant.parse(lastSync)
            } catch (_: Exception) {
                val ldt = kotlinx.datetime.LocalDateTime.parse(lastSync)
                val tz = kotlinx.datetime.TimeZone.currentSystemDefault()
                with(kotlinx.datetime.TimeZone) {
                    ldt.toInstant(tz)
                }
            }
            val diff = now - syncInstant
            diff.inWholeHours <= maxAgeHours
        } catch (_: Exception) {
            true
        }
    }
}

class CloudScheduleClient(
    val institutionId: String,
    private val httpClient: HttpClient,
    private val customBaseUrls: List<String> = emptyList()
) : ScheduleClient {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val tag = buildTag(LogScope.NETWORK, LogCat.NET)

    private var cachedGroups: List<GroupDto>? = null
    private var cachedTeachers: List<TeacherDto>? = null
    private var cachedMeta: CloudInstitutionMeta? = null
    private var groupsNotFound = false
    private var teachersNotFound = false

    fun clearCache() {
        cachedMeta = null
        cachedGroups = null
        cachedTeachers = null
        groupsNotFound = false
        teachersNotFound = false
    }

    private fun getBaseUrls(): List<String> = buildList {
        addAll(customBaseUrls)
        // Первостепенный источник — актуальные файлы напрямую из GitHub
        add("https://raw.githubusercontent.com/whatrushki/schedule/gh-pages/schedule/$institutionId")
        // Резервный источник — CDN jsDelivr на случай проблем с доступом к raw.githubusercontent.com
        add("https://cdn.jsdelivr.net/gh/whatrushki/schedule@gh-pages/schedule/$institutionId")
    }

    private sealed class FetchResult {
        data class Success(val text: String) : FetchResult()
        object NotFound : FetchResult()
        data class Error(val cause: Exception?) : FetchResult()
    }

    private suspend fun fetchWithStatus(relativePath: String): FetchResult {
        val urls = getBaseUrls().map { "$it/$relativePath" }
        var is404 = false
        var lastException: Exception? = null

        for (url in urls) {
            try {
                Auditor.debug(tag, "[CloudScheduleClient] Fetching: $url")
                val response = httpClient.get(url)
                if (response.status.isSuccess()) {
                    val text = response.bodyAsText()
                    if (text.isNotBlank()) {
                        return FetchResult.Success(text)
                    }
                } else {
                    Auditor.warn(tag, "[CloudScheduleClient] HTTP ${response.status.value} for: $url")
                    if (response.status.value == 404) {
                        is404 = true
                        if (url.contains("raw.githubusercontent.com")) {
                            // Файла нет в ветке gh-pages. Зеркало jsDelivr опрашивать бессмысленно —
                            // оно вернет тот же 404, но может зависать на 10-12 секунд.
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                Auditor.warn(tag, "[CloudScheduleClient] Failed to fetch $url: ${e.message}")
                lastException = e
            }
        }
        return if (is404) FetchResult.NotFound else FetchResult.Error(lastException)
    }

    private suspend fun fetchJson(relativePath: String): String? {
        return when (val res = fetchWithStatus(relativePath)) {
            is FetchResult.Success -> res.text
            else -> null
        }
    }

    suspend fun isSyncHealthy(): Boolean {
        val meta = getMeta() ?: return false
        // Для RKSI частые изменения/замены, порог актуальности 14 часов. Для других вузов 36 часов.
        val maxAge = if (institutionId.equals("rksi", ignoreCase = true)) 14L else 36L
        return meta.isHealthy(maxAge)
    }

    suspend fun getMeta(): CloudInstitutionMeta? {
        cachedMeta?.let { return it }
        val text = fetchJson("meta.json") ?: return null
        return try {
            val meta = json.decodeFromString<CloudInstitutionMeta>(text)
            cachedMeta = meta
            meta
        } catch (e: Exception) {
            Auditor.err(tag, "[CloudScheduleClient] Error parsing meta.json", e)
            null
        }
    }

    override suspend fun getGroups(): List<GroupDto> {
        cachedGroups?.let { return it }
        if (groupsNotFound) return emptyList()

        return when (val result = fetchWithStatus("groups.json")) {
            is FetchResult.Success -> {
                try {
                    val groups = json.decodeFromString<List<GroupDto>>(result.text)
                    cachedGroups = groups
                    groups
                } catch (e: Exception) {
                    Auditor.err(tag, "[CloudScheduleClient] Error parsing groups.json", e)
                    emptyList()
                }
            }
            is FetchResult.NotFound -> {
                // Сервер явно ответил 404: файла каталога групп в облаке нет
                groupsNotFound = true
                cachedGroups = emptyList()
                emptyList()
            }
            is FetchResult.Error -> {
                // Сетевая ошибка или таймаут — НЕ помечаем как NotFound, чтобы запрос можно было повторить
                emptyList()
            }
        }
    }

    override suspend fun getTeachers(): List<TeacherDto> {
        cachedTeachers?.let { return it }
        if (teachersNotFound) return emptyList()

        return when (val result = fetchWithStatus("teachers.json")) {
            is FetchResult.Success -> {
                try {
                    val teachers = json.decodeFromString<List<TeacherDto>>(result.text)
                    cachedTeachers = teachers
                    teachers
                } catch (e: Exception) {
                    Auditor.err(tag, "[CloudScheduleClient] Error parsing teachers.json", e)
                    emptyList()
                }
            }
            is FetchResult.NotFound -> {
                // Сервер явно ответил 404: файла каталога преподавателей в облаке нет
                teachersNotFound = true
                cachedTeachers = emptyList()
                emptyList()
            }
            is FetchResult.Error -> {
                // Сетевая ошибка или таймаут — НЕ помечаем как NotFound
                emptyList()
            }
        }
    }

    override suspend fun getGroupSchedule(group: String, showReplacements: Boolean): List<DayScheduleDto> {
        // Шаг 1: Сначала сразу пробуем загрузить расписание по прямому имени группы (например groups/ЮР-33.json).
        // В 99% случаев файл называется именно по имени группы, поэтому расписание отдается моментально без запроса groups.json.
        val directKey = sanitizeKey(group)
        val directText = fetchJson("groups/${directKey.encodeURLPathPart()}.json")
        if (directText != null) {
            return try {
                json.decodeFromString<List<DayScheduleDto>>(directText)
            } catch (e: Exception) {
                Auditor.err(tag, "[CloudScheduleClient] Error parsing schedule for $directKey", e)
                emptyList()
            }
        }

        // Шаг 2 (Фолбэк): Если файл по прямому имени не найден (например, был передан числовой ID вместо имени),
        // только тогда обращаемся к каталогу groups.json для поиска соответствия ID -> Имя.
        val groups = cachedGroups ?: if (!groupsNotFound) getGroups() else emptyList()
        val matchedGroup = groups.firstOrNull { it.id == group || it.name.equals(group, ignoreCase = true) }

        if (matchedGroup != null) {
            val candidateNames = buildList {
                if (!matchedGroup.name.equals(group, ignoreCase = true)) {
                    add(matchedGroup.name)
                }
                if (matchedGroup.id != matchedGroup.name && !matchedGroup.id.equals(group, ignoreCase = true)) {
                    add(matchedGroup.id)
                }
            }

            for (candidate in candidateNames) {
                val safeName = sanitizeKey(candidate)
                val text = fetchJson("groups/${safeName.encodeURLPathPart()}.json")
                if (text != null) {
                    return try {
                        json.decodeFromString<List<DayScheduleDto>>(text)
                    } catch (e: Exception) {
                        Auditor.err(tag, "[CloudScheduleClient] Error parsing schedule for $safeName", e)
                        emptyList()
                    }
                }
            }
        }

        return emptyList()
    }

    override suspend fun getTeacherSchedule(teacher: String, showReplacements: Boolean): List<DayScheduleDto> {
        // Шаг 1: Сначала сразу пробуем загрузить расписание по прямому ключу/ФИО преподавателя.
        val directKey = sanitizeKey(teacher)
        val directText = fetchJson("teachers/${directKey.encodeURLPathPart()}.json")
        if (directText != null) {
            return try {
                json.decodeFromString<List<DayScheduleDto>>(directText)
            } catch (e: Exception) {
                Auditor.err(tag, "[CloudScheduleClient] Error parsing schedule for $directKey", e)
                emptyList()
            }
        }

        // Шаг 2 (Фолбэк): Если по прямому ключу не найдено, ищем соответствие в каталоге teachers.json
        val teachers = cachedTeachers ?: if (!teachersNotFound) getTeachers() else emptyList()
        val matchedTeacher = teachers.firstOrNull { it.id == teacher || it.name.equals(teacher, ignoreCase = true) }

        if (matchedTeacher != null) {
            val candidateKeys = buildList {
                if (matchedTeacher.id.isNotEmpty() && !matchedTeacher.id.equals(teacher, ignoreCase = true)) {
                    add(matchedTeacher.id)
                }
                if (!matchedTeacher.name.equals(teacher, ignoreCase = true)) {
                    add(matchedTeacher.name)
                }
            }

            for (key in candidateKeys) {
                val safeKey = sanitizeKey(key)
                val text = fetchJson("teachers/${safeKey.encodeURLPathPart()}.json")
                if (text != null) {
                    return try {
                        json.decodeFromString<List<DayScheduleDto>>(text)
                    } catch (e: Exception) {
                        Auditor.err(tag, "[CloudScheduleClient] Error parsing schedule for $safeKey", e)
                        emptyList()
                    }
                }
            }
        }

        return emptyList()
    }

    private fun sanitizeKey(key: String): String = key
        .replace(Regex("""[\\/:*?"<>|\r\n\t]"""), "_")
        .trim()
        .trimEnd('.')
        .ifEmpty { "unknown" }
}

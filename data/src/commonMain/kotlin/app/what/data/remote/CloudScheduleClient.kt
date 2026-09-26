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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class CloudInstitutionMeta(
    val lastSync: String? = null,
    val institution: String? = null,
    val groupCount: Int = 0,
    val teacherCount: Int = 0
)

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

    private fun getBaseUrls(): List<String> = buildList {
        addAll(customBaseUrls)
        // Первостепенный источник — актуальные файлы напрямую из GitHub
        add("https://raw.githubusercontent.com/whatrushki/schedule/gh-pages/schedule/$institutionId")
        // Резервный источник — CDN jsDelivr на случай проблем с доступом к raw.githubusercontent.com
        add("https://cdn.jsdelivr.net/gh/whatrushki/schedule@gh-pages/schedule/$institutionId")
    }

    private suspend fun fetchJson(relativePath: String): String? {
        val urls = getBaseUrls().map { "$it/$relativePath" }
        for (url in urls) {
            try {
                Auditor.debug(tag, "[CloudScheduleClient] Fetching: $url")
                val response = httpClient.get(url)
                if (response.status.isSuccess()) {
                    val text = response.bodyAsText()
                    if (text.isNotBlank()) {
                        return text
                    }
                } else {
                    Auditor.warn(tag, "[CloudScheduleClient] HTTP ${response.status.value} for: $url")
                }
            } catch (e: Exception) {
                Auditor.warn(tag, "[CloudScheduleClient] Failed to fetch $url: ${e.message}")
            }
        }
        return null
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
        val text = fetchJson("groups.json") ?: return emptyList()
        return try {
            val groups = json.decodeFromString<List<GroupDto>>(text)
            cachedGroups = groups
            groups
        } catch (e: Exception) {
            Auditor.err(tag, "[CloudScheduleClient] Error parsing groups.json", e)
            emptyList()
        }
    }

    override suspend fun getTeachers(): List<TeacherDto> {
        cachedTeachers?.let { return it }
        val text = fetchJson("teachers.json") ?: return emptyList()
        return try {
            val teachers = json.decodeFromString<List<TeacherDto>>(text)
            cachedTeachers = teachers
            teachers
        } catch (e: Exception) {
            Auditor.err(tag, "[CloudScheduleClient] Error parsing teachers.json", e)
            emptyList()
        }
    }

    override suspend fun getGroupSchedule(group: String, showReplacements: Boolean): List<DayScheduleDto> {
        val groups = cachedGroups ?: getGroups()
        val matchedGroup = groups.firstOrNull { it.id == group || it.name.equals(group, ignoreCase = true) }

        val candidateNames = buildList {
            if (matchedGroup != null) {
                add(matchedGroup.name)
                if (matchedGroup.id != matchedGroup.name) {
                    add(matchedGroup.id)
                }
            }
            if (group !in this) {
                add(group)
            }
        }

        for (candidate in candidateNames) {
            val safeName = candidate.replace("/", "_").replace("\\", "_")
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

        return emptyList()
    }

    override suspend fun getTeacherSchedule(teacher: String, showReplacements: Boolean): List<DayScheduleDto> {
        val teachers = cachedTeachers ?: getTeachers()
        val matchedTeacher = teachers.firstOrNull { it.id == teacher || it.name.equals(teacher, ignoreCase = true) }

        val candidateKeys = buildList {
            if (matchedTeacher != null) {
                if (matchedTeacher.id.isNotEmpty()) add(matchedTeacher.id)
                add(matchedTeacher.name)
            }
            if (teacher !in this) {
                add(teacher)
            }
        }

        for (key in candidateKeys) {
            val safeKey = key.replace("/", "_").replace("\\", "_")
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

        return emptyList()
    }
}

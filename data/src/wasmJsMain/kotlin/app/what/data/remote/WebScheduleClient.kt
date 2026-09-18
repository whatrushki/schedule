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
import kotlinx.browser.window
import kotlinx.serialization.json.Json

class WebScheduleClient(
    val institutionId: String,
    private val httpClient: HttpClient
) : ScheduleClient {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val tag = buildTag(LogScope.NETWORK, LogCat.NET)

    private var cachedGroups: List<GroupDto>? = null
    private var cachedTeachers: List<TeacherDto>? = null

    private fun getBaseUrls(): List<String> {
        val list = mutableListOf<String>()
        try {
            val origin = window.location.origin
            val path = window.location.pathname.trimEnd('/')
            list.add("$origin$path/schedule/$institutionId")
        } catch (_: Exception) {}
        list.add("https://raw.githubusercontent.com/whatrushki/schedule/master/.github/schedule/$institutionId")
        return list
    }

    private suspend fun fetchJson(relativePath: String): String? {
        val urls = getBaseUrls().map { "$it/$relativePath" }
        for (url in urls) {
            try {
                Auditor.debug(tag, "[WebScheduleClient] Fetching: $url")
                val response = httpClient.get(url)
                if (response.status.isSuccess()) {
                    val text = response.bodyAsText()
                    if (text.isNotBlank()) {
                        return text
                    }
                } else {
                    Auditor.warn(tag, "[WebScheduleClient] HTTP ${response.status.value} for: $url")
                }
            } catch (e: Exception) {
                Auditor.warn(tag, "[WebScheduleClient] Failed to fetch $url: ${e.message}")
            }
        }
        return null
    }

    override suspend fun getGroups(): List<GroupDto> {
        cachedGroups?.let { return it }
        val text = fetchJson("groups.json") ?: return emptyList()
        return try {
            val groups = json.decodeFromString<List<GroupDto>>(text)
            cachedGroups = groups
            groups
        } catch (e: Exception) {
            Auditor.err(tag, "[WebScheduleClient] Error parsing groups.json", e)
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
            Auditor.err(tag, "[WebScheduleClient] Error parsing teachers.json", e)
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
                    Auditor.err(tag, "[WebScheduleClient] Error parsing schedule for $safeName", e)
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
                    Auditor.err(tag, "[WebScheduleClient] Error parsing schedule for $safeKey", e)
                    emptyList()
                }
            }
        }

        return emptyList()
    }
}

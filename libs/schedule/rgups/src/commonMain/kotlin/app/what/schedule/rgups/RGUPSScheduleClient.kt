package app.what.schedule.rgups

import app.what.schedule.core.cache.FileCache
import app.what.schedule.core.cache.NoOpFileCache
import app.what.schedule.core.clients.ScheduleClient
import app.what.schedule.core.models.*
import app.what.schedule.rgups.models.*
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.parameters
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json

class RGUPSScheduleClient(
    private val client: HttpClient,
    private val baseUrl: String = "https://www.rgups.ru",
    private val fileCache: FileCache = NoOpFileCache(),
    private val log: ((String) -> Unit)? = null
) : ScheduleClient {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    private var cachedGroups: List<RgupsGroup>? = null
    private val groupsMutex = Mutex()

    suspend fun getRgupsGroups(): List<RgupsGroup> {
        cachedGroups?.let { return it }
        return groupsMutex.withLock {
            cachedGroups?.let { return it }

            val cacheKey = "rgups_internal_groups.json"
            val cachedBytes = fileCache.get(cacheKey)
            if (cachedBytes != null) {
                try {
                    val parsed = json.decodeFromString<List<RgupsGroup>>(cachedBytes.decodeToString())
                    if (parsed.isNotEmpty()) {
                        cachedGroups = parsed
                        return parsed
                    }
                } catch (_: Exception) {}
            }

            try {
                log?.invoke("Fetching faculties from RGUPS...")
                val initialHtml = client.get("$baseUrl/services/time/").bodyAsText()
                val faculties = RgupsHtmlParser.parseFaculties(initialHtml)
                log?.invoke("Found ${faculties.size} faculties")

                val groupsFromAllFaculties = coroutineScope {
                    faculties.map { fac ->
                        async {
                            val facGroups = mutableListOf<RgupsGroup>()
                            try {
                                val courseResp = client.submitForm(
                                    url = "$baseUrl/services/time/",
                                    formParameters = parameters {
                                        append("action", "course")
                                        append("fac-id", fac.id)
                                        append("edu-type", "internal")
                                    }
                                ).bodyAsText()
                                val courses = RgupsHtmlParser.parseCourses(courseResp).ifEmpty { listOf(1, 2, 3, 4, 5) }

                                for (courseId in courses) {
                                    try {
                                        val groupsResp = client.submitForm(
                                            url = "$baseUrl/services/time/",
                                            formParameters = parameters {
                                                append("action", "groups")
                                                append("fac-id", fac.id)
                                                append("course-id", courseId.toString())
                                                append("edu-type", "internal")
                                            }
                                        ).bodyAsText()
                                        val groups = RgupsHtmlParser.parseGroups(groupsResp, fac.id, courseId, "internal")
                                        facGroups.addAll(groups)
                                    } catch (_: Exception) {}
                                }
                            } catch (_: Exception) {}
                            facGroups
                        }
                    }.awaitAll().flatten()
                }

                val distinctGroups = groupsFromAllFaculties.distinctBy { it.id }.sortedBy { it.name }
                if (distinctGroups.isNotEmpty()) {
                    fileCache.put(cacheKey, json.encodeToString(distinctGroups).encodeToByteArray(), ttlMillis = 12 * 60 * 60 * 1000L)
                    cachedGroups = distinctGroups
                }
                distinctGroups
            } catch (e: Exception) {
                log?.invoke("Error fetching RGUPS groups: ${e.message}")
                emptyList()
            }
        }
    }

    override suspend fun getGroups(): List<GroupDto> {
        val rgupsGroups = getRgupsGroups()
        return rgupsGroups.map { g ->
            GroupDto(
                id = g.id,
                name = g.name,
                course = g.courseId
            )
        }
    }

    override suspend fun getTeachers(): List<TeacherDto> {
        return emptyList()
    }

    override suspend fun getGroupSchedule(group: String, showReplacements: Boolean): List<DayScheduleDto> {
        val rgupsGroups = getRgupsGroups()
        val target = resolveGroup(group, rgupsGroups) ?: return emptyList()

        return try {
            val responseHtml = client.submitForm(
                url = "$baseUrl/services/time/",
                formParameters = parameters {
                    append("action", "timetable")
                    append("fac-id", target.facId)
                    append("course-id", target.courseId.toString())
                    append("group-id", target.id)
                    append("edu-type", target.eduType)
                }
            ).bodyAsText()

            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            RgupsHtmlParser.parseTimetable(responseHtml, today)
        } catch (e: Exception) {
            log?.invoke("Error fetching RGUPS schedule for group $group: ${e.message}")
            emptyList()
        }
    }

    override suspend fun getTeacherSchedule(teacher: String, showReplacements: Boolean): List<DayScheduleDto> {
        return emptyList()
    }

    private fun resolveGroup(query: String, groups: List<RgupsGroup>): RgupsGroup? {
        val trimmed = query.trim()
        groups.firstOrNull { it.id == trimmed }?.let { return it }
        return groups.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
            ?: groups.firstOrNull { it.name.startsWith(trimmed, ignoreCase = true) }
            ?: groups.firstOrNull { it.name.contains(trimmed, ignoreCase = true) }
    }
}

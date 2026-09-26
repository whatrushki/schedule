package app.what.schedule.rgups_tuapse

import app.what.schedule.core.cache.FileCache
import app.what.schedule.core.cache.NoOpFileCache
import app.what.schedule.core.clients.ScheduleClient
import app.what.schedule.core.models.*
import app.what.schedule.rgups_tuapse.models.*
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

class RGUPSTuapseScheduleClient(
    private val client: HttpClient,
    private val fileCache: FileCache = NoOpFileCache(),
    private val apiUrl: String = "https://cabinetstudent.apisrv.ru/web/t615B6E3FEE70F153B65B83C63505429B57EE6A5401FD9019C24B9F67563BF62F82E054C0851A51C09CDA86F39815A9E1E835E3F4C01BC1841248A37062DC0E83/beta/v1/timetable/?list=all&days=30",
    private val log: ((String) -> Unit)? = null
) : ScheduleClient {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    private var cachedData: TuapseTimetableResponse? = null
    private val dataMutex = Mutex()

    suspend fun getTimetableData(): TuapseTimetableResponse {
        cachedData?.let { return it }
        return dataMutex.withLock {
            cachedData?.let { return it }

            val cacheKey = "rgups_tuapse_timetable.json"
            val cachedBytes = fileCache.get(cacheKey)
            if (cachedBytes != null) {
                try {
                    val parsed = json.decodeFromString<TuapseTimetableResponse>(cachedBytes.decodeToString())
                    cachedData = parsed
                    return parsed
                } catch (_: Exception) {}
            }

            try {
                log?.invoke("Fetching RGUPSTuapse timetable from API...")
                val responseText = client.get(apiUrl).bodyAsText()
                val parsed = json.decodeFromString<TuapseTimetableResponse>(responseText)
                fileCache.put(cacheKey, responseText.encodeToByteArray(), ttlMillis = 2 * 60 * 60 * 1000L)
                cachedData = parsed
                parsed
            } catch (e: Exception) {
                log?.invoke("Error fetching RGUPSTuapse timetable: ${e.message}")
                cachedData ?: TuapseTimetableResponse()
            }
        }
    }

    override suspend fun getGroups(): List<GroupDto> {
        val data = getTimetableData()
        return data.groups.map { g ->
            GroupDto(
                id = g.id.toString(),
                name = g.n.trim(),
                course = g.c
            )
        }.sortedBy { it.name }
    }

    override suspend fun getTeachers(): List<TeacherDto> {
        val data = getTimetableData()
        return data.teachers.map { (id, name) ->
            TeacherDto(
                id = id.trim(),
                name = name.trim()
            )
        }.filter { it.name.isNotBlank() }.sortedBy { it.name }
    }

    override suspend fun getGroupSchedule(group: String, showReplacements: Boolean): List<DayScheduleDto> {
        val data = getTimetableData()
        val targetGroupId = resolveGroupId(group, data.groups) ?: return emptyList()
        return buildSchedule(data, isGroup = true, targetId = targetGroupId)
    }

    override suspend fun getTeacherSchedule(teacher: String, showReplacements: Boolean): List<DayScheduleDto> {
        val data = getTimetableData()
        val targetTeacherId = resolveTeacherId(teacher, data.teachers) ?: return emptyList()
        return buildSchedule(data, isGroup = false, targetId = targetTeacherId)
    }

    private fun resolveGroupId(groupQuery: String, groups: List<TuapseGroup>): Int? {
        val trimmed = groupQuery.trim()
        trimmed.toIntOrNull()?.let { id ->
            if (groups.any { it.id == id }) return id
        }
        return groups.firstOrNull { it.n.equals(trimmed, ignoreCase = true) }?.id
            ?: groups.firstOrNull { it.n.startsWith(trimmed, ignoreCase = true) }?.id
            ?: groups.firstOrNull { it.n.contains(trimmed, ignoreCase = true) }?.id
    }

    private fun resolveTeacherId(teacherQuery: String, teachers: Map<String, String>): String? {
        val trimmed = teacherQuery.trim()
        if (teachers.containsKey(trimmed)) return trimmed
        return teachers.entries.firstOrNull { it.value.equals(trimmed, ignoreCase = true) }?.key
            ?: teachers.entries.firstOrNull { it.value.contains(trimmed, ignoreCase = true) }?.key
    }

    private fun buildSchedule(
        data: TuapseTimetableResponse,
        isGroup: Boolean,
        targetId: Any
    ): List<DayScheduleDto> {
        val targetIdInt = targetId.toString().toIntOrNull() ?: return emptyList()
        val lessonsByDate = mutableMapOf<LocalDate, MutableList<LessonDto>>()

        // row structure: [date, parnum, timeIdx, subjIdx, typeIdx, teacherId, groupId, audIdx]
        for (row in data.rows) {
            if (row.size < 7) continue
            val dateInt = row.getOrNull(0)?.jsonPrimitive?.intOrNull ?: continue
            val parnum = row.getOrNull(1)?.jsonPrimitive?.intOrNull ?: 0
            val timeIdx = row.getOrNull(2)?.jsonPrimitive?.intOrNull ?: -1
            val subjIdx = row.getOrNull(3)?.jsonPrimitive?.intOrNull ?: -1
            val typeIdx = row.getOrNull(4)?.jsonPrimitive?.intOrNull ?: -1
            val teacherId = row.getOrNull(5)?.jsonPrimitive?.intOrNull ?: -1
            val groupId = row.getOrNull(6)?.jsonPrimitive?.intOrNull ?: -1
            val audIdx = row.getOrNull(7)?.jsonPrimitive?.intOrNull ?: -1

            if (isGroup && groupId != targetIdInt) continue
            if (!isGroup && teacherId != targetIdInt) continue

            val year = dateInt / 10000
            val month = (dateInt % 10000) / 100
            val day = dateInt % 100
            val date = try { LocalDate(year, month, day) } catch (_: Exception) { continue }

            val timeStr = data.times.getOrNull(timeIdx).orEmpty()
            val (startTime, endTime) = parseTimeRange(timeStr)

            val subject = data.subjects.getOrNull(subjIdx).orEmpty()
            val typeStr = data.types.getOrNull(typeIdx).orEmpty()
            val type = LessonTypeDto.fromString(typeStr)

            val teacherName = data.teachers[teacherId.toString()].orEmpty()
            val groupName = data.groups.firstOrNull { it.id == groupId }?.n.orEmpty()
            val room = if (audIdx >= 0) data.audiences.getOrNull(audIdx).orEmpty() else ""

            val otUnit = OneTimeUnitDto(
                teacher = teacherName,
                group = groupName,
                room = room
            )

            val lesson = LessonDto(
                date = date,
                number = parnum,
                startTime = startTime,
                endTime = endTime,
                subject = subject,
                otUnits = listOf(otUnit),
                type = type,
                state = LessonStateDto.COMMON
            )

            lessonsByDate.getOrPut(date) { mutableListOf() }.add(lesson)
        }

        return lessonsByDate.map { (date, lessons) ->
            val mergedLessons = lessons
                .groupBy { Triple(it.number, it.startTime, it.subject) }
                .map { (_, groupLessons) ->
                    groupLessons.first().copy(
                        otUnits = groupLessons.flatMap { it.otUnits }.distinct()
                    )
                }
                .sortedWith(compareBy({ it.startTime }, { it.number }))

            DayScheduleDto(
                date = date,
                scheduleType = LessonsScheduleTypeDto.COMMON,
                lessons = mergedLessons
            )
        }.sortedBy { it.date }
    }

    private fun parseTimeRange(range: String): Pair<LocalTime, LocalTime> {
        val parts = range.split("-", "—", "–").map { it.trim() }
        val start = parts.getOrNull(0)?.let { parseTime(it) } ?: LocalTime(8, 0)
        val end = parts.getOrNull(1)?.let { parseTime(it) } ?: LocalTime(9, 30)
        return start to end
    }

    private fun parseTime(str: String): LocalTime {
        return try {
            val parts = str.split(":", ".").map { it.trim().toInt() }
            LocalTime(parts[0], parts[1])
        } catch (_: Exception) {
            LocalTime(8, 0)
        }
    }
}

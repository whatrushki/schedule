package app.what.schedule.tvgu

import app.what.schedule.core.clients.ScheduleClient
import app.what.schedule.core.models.DayScheduleDto
import app.what.schedule.core.models.GroupDto
import app.what.schedule.core.models.LessonDto
import app.what.schedule.core.models.LessonStateDto
import app.what.schedule.core.models.LessonTypeDto
import app.what.schedule.core.models.LessonsScheduleTypeDto
import app.what.schedule.core.models.OneTimeUnitDto
import app.what.schedule.core.models.TeacherDto
import app.what.schedule.tvgu.models.TvGuGroupItem
import app.what.schedule.tvgu.models.TvGuGroupsResponse
import app.what.schedule.tvgu.models.TvGuLessonContainer
import app.what.schedule.tvgu.models.TvGuLessonTime
import app.what.schedule.tvgu.models.TvGuTimetableResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json

class TvGUScheduleClient(
    private val client: HttpClient,
    private val baseUrl: String = "https://timetable.tversu.ru",
    private val log: ((String) -> Unit)? = null
) : ScheduleClient {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private var cachedGroups: List<GroupDto>? = null
    private val cachedTeachers = mutableSetOf<String>()
    private val groupScheduleCache = mutableMapOf<String, List<DayScheduleDto>>()

    private val defaultBells = listOf(
        TvGuLessonTime("08:30", "10:05"),
        TvGuLessonTime("10:15", "11:50"),
        TvGuLessonTime("12:10", "13:45"),
        TvGuLessonTime("14:00", "15:35"),
        TvGuLessonTime("15:55", "17:30"),
        TvGuLessonTime("17:45", "19:20"),
        TvGuLessonTime("19:30", "21:00")
    )

    override suspend fun getGroups(): List<GroupDto> {
        cachedGroups?.let { return it }
        return try {
            val response = client.get("$baseUrl/api/v3/groups") {
                header(HttpHeaders.UserAgent, USER_AGENT)
                header(HttpHeaders.Accept, "application/json")
            }
            val text = response.bodyAsText()
            val parsed = json.decodeFromString<TvGuGroupsResponse>(text)
            val list = parsed.groups
                .map { it.groupName.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .sorted()
                .map { GroupDto(name = it, id = it) }
            cachedGroups = list
            list
        } catch (e: Exception) {
            log?.invoke("TvGU getGroups error: ${e.message}")
            emptyList()
        }
    }

    override suspend fun getTeachers(): List<TeacherDto> {
        return cachedTeachers.sorted().map { TeacherDto(name = it, id = it) }
    }

    override suspend fun getGroupSchedule(
        group: String,
        showReplacements: Boolean
    ): List<DayScheduleDto> {
        val cleanGroup = group.trim()
        val allGroups = cachedGroups ?: getGroups()
        val canonicalGroup = allGroups.firstOrNull { it.name.equals(cleanGroup, ignoreCase = true) }?.name
            ?: cleanGroup.uppercase()

        val url = "$baseUrl/api/v3/timetable?group_name=${encodeParam(canonicalGroup)}&type=0"
        return try {
            val response = client.get(url) {
                header(HttpHeaders.UserAgent, USER_AGENT)
                header(HttpHeaders.Accept, "application/json")
            }
            if (response.status.value !in 200..299) {
                log?.invoke("TvGU getGroupSchedule HTTP ${response.status.value} for $canonicalGroup")
                return emptyList()
            }
            val text = response.bodyAsText()
            if (text.contains("Расписание не найдено") || text.contains("\"status\":404")) {
                return emptyList()
            }
            val timetable = json.decodeFromString<TvGuTimetableResponse>(text)
            val result = parseTimetable(canonicalGroup, timetable)
            groupScheduleCache[canonicalGroup] = result
            groupScheduleCache[cleanGroup] = result
            result
        } catch (e: Exception) {
            log?.invoke("TvGU getGroupSchedule error for $canonicalGroup: ${e.message}")
            emptyList()
        }
    }

    override suspend fun getTeacherSchedule(
        teacher: String,
        showReplacements: Boolean
    ): List<DayScheduleDto> {
        val cleanTeacher = teacher.trim().lowercase()
        val allDays = mutableMapOf<LocalDate, MutableList<LessonDto>>()

        for ((_, schedules) in groupScheduleCache) {
            for (day in schedules) {
                val teacherLessons = day.lessons.filter { lesson ->
                    lesson.otUnits.any { it.teacher.trim().lowercase().contains(cleanTeacher) }
                }
                if (teacherLessons.isNotEmpty()) {
                    allDays.getOrPut(day.date) { mutableListOf() }.addAll(teacherLessons)
                }
            }
        }

        return allDays.map { (date, lessons) ->
            val mergedLessons = lessons
                .groupBy { it.number to it.startTime }
                .map { (_, groupLessons) ->
                    val first = groupLessons.first()
                    val state = when {
                        groupLessons.any { it.state == LessonStateDto.CHANGED } -> LessonStateDto.CHANGED
                        groupLessons.all { it.state == LessonStateDto.REMOVED } -> LessonStateDto.REMOVED
                        else -> first.state
                    }
                    val combinedUnits = groupLessons.flatMap { l ->
                        l.otUnits.map { u ->
                            if (u.subject.isNullOrBlank()) u.copy(subject = l.subject) else u
                        }
                    }.distinctBy { it.group to it.room to it.teacher to it.subject }

                    first.copy(
                        state = state,
                        otUnits = combinedUnits
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

    private fun parseTimetable(
        group: String,
        timetable: TvGuTimetableResponse
    ): List<DayScheduleDto> {
        val bells = if (timetable.lessonTimeData.isNotEmpty()) timetable.lessonTimeData else defaultBells
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

        // Baseline date for parity calculation (e.g. 01.09.2026)
        val baselineDate = parseDate(timetable.start) ?: run {
            if (now.monthNumber >= 9) LocalDate(now.year, 9, 1)
            else LocalDate(now.year, 2, 1)
        }
        val baselineMonday = baselineDate.minus(DatePeriod(days = baselineDate.dayOfWeek.isoDayNumber - 1))

        // Start from Monday of current week
        val startMonday = now.minus(DatePeriod(days = now.dayOfWeek.isoDayNumber - 1))
        val daysToProject = 28 // 4 weeks

        val resultDays = mutableListOf<DayScheduleDto>()

        for (offset in 0 until daysToProject) {
            val targetDate = startMonday.plus(DatePeriod(days = offset))
            val targetIsoDay = targetDate.dayOfWeek.isoDayNumber
            if (targetIsoDay > 6) continue // Sunday no classes

            val targetMonday = targetDate.minus(DatePeriod(days = targetIsoDay - 1))
            val weeksDiff = (targetMonday.toEpochDays() - baselineMonday.toEpochDays()) / 7
            val isMinus = (weeksDiff % 2L == 0L || weeksDiff % 2L == -0L)
            val currentWeekMark = if (isMinus) "minus" else "plus"

            val matchingContainers = timetable.lessonsContainers.filter { c ->
                val matchesDay = c.weekDay == targetIsoDay
                val matchesParity = c.weekMark.equals("every", ignoreCase = true) ||
                        c.weekMark.equals(currentWeekMark, ignoreCase = true)
                matchesDay && matchesParity
            }.distinctBy { c ->
                c.lessonNumber to c.texts.joinToString("||")
            }

            if (matchingContainers.isEmpty()) continue

            val containersBySlot = matchingContainers.groupBy { it.lessonNumber }
            val lessons = containersBySlot.mapNotNull { (lessonNumber, slotContainers) ->
                buildLessonForSlot(group, lessonNumber, slotContainers, bells, targetDate)
            }.sortedWith(compareBy({ it.startTime }, { it.number }))

            if (lessons.isNotEmpty()) {
                resultDays.add(
                    DayScheduleDto(
                        date = targetDate,
                        scheduleType = LessonsScheduleTypeDto.COMMON,
                        lessons = lessons
                    )
                )
            }
        }

        return resultDays.sortedBy { it.date }
    }

    private fun buildLessonForSlot(
        group: String,
        lessonNumber: Int,
        containers: List<TvGuLessonContainer>,
        bells: List<TvGuLessonTime>,
        date: LocalDate
    ): LessonDto? {
        val bell = bells.getOrNull(lessonNumber)
            ?: defaultBells.getOrNull(lessonNumber)
            ?: TvGuLessonTime("08:30", "10:05")

        val startTime = parseTime(bell.start) ?: LocalTime(8, 30)
        val endTime = parseTime(bell.end) ?: LocalTime(10, 5)

        val allOtUnits = mutableListOf<OneTimeUnitDto>()
        var primarySubject = ""

        for (container in containers) {
            val subjectRaw = container.texts.getOrNull(1)?.trim().orEmpty()
            if (subjectRaw.isEmpty()) continue
            if (primarySubject.isEmpty()) primarySubject = subjectRaw

            val teachersRaw = container.texts.getOrNull(2)?.trim()
            val roomsRaw = container.texts.getOrNull(3)?.trim()

            val parsedTeachers = parseTeachers(teachersRaw)
            val parsedRooms = parseRooms(roomsRaw)

            // Track teachers in client cache
            parsedTeachers.forEach { if (it.isNotEmpty() && it != "-") cachedTeachers.add(it) }

            val subjects = if ("/" in subjectRaw) {
                subjectRaw.split("/").map { it.trim().trimEnd(')') }.filter { it.isNotEmpty() }
            } else {
                listOf(subjectRaw)
            }

            val units = createOtUnits(group, parsedTeachers, parsedRooms, subjects)
            allOtUnits.addAll(units)
        }

        if (primarySubject.isEmpty() || allOtUnits.isEmpty()) return null

        return LessonDto(
            date = date,
            number = lessonNumber + 1,
            startTime = startTime,
            endTime = endTime,
            subject = primarySubject,
            type = LessonTypeDto.fromString(primarySubject),
            state = LessonStateDto.COMMON,
            otUnits = allOtUnits
        )
    }

    private data class ParsedRoom(
        val room: String,
        val building: String,
        val onlineUrl: String? = null
    )

    private fun parseRooms(raw: String?): List<ParsedRoom> {
        if (raw.isNullOrBlank()) return listOf(ParsedRoom(room = "-", building = ""))

        // Check if raw is a direct URL
        if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) {
            return listOf(ParsedRoom(room = "Дистант", building = "", onlineUrl = raw.trim()))
        }

        // Split multiple rooms: e.g. "Учебный корпус №7 - 109, Учебный корпус №7 - 212"
        val parts = raw.split(Regex(",\\s*(?=Учебный|корпус|https?://|[А-ЯA-Z0-9])"))
        return parts.map { part ->
            val p = part.trim()
            if (p.startsWith("http://", ignoreCase = true) || p.startsWith("https://", ignoreCase = true)) {
                ParsedRoom(room = "Дистант", building = "", onlineUrl = p)
            } else if ("-" in p) {
                val segs = p.split("-", limit = 2)
                val building = segs[0]
                    .replace(Regex("(?i)учебный\\s*корпус|корпус|корп\\.?|№|N"), "")
                    .trim()
                val room = segs[1].trim()
                ParsedRoom(room = room.ifEmpty { "-" }, building = building)
            } else {
                ParsedRoom(room = p, building = "")
            }
        }
    }

    private fun parseTeachers(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return listOf("-")
        // Split by comma followed by capital letter
        val parts = raw.split(Regex(",\\s*(?=[А-ЯЁA-Z])"))
        return parts.map { teacherWithTitle ->
            // Clean title: "Антонова Н.А. (Заведующий кафедрой)" -> "Антонова Н.А."
            teacherWithTitle.replace(Regex("\\s*\\([^)]*\\)"), "").trim().ifEmpty { "-" }
        }
    }

    private fun createOtUnits(
        group: String,
        teachers: List<String>,
        rooms: List<ParsedRoom>,
        subjects: List<String> = emptyList()
    ): List<OneTimeUnitDto> {
        val maxLen = maxOf(teachers.size, rooms.size, subjects.size)
        return (0 until maxLen).map { i ->
            val teacher = teachers.getOrNull(i) ?: teachers.lastOrNull() ?: "-"
            val roomObj = rooms.getOrNull(i) ?: rooms.lastOrNull() ?: ParsedRoom("-", "")
            val subject = subjects.getOrNull(i) ?: subjects.lastOrNull()
            OneTimeUnitDto(
                teacher = teacher,
                group = group,
                room = roomObj.room,
                additional = roomObj.building,
                onlineUrl = roomObj.onlineUrl,
                subject = subject
            )
        }
    }

    private fun parseTime(s: String): LocalTime? = try {
        val parts = s.trim().split(":")
        LocalTime(parts[0].toInt(), parts[1].toInt())
    } catch (_: Exception) {
        null
    }

    private fun parseDate(s: String?): LocalDate? = try {
        if (s.isNullOrBlank()) null
        else {
            val parts = s.trim().split(".")
            LocalDate(parts[2].toInt(), parts[1].toInt(), parts[0].toInt())
        }
    } catch (_: Exception) {
        null
    }

    private fun encodeParam(s: String): String {
        return buildString {
            for (b in s.encodeToByteArray()) {
                val byteVal = b.toInt() and 0xFF
                val ch = byteVal.toChar()
                if (ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '-' || ch == '_' || ch == '.' || ch == '~') {
                    append(ch)
                } else {
                    val hex = byteVal.toString(16).uppercase()
                    append('%')
                    if (hex.length == 1) append('0')
                    append(hex)
                }
            }
        }
    }

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
    }
}

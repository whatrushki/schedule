package app.what.schedule.sfedu

import app.what.schedule.core.clients.ScheduleClient
import app.what.schedule.core.models.*
import app.what.schedule.sfedu.models.*
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.datetime.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json

class SFEDUScheduleClient(
    private val client: HttpClient,
    private val baseUrl: String = "https://schedule.sfedu.ru",
    private val log: ((String) -> Unit)? = null
) : ScheduleClient {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    private var cachedGroups: List<GroupDto>? = null
    private var cachedTeachers: List<TeacherDto>? = null

    override suspend fun getGroups(): List<GroupDto> {
        cachedGroups?.let { return it }
        return try {
            val gradesText = client.get("$baseUrl/APIv1/grade/list").bodyAsText()
            val grades = json.decodeFromString<List<SfeduGrade>>(gradesText)
            val result = mutableListOf<GroupDto>()
            for (grade in grades) {
                try {
                    val groupsText = client.get("$baseUrl/APIv1/group/forGrade/${grade.id}").bodyAsText()
                    val groups = json.decodeFromString<List<SfeduGroup>>(groupsText)
                    for (g in groups) {
                        val displayName = if (g.name.contains(g.num.toString())) g.name else "${g.name} ${g.num}".trim()
                        result.add(GroupDto(id = g.id.toString(), name = displayName, course = grade.num))
                    }
                } catch (e: Exception) {
                    log?.invoke("Error fetching SFEDU groups for grade ${grade.id}: ${e.message}")
                }
            }
            val sorted = result.distinctBy { it.id }.sortedBy { it.name }
            if (sorted.isNotEmpty()) cachedGroups = sorted
            sorted
        } catch (e: Exception) {
            log?.invoke("Error fetching SFEDU grades: ${e.message}")
            emptyList()
        }
    }

    override suspend fun getTeachers(): List<TeacherDto> {
        cachedTeachers?.let { return it }
        return try {
            val teachersText = client.get("$baseUrl/APIv1/teacher/list").bodyAsText()
            val teachers = json.decodeFromString<List<SfeduTeacher>>(teachersText)
            val result = teachers
                .filter { it.name.isNotBlank() }
                .map { TeacherDto(id = it.id.toString(), name = it.name) }
                .sortedBy { it.name }
            if (result.isNotEmpty()) cachedTeachers = result
            result
        } catch (e: Exception) {
            log?.invoke("Error fetching SFEDU teachers: ${e.message}")
            emptyList()
        }
    }

    override suspend fun getGroupSchedule(group: String, showReplacements: Boolean): List<DayScheduleDto> {
        return try {
            val groupId = resolveGroupId(group) ?: return emptyList()
            val responseText = client.get("$baseUrl/APIv1/schedule/group/$groupId").bodyAsText()
            val response = json.decodeFromString<SfeduScheduleResponse>(responseText)
            val currentWeek = getWeekType()
            buildScheduleFromResponse(response, currentWeek, isTeacher = false)
        } catch (e: Exception) {
            log?.invoke("Error fetching SFEDU group schedule for $group: ${e.message}")
            emptyList()
        }
    }

    override suspend fun getTeacherSchedule(teacher: String, showReplacements: Boolean): List<DayScheduleDto> {
        return try {
            val teacherId = resolveTeacherId(teacher) ?: return emptyList()
            val teacherName = resolveTeacherName(teacher)
            val responseText = client.get("$baseUrl/APIv1/schedule/teacher/$teacherId").bodyAsText()
            val response = json.decodeFromString<SfeduScheduleResponse>(responseText)
            val currentWeek = getWeekType()
            buildScheduleFromResponse(response, currentWeek, isTeacher = true, defaultTeacherName = teacherName)
        } catch (e: Exception) {
            log?.invoke("Error fetching SFEDU teacher schedule for $teacher: ${e.message}")
            emptyList()
        }
    }

    private suspend fun getWeekType(): Int {
        return try {
            val weekText = client.get("$baseUrl/APIv1/week").bodyAsText()
            val weekInfo = json.decodeFromString<SfeduWeekInfo>(weekText)
            weekInfo.week
        } catch (_: Exception) {
            0
        }
    }

    private suspend fun resolveGroupId(group: String): String? {
        if (group.all { it.isDigit() }) return group
        val groups = getGroups()
        return groups.firstOrNull { it.name.equals(group.trim(), ignoreCase = true) }?.id
            ?: groups.firstOrNull { it.name.contains(group.trim(), ignoreCase = true) }?.id
    }

    private suspend fun resolveTeacherId(teacher: String): String? {
        if (teacher.all { it.isDigit() }) return teacher
        val teachers = getTeachers()
        return teachers.firstOrNull { it.name.equals(teacher.trim(), ignoreCase = true) }?.id
            ?: teachers.firstOrNull { it.name.contains(teacher.trim(), ignoreCase = true) }?.id
    }

    private suspend fun resolveTeacherName(teacher: String): String {
        if (teacher.all { it.isDigit() }) {
            val teachers = getTeachers()
            return teachers.firstOrNull { it.id == teacher }?.name ?: teacher
        }
        return teacher
    }

    data class ParsedTimeslot(
        val dayOfWeek: Int, // 0 = Monday, 5 = Saturday
        val startTime: LocalTime,
        val endTime: LocalTime,
        val weekType: String // "upper", "lower", "all", "full"
    )

    fun parseTimeslot(raw: String): ParsedTimeslot? {
        try {
            val clean = raw.trim().removePrefix("(").removeSuffix(")")
            val parts = clean.split(",")
            if (parts.size < 4) return null
            val dayOfWeek = parts[0].trim().toIntOrNull() ?: return null
            val startParts = parts[1].trim().split(":")
            val endParts = parts[2].trim().split(":")
            val startTime = LocalTime(startParts[0].toInt(), startParts[1].toInt())
            val endTime = LocalTime(endParts[0].toInt(), endParts[1].toInt())
            val weekType = parts[3].trim().lowercase()
            return ParsedTimeslot(dayOfWeek, startTime, endTime, weekType)
        } catch (_: Exception) {
            return null
        }
    }

    private fun buildScheduleFromResponse(
        response: SfeduScheduleResponse,
        currentWeekNum: Int,
        isTeacher: Boolean,
        defaultTeacherName: String = ""
    ): List<DayScheduleDto> {
        val curriculaByLessonId = response.curricula.groupBy { it.lessonid }
        val groupsByUberId = response.groups.associateBy { it.uberid ?: it.id }

        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val dayOfWeekIso = today.dayOfWeek.ordinal // Monday = 0, Sunday = 6
        val monday = today.minus(DatePeriod(days = dayOfWeekIso))

        val days = mutableListOf<DayScheduleDto>()

        // Generate schedule for 2 weeks (current week and next week)
        for (weekOffset in 0..1) {
            val weekMonday = monday.plus(DatePeriod(days = weekOffset * 7))
            val weekTypeStr = if ((currentWeekNum + weekOffset) % 2 == 0) "upper" else "lower"

            for (dayIndex in 0..5) {
                val dayDate = weekMonday.plus(DatePeriod(days = dayIndex))
                val lessonsForDay = mutableListOf<LessonDto>()

                for (lesson in response.lessons) {
                    val parsedSlot = parseTimeslot(lesson.timeslot) ?: continue
                    if (parsedSlot.dayOfWeek != dayIndex) continue

                    val slotWeek = parsedSlot.weekType
                    if (slotWeek != "all" && slotWeek != "full" && slotWeek != weekTypeStr && slotWeek.isNotBlank()) {
                        continue
                    }

                    val curricula = curriculaByLessonId[lesson.id ?: -1] ?: emptyList()
                    val lessonType = when (lesson.ctype) {
                        true -> LessonTypeDto.LECTURE
                        false -> LessonTypeDto.PRACTICE
                        null -> LessonTypeDto.COMMON
                    }
                    val lessonNum = getLessonNumber(parsedSlot.startTime)

                    if (curricula.isNotEmpty()) {
                        for (curr in curricula) {
                            val subject = curr.subjectname.ifBlank { curr.subjectabbr ?: lesson.info ?: "Занятие" }
                            val teacher = curr.teachername ?: defaultTeacherName
                            val room = curr.roomname ?: ""
                            val groupName = if (isTeacher && lesson.uberid != null) {
                                groupsByUberId[lesson.uberid]?.name ?: ""
                            } else ""

                            val otUnit = OneTimeUnitDto(
                                teacher = teacher,
                                group = groupName,
                                room = room
                            )

                            lessonsForDay.add(
                                LessonDto(
                                    date = dayDate,
                                    number = lessonNum,
                                    startTime = parsedSlot.startTime,
                                    endTime = parsedSlot.endTime,
                                    subject = subject,
                                    otUnits = listOf(otUnit),
                                    type = lessonType
                                )
                            )
                        }
                    } else if (!lesson.info.isNullOrBlank()) {
                        val groupName = if (isTeacher && lesson.uberid != null) {
                            groupsByUberId[lesson.uberid]?.name ?: ""
                        } else ""

                        lessonsForDay.add(
                            LessonDto(
                                date = dayDate,
                                number = lessonNum,
                                startTime = parsedSlot.startTime,
                                endTime = parsedSlot.endTime,
                                subject = lesson.info,
                                otUnits = listOf(OneTimeUnitDto(teacher = defaultTeacherName, group = groupName, room = "")),
                                type = lessonType
                            )
                        )
                    }
                }

                if (lessonsForDay.isNotEmpty()) {
                    val combinedLessons = lessonsForDay
                        .groupBy { it.number to it.subject }
                        .map { (_, list) ->
                            list.first().copy(otUnits = list.flatMap { it.otUnits }.distinct())
                        }
                        .sortedWith(compareBy({ it.startTime }, { it.number }))

                    days.add(
                        DayScheduleDto(
                            date = dayDate,
                            scheduleType = LessonsScheduleTypeDto.COMMON,
                            lessons = combinedLessons
                        )
                    )
                }
            }
        }

        return days.sortedBy { it.date }
    }

    private fun getLessonNumber(startTime: LocalTime): Int = when {
        startTime.hour < 9 -> 1
        startTime.hour < 11 -> 2
        startTime.hour < 13 -> 3
        startTime.hour < 15 -> 4
        startTime.hour < 17 -> 5
        startTime.hour < 19 -> 6
        else -> 7
    }
}

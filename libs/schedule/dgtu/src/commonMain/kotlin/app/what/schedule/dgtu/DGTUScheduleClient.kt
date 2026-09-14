package app.what.schedule.dgtu

import app.what.schedule.core.clients.ScheduleClient
import app.what.schedule.core.models.*
import app.what.schedule.dgtu.models.ApiResponse
import app.what.schedule.dgtu.models.DGTUApi
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

class DGTUScheduleClient(
    private val client: HttpClient,
    private val baseUrl: String = "https://edu.donstu.ru/api",
    private val log: ((String) -> Unit)? = null
) : ScheduleClient {

    private var cachedYears: List<String>? = null

    private suspend fun getYears(): List<String> {
        cachedYears?.let { return it }
        val response = client.get("$baseUrl/Rasp/ListYears")
            .body<ApiResponse<DGTUApi.Schedule.ListYears>>()
            .data.years
        cachedYears = response
        return response
    }

    private suspend fun getActiveYear(): String {
        val years = getYears()
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val expectedYear = if (now.monthNumber >= 8) "${now.year}-${now.year + 1}" else "${now.year - 1}-${now.year}"
        return when {
            years.contains(expectedYear) -> expectedYear
            years.size >= 2 -> years[years.size - 2]
            else -> years.lastOrNull() ?: expectedYear
        }
    }

    private var cachedGroups: List<GroupDto>? = null
    private var cachedTeachers: List<TeacherDto>? = null

    override suspend fun getGroups(): List<GroupDto> {
        cachedGroups?.let { return it }
        val year = getActiveYear()
        val groups = client.get("$baseUrl/raspGrouplist?year=$year")
            .body<ApiResponse<List<DGTUApi.Models.DGTUGroup>>>()
            ._data.orEmpty().map { GroupDto(id = it.id.toString(), name = it.name.trim(), course = it.kurs ?: 1) }
            .distinctBy { it.name }
            .sortedBy { it.name }
        if (groups.isNotEmpty()) cachedGroups = groups
        return groups
    }

    override suspend fun getTeachers(): List<TeacherDto> {
        cachedTeachers?.let { return it }
        val year = getActiveYear()
        val teachers = client.get("$baseUrl/raspTeacherlist?year=$year")
            .body<ApiResponse<List<DGTUApi.Models.DGTUTeacher>>>()
            ._data.orEmpty().map { teacher ->
                val parts = teacher.name.split(" ")
                val formattedName = if (parts.size >= 3) {
                    "${parts[0]} ${parts[1].firstOrNull() ?: ""}.${parts[2].firstOrNull() ?: ""}."
                } else teacher.name
                TeacherDto(id = teacher.id.toString(), name = formattedName.trim())
            }
            .distinctBy { it.name }
            .sortedBy { it.name }
        if (teachers.isNotEmpty()) cachedTeachers = teachers
        return teachers
    }

    override suspend fun getGroupSchedule(group: String, showReplacements: Boolean): List<DayScheduleDto> =
        fetchSchedule(targetId = group, isTeacher = false)

    override suspend fun getTeacherSchedule(teacher: String, showReplacements: Boolean): List<DayScheduleDto> =
        fetchSchedule(targetId = teacher, isTeacher = true)

    private suspend fun fetchSchedule(targetId: String, isTeacher: Boolean): List<DayScheduleDto> = coroutineScope {
        log?.invoke("Запрос расписания ДГТУ для ${if (isTeacher) "преподавателя" else "группы"}: $targetId")

        val actualTargetId = if (targetId.all { it.isDigit() }) {
            targetId
        } else {
            if (isTeacher) {
                getTeachers().firstOrNull { it.name.equals(targetId, ignoreCase = true) || it.name.contains(targetId) }?.id ?: targetId
            } else {
                getGroups().firstOrNull { it.name.equals(targetId, ignoreCase = true) }?.id ?: targetId
            }
        }

        val paramName = if (isTeacher) "idTeacher" else "idGroup"
        
        try {
            // Запрос полного расписания без привязки к дате (возвращает все занятия семестра)
            val fullUrl = "$baseUrl/Rasp?$paramName=$actualTargetId"
            val response = client.get(fullUrl).body<ApiResponse<DGTUApi.Schedule.Get>>()
            val schedules = response._data?.rasp?.toDaySchedules() ?: emptyList()
            if (schedules.isNotEmpty()) {
                return@coroutineScope schedules
            }
        } catch (e: Exception) {
            log?.invoke("Ошибка загрузки общего расписания: ${e.message}")
        }

        // Запасной вариант: запрос по неделям
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val weeks = listOf(today, today.plus(7, DateTimeUnit.DAY))
        val jobs = weeks.map { weekDate ->
            async {
                try {
                    val dateFormatted = weekDate.toString()
                    val url = "$baseUrl/Rasp?$paramName=$actualTargetId&sdate=$dateFormatted"
                    val response = client.get(url).body<ApiResponse<DGTUApi.Schedule.Get>>()
                    response._data?.rasp?.toDaySchedules() ?: emptyList()
                } catch (e: Exception) {
                    log?.invoke("Ошибка загрузки недели $weekDate: ${e.message}")
                    emptyList()
                }
            }
        }

        val allWeeks = jobs.awaitAll().flatten()
        allWeeks.groupBy { it.date }.map { (date, days) ->
            val allLessons = days.flatMap { it.lessons }.distinctBy { "${it.number}_${it.subject}_${it.startTime}" }
            DayScheduleDto(
                date = date,
                scheduleType = LessonsScheduleTypeDto.COMMON,
                lessons = allLessons
            )
        }.sortedBy { it.date }
    }

    private fun List<DGTUApi.Models.DGTULesson>.toDaySchedules(): List<DayScheduleDto> =
        map { it.toLessonDto() }
            .groupBy { it.date }
            .map { (day, lessons) ->
                val mergedLessons = lessons.groupBy { it.number }.map { (_, lessonGroup) ->
                    lessonGroup.reduce { acc, next -> acc + next }
                }
                DayScheduleDto(
                    date = day,
                    scheduleType = LessonsScheduleTypeDto.COMMON,
                    lessons = mergedLessons.sortedBy { it.number }
                )
            }.sortedBy { it.date }

    private fun DGTUApi.Models.DGTULesson.toLessonDto(): LessonDto {
        val rawData = auditory?.split("-") ?: emptyList()
        val building = if (rawData.size > 1) "Корпус ${rawData[0]}" else "Главный"
        val aud = if (rawData.size > 1) rawData[1] else (auditory ?: "")

        val otUnit = OneTimeUnitDto(
            group = group ?: "",
            teacher = teacherName ?: teacher ?: "",
            room = aud,
            additional = building
        )

        return LessonDto(
            date = date.date,
            number = number,
            startTime = startTime.time,
            endTime = endTime.time,
            subject = subject,
            otUnits = listOf(otUnit),
            type = if (number == 0) LessonTypeDto.UNKNOWN else LessonTypeDto.COMMON,
            state = if (replacement == true) LessonStateDto.CHANGED else LessonStateDto.COMMON
        )
    }
}

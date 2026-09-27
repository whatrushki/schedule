package app.what.schedule.rgups

import app.what.schedule.core.models.*
import app.what.schedule.rgups.models.*
import com.fleeksoft.ksoup.Ksoup
import kotlinx.datetime.*

object RgupsHtmlParser {

    fun parseFaculties(html: String): List<RgupsFaculty> {
        val regex = Regex("""<a[^>]*data-fac-id=["'](\d+)["'][^>]*>(.*?)</a>([^<]*)""", RegexOption.IGNORE_CASE)
        val list = mutableListOf<RgupsFaculty>()
        for (match in regex.findAll(html)) {
            val id = match.groupValues[1].trim()
            val inner = match.groupValues[2].trim()
            val outer = match.groupValues[3].trim()
            val name = if (inner.isNotEmpty()) inner else outer
            if (id.isNotEmpty() && name.isNotEmpty()) {
                list.add(RgupsFaculty(id = id, name = name))
            }
        }
        if (list.isNotEmpty()) {
            return list.distinctBy { it.id }
        }

        val doc = Ksoup.parse(html)
        val links = doc.select("a[data-fac-id]")
        return links.mapNotNull { a ->
            val id = a.attr("data-fac-id").trim()
            val name = a.text().trim().ifEmpty { a.nextSibling()?.toString()?.trim().orEmpty() }
            if (id.isNotEmpty() && name.isNotEmpty()) {
                RgupsFaculty(id = id, name = name)
            } else null
        }.distinctBy { it.id }
    }

    fun parseCourses(html: String): List<Int> {
        val doc = Ksoup.parse(html)
        val links = doc.select("a[data-course-id]")
        return links.mapNotNull { a ->
            a.attr("data-course-id").trim().toIntOrNull()
        }.distinct().sorted()
    }

    fun parseGroups(html: String, facId: String, courseId: Int, eduType: String): List<RgupsGroup> {
        val doc = Ksoup.parse(html)
        val links = doc.select("a[data-group-id]")
        return links.mapNotNull { a ->
            val id = a.attr("data-group-id").trim()
            val name = a.text().trim()
            if (id.isNotEmpty() && name.isNotEmpty()) {
                RgupsGroup(
                    id = id,
                    name = name,
                    courseId = courseId,
                    facId = facId,
                    eduType = eduType
                )
            } else null
        }.distinctBy { it.id }
    }

    fun parseTimetable(html: String, currentLocalDate: LocalDate, groupName: String = ""): List<DayScheduleDto> {
        val doc = Ksoup.parse(html)
        val table = doc.selectFirst("table.table") ?: return emptyList()
        val rows = table.select("tr")

        // Find Monday of current week
        val currentDayOfWeek = currentLocalDate.dayOfWeek.isoDayNumber // 1..7
        val monday = currentLocalDate.minus(DatePeriod(days = currentDayOfWeek - 1))

        val daysMap = mutableMapOf<LocalDate, MutableList<LessonDto>>()
        var currentDayDate: LocalDate? = null
        var lastParnum = 0
        var lastStartTime = LocalTime(8, 0)
        var lastEndTime = LocalTime(9, 30)

        for (r in rows) {
            val th = r.selectFirst("th")
            if (th != null) {
                val headerText = th.text().trim()
                val dayOfWeekNum = parseDayOfWeek(headerText)
                currentDayDate = if (dayOfWeekNum in 1..7) {
                    monday.plus(DatePeriod(days = dayOfWeekNum - 1))
                } else null
                lastParnum = 0
                continue
            }

            val targetDate = currentDayDate ?: continue
            val tds = r.select("td")
            if (tds.isEmpty()) continue
            if (tds.size == 1 && tds[0].text().contains("Нет пар", ignoreCase = true)) {
                continue
            }

            val isDisabled = tds.any { it.hasClass("disable") }
            // If it's disabled, it belongs to the opposite week parity.
            // For now, we collect active lessons, but if both exist, active one takes precedence.

            if (tds.size >= 6) {
                val parnumStr = tds[0].text().trim()
                val parnum = parnumStr.toIntOrNull() ?: (lastParnum + 1)
                lastParnum = parnum

                val timeStr = tds[1].text().trim()
                val (st, et) = parseTimeRange(timeStr)
                lastStartTime = st
                lastEndTime = et

                val subjectRaw = tds[3].text().trim()
                if (subjectRaw == "—" || subjectRaw.isBlank()) continue

                val (subject, type) = parseSubjectAndType(subjectRaw)
                val teacher = tds[4].text().trim()
                val room = tds[5].text().trim()

                val lesson = LessonDto(
                    date = targetDate,
                    number = parnum,
                    startTime = st,
                    endTime = et,
                    subject = subject,
                    otUnits = listOf(OneTimeUnitDto(teacher = teacher, group = groupName, room = room)),
                    type = type,
                    state = if (isDisabled) LessonStateDto.CHANGED else LessonStateDto.COMMON
                )
                daysMap.getOrPut(targetDate) { mutableListOf() }.add(lesson)
            } else if (tds.size >= 4) {
                // Secondary row of rowspan pair (e.g. ['под чертой', subject, teacher, room])
                val subjectRaw = tds[1].text().trim()
                if (subjectRaw == "—" || subjectRaw.isBlank()) continue

                val (subject, type) = parseSubjectAndType(subjectRaw)
                val teacher = tds[2].text().trim()
                val room = tds[3].text().trim()

                val lesson = LessonDto(
                    date = targetDate,
                    number = lastParnum,
                    startTime = lastStartTime,
                    endTime = lastEndTime,
                    subject = subject,
                    otUnits = listOf(OneTimeUnitDto(teacher = teacher, group = groupName, room = room)),
                    type = type,
                    state = if (isDisabled) LessonStateDto.CHANGED else LessonStateDto.COMMON
                )
                daysMap.getOrPut(targetDate) { mutableListOf() }.add(lesson)
            }
        }

        return daysMap.map { (date, lessons) ->
            val mergedLessons = lessons
                .groupBy { Triple(it.number, it.startTime, it.subject) }
                .map { (_, groupLessons) ->
                    // Prefer non-disabled (state == COMMON) if both exist
                    val activeLesson = groupLessons.firstOrNull { it.state == LessonStateDto.COMMON } ?: groupLessons.first()
                    activeLesson.copy(
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

    private fun parseDayOfWeek(header: String): Int {
        val clean = header.lowercase()
        return when {
            clean.contains("понедельник") -> 1
            clean.contains("вторник") -> 2
            clean.contains("среда") -> 3
            clean.contains("четверг") -> 4
            clean.contains("пятница") -> 5
            clean.contains("суббота") -> 6
            clean.contains("воскресенье") -> 7
            else -> 0
        }
    }

    private fun parseSubjectAndType(raw: String): Pair<String, LessonTypeDto> {
        val regex = Regex("^(.*?)(?:\\s*\\((.*?)\\))?$")
        val match = regex.find(raw.trim())
        val subject = match?.groups?.get(1)?.value?.trim().orEmpty()
        val typeStr = match?.groups?.get(2)?.value?.trim().orEmpty()
        val type = when {
            typeStr.contains("ЛЕК", ignoreCase = true) -> LessonTypeDto.LECTURE
            typeStr.contains("ПРАК", ignoreCase = true) || typeStr.contains("ПР", ignoreCase = true) -> LessonTypeDto.PRACTICE
            typeStr.contains("ЛАБ", ignoreCase = true) -> LessonTypeDto.LABORATORY
            typeStr.contains("КОНС", ignoreCase = true) -> LessonTypeDto.CONSULTATION
            typeStr.contains("ЭКЗ", ignoreCase = true) -> LessonTypeDto.EXAM
            typeStr.contains("ЗАЧ", ignoreCase = true) -> LessonTypeDto.CREDIT
            else -> LessonTypeDto.COMMON
        }
        return subject to type
    }

    private fun parseTimeRange(range: String): Pair<LocalTime, LocalTime> {
        val parts = range.split("-", "—", "–").map { it.trim() }
        val start = parts.getOrNull(0)?.let { parseTime(it) } ?: LocalTime(8, 15)
        val end = parts.getOrNull(1)?.let { parseTime(it) } ?: LocalTime(9, 45)
        return start to end
    }

    private fun parseTime(str: String): LocalTime {
        return try {
            val parts = str.split(":", ".").map { it.trim().toInt() }
            LocalTime(parts[0], parts[1])
        } catch (_: Exception) {
            LocalTime(8, 15)
        }
    }
}

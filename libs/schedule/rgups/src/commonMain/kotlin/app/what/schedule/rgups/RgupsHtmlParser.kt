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

    private data class ParsedSlotRow(
        val isDisabled: Boolean,
        val weekLabel: String,
        val subject: String,
        val teacher: String,
        val room: String
    )

    private data class ParsedSlot(
        val parnum: Int,
        val startTime: LocalTime,
        val endTime: LocalTime,
        val rows: MutableList<ParsedSlotRow> = mutableListOf()
    )

    fun parseTimetable(html: String, currentLocalDate: LocalDate, groupName: String = ""): List<DayScheduleDto> {
        val doc = Ksoup.parse(html)
        val table = doc.selectFirst("table.table") ?: return emptyList()
        val rows = table.select("tr")

        // Find Monday of current week
        val currentDayOfWeek = currentLocalDate.dayOfWeek.isoDayNumber // 1..7
        val monday = currentLocalDate.minus(DatePeriod(days = currentDayOfWeek - 1))

        val daysMap = mutableMapOf<LocalDate, MutableList<ParsedSlot>>()
        var currentDayDate: LocalDate? = null
        var currentSlot: ParsedSlot? = null

        for (r in rows) {
            val th = r.selectFirst("th")
            if (th != null) {
                val headerText = th.text().trim()
                val dayOfWeekNum = parseDayOfWeek(headerText)
                currentDayDate = if (dayOfWeekNum in 1..7) {
                    monday.plus(DatePeriod(days = dayOfWeekNum - 1))
                } else null
                currentSlot = null
                continue
            }

            val targetDate = currentDayDate ?: continue
            val tds = r.select("td")
            if (tds.isEmpty()) continue
            if (tds.size == 1 && tds[0].text().contains("Нет пар", ignoreCase = true)) {
                continue
            }

            val isDisabled = tds.any { it.hasClass("disable") }
            val firstText = tds[0].text().trim()

            if (tds.size >= 6 && firstText.toIntOrNull() != null) {
                val parnum = firstText.toInt()
                val (st, et) = parseTimeRange(tds[1].text().trim())
                val weekLabel = tds[2].text().trim()
                val subject = tds[3].text().trim()
                val teacher = cleanTeacher(tds[4].text().trim())
                val room = tds[5].text().trim()

                val slot = ParsedSlot(
                    parnum = parnum,
                    startTime = st,
                    endTime = et,
                    rows = mutableListOf(ParsedSlotRow(isDisabled, weekLabel, subject, teacher, room))
                )
                currentSlot = slot
                daysMap.getOrPut(targetDate) { mutableListOf() }.add(slot)
            } else if (tds.size >= 4) {
                val weekLabel = tds[0].text().trim()
                val subject = tds[1].text().trim()
                val teacher = cleanTeacher(tds[2].text().trim())
                val room = tds[3].text().trim()
                currentSlot?.rows?.add(ParsedSlotRow(isDisabled, weekLabel, subject, teacher, room))
            } else if (tds.size >= 3) {
                val subject = tds[0].text().trim()
                val teacher = cleanTeacher(tds[1].text().trim())
                val room = tds[2].text().trim()
                val weekLabel = currentSlot?.rows?.lastOrNull()?.weekLabel.orEmpty()
                currentSlot?.rows?.add(ParsedSlotRow(isDisabled, weekLabel, subject, teacher, room))
            }
        }

        return daysMap.map { (date, slots) ->
            val dayLessons = mutableListOf<LessonDto>()

            for (slot in slots) {
                val slotRows = slot.rows
                if (slotRows.isEmpty()) continue

                // Check "обе недели"
                if (slotRows.size == 1 && slotRows[0].weekLabel.contains("обе недели", ignoreCase = true)) {
                    val r = slotRows[0]
                    if (r.subject.isNotBlank() && r.subject != "—") {
                        val (subj, type) = parseSubjectAndType(r.subject)
                        dayLessons.add(
                            LessonDto(
                                date = date,
                                number = slot.parnum,
                                startTime = slot.startTime,
                                endTime = slot.endTime,
                                subject = subj,
                                otUnits = listOf(OneTimeUnitDto(teacher = r.teacher, group = groupName, room = r.room)),
                                type = type,
                                state = LessonStateDto.COMMON
                            )
                        )
                    }
                    continue
                }

                // Split week (над чертой / под чертой)
                val activeRows = slotRows.filter { !it.isDisabled }
                val disabledRows = slotRows.filter { it.isDisabled }

                val validActive = activeRows.filter { it.subject.isNotBlank() && it.subject != "—" }

                if (validActive.isNotEmpty()) {
                    // Group valid active rows by subject (e.g. sub-groups [1] and [2])
                    val bySubj = validActive.groupBy { it.subject }
                    for ((subjRaw, subjRows) in bySubj) {
                        val (subj, type) = parseSubjectAndType(subjRaw)
                        val units = subjRows.map { r ->
                            OneTimeUnitDto(teacher = r.teacher, group = groupName, room = r.room)
                        }.distinct()
                        dayLessons.add(
                            LessonDto(
                                date = date,
                                number = slot.parnum,
                                startTime = slot.startTime,
                                endTime = slot.endTime,
                                subject = subj,
                                otUnits = units,
                                type = type,
                                state = LessonStateDto.CHANGED
                            )
                        )
                    }
                } else {
                    // Active week has no lesson (dash '—' or empty) -> pair is cancelled
                    val validDisabled = disabledRows.filter { it.subject.isNotBlank() && it.subject != "—" }
                    if (validDisabled.isNotEmpty()) {
                        val bySubj = validDisabled.groupBy { it.subject }
                        for ((subjRaw, subjRows) in bySubj) {
                            val (subj, type) = parseSubjectAndType(subjRaw)
                            val units = subjRows.map { r ->
                                OneTimeUnitDto(teacher = r.teacher, group = groupName, room = r.room)
                            }.distinct()
                            dayLessons.add(
                                LessonDto(
                                    date = date,
                                    number = slot.parnum,
                                    startTime = slot.startTime,
                                    endTime = slot.endTime,
                                    subject = subj,
                                    otUnits = units,
                                    type = type,
                                    state = LessonStateDto.REMOVED
                                )
                            )
                        }
                    }
                }
            }

            DayScheduleDto(
                date = date,
                scheduleType = LessonsScheduleTypeDto.COMMON,
                lessons = dayLessons.sortedWith(compareBy({ it.startTime }, { it.number }))
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

    fun cleanTeacher(teacher: String): String {
        return teacher.replace(Regex("""\s*\[\d+\]"""), "").trim()
    }

    private fun parseSubjectAndType(raw: String): Pair<String, LessonTypeDto> {
        val regex = Regex("^(.*?)(?:\\s*\\((.*?)\\))?$")
        val match = regex.find(raw.trim())
        val baseSubject = match?.groups?.get(1)?.value?.trim().orEmpty()
        val typeStr = match?.groups?.get(2)?.value?.trim().orEmpty()
        val (type, typeLabel) = when {
            typeStr.contains("ЛЕК", ignoreCase = true) -> LessonTypeDto.LECTURE to " (лекция)"
            typeStr.contains("ПРАК", ignoreCase = true) || typeStr.contains("ПР", ignoreCase = true) -> LessonTypeDto.PRACTICE to " (практика)"
            typeStr.contains("ЛАБ", ignoreCase = true) -> LessonTypeDto.LABORATORY to " (лабораторная)"
            typeStr.contains("КОНС", ignoreCase = true) -> LessonTypeDto.CONSULTATION to " (консультация)"
            typeStr.contains("ЭКЗ", ignoreCase = true) -> LessonTypeDto.EXAM to " (экзамен)"
            typeStr.contains("ЗАЧ", ignoreCase = true) -> LessonTypeDto.CREDIT to " (зачёт)"
            typeStr.isNotBlank() -> LessonTypeDto.COMMON to " ($typeStr)"
            else -> LessonTypeDto.COMMON to ""
        }
        val subject = if (typeLabel.isNotEmpty()) "$baseSubject$typeLabel" else baseSubject
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

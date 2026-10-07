package app.what.schedule.rksi.parser

import app.what.schedule.core.models.LessonDto
import app.what.schedule.core.models.LessonStateDto
import app.what.schedule.core.models.LessonTimeDto
import app.what.schedule.core.models.LessonTypeDto
import app.what.schedule.core.models.OneTimeUnitDto
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

object RKSIReplacementsParser {

    fun parse(
        sheets: Map<String, List<List<String>>>,
        columns: Int,
        date: LocalDate,
        predicate: (teacher: String, group: String) -> Boolean
    ): List<LessonDto> {
        val lessons = mutableListOf<LessonDto>()

        sheets.forEach { (sheetName, rows) ->
            var emptyRows = 0
            var rowIndex = -1
            val otUnits = mutableListOf<OneTimeUnitDto>()

            val isClassHourSheet = sheetName.contains("кл", ignoreCase = true) ||
                sheetName.contains("классн", ignoreCase = true)
            val lessonNumber = when {
                isClassHourSheet -> 0
                "Пара" !in sheetName -> 0
                else -> sheetName.split(" ").last().toIntOrNull() ?: 0
            }

            for (row in rows) {
                rowIndex++
                if (rowIndex == 0) continue
                if (emptyRows > 5) break

                if (row.isEmpty() || row.all { it.isBlank() }) {
                    emptyRows++
                } else {
                    (0 until columns).mapNotNull { i ->
                        val firstCellIndex = i * 3
                        val auditory = row.getOrNull(firstCellIndex)?.trim()?.ifEmpty { null } ?: return@mapNotNull null
                        val teacher = row.getOrNull(firstCellIndex + 2)?.trim()?.ifEmpty { null } ?: return@mapNotNull null
                        val rawGroups = row.getOrNull(firstCellIndex + 1)?.trim()?.ifEmpty { null } ?: return@mapNotNull null
                        val groups = rawGroups
                            .split(',', '+', '/')
                            .map { it.trim() }
                            .filter { it.isNotEmpty() && predicate(teacher, it) }
                            .ifEmpty { null } ?: return@mapNotNull null

                        groups.map { groupName ->
                            OneTimeUnitDto(
                                teacher = teacher.replace("__", "_"),
                                group = groupName,
                                room = try { auditory.toFloat().toInt().toString() } catch (_: Exception) { auditory },
                                additional = if (columns == 1) "2" else "1"
                            )
                        }
                    }.let { otUnits.addAll(it.flatten()) }

                    if (otUnits.isNotEmpty()) {
                        val isClassHour = lessonNumber == 0 || isClassHourSheet
                        val defaultStartTime = if (isClassHour) LocalTime(13, 5) else LocalTime(0, 0)
                        val defaultEndTime = if (isClassHour) LocalTime(14, 5) else LocalTime(0, 0)
                        lessons.add(
                            LessonDto(
                                date = date,
                                number = if (isClassHour) 0 else lessonNumber,
                                startTime = defaultStartTime,
                                endTime = defaultEndTime,
                                otUnits = otUnits.toList(),
                                subject = if (isClassHour) "Классный час" else "",
                                type = if (isClassHour) LessonTypeDto.CLASS_HOUR else LessonTypeDto.OTHER
                            )
                        )
                        otUnits.clear()
                    }
                }
            }
        }

        val mergedLessons = lessons.groupBy { it.date to it.number }
            .map { (_, groupLessons) ->
                groupLessons.first().copy(
                    otUnits = groupLessons.flatMap { it.otUnits }.distinct()
                )
            }

        return mergedLessons
    }

    private fun normalize(name: String): String = name
        .replace(" ", "")
        .replace("-", "")
        .replace("—", "")
        .replace("–", "")
        .replace(".", "")
        .replace("c", "с", ignoreCase = true)
        .replace("a", "а", ignoreCase = true)
        .replace("e", "е", ignoreCase = true)
        .replace("o", "о", ignoreCase = true)
        .replace("p", "р", ignoreCase = true)
        .replace("x", "х", ignoreCase = true)
        .trim()
        .lowercase()

    private fun isSameTeacher(baseTeacher: String?, repTeacher: String?): Boolean {
        if (baseTeacher.isNullOrBlank() || repTeacher.isNullOrBlank()) return false
        val cleanBase = normalize(baseTeacher)
        val cleanRep = normalize(repTeacher)
        return cleanBase == cleanRep || cleanBase.startsWith(cleanRep) || cleanRep.startsWith(cleanBase)
    }

    fun applyReplacements(
        baseLessons: List<LessonDto>,
        replacements: List<LessonDto>,
        timeSchedule: List<LessonTimeDto>,
        subjectResolver: ((teacher: String, group: String) -> String?)? = null
    ): List<LessonDto> {
        val cleanBaseLessons = baseLessons.groupBy { Triple(it.date, it.startTime, it.subject) }
            .map { (_, groupLessons) ->
                groupLessons.first().copy(
                    otUnits = groupLessons.flatMap { it.otUnits }.distinct()
                )
            }

        if (replacements.isEmpty()) {
            return cleanBaseLessons.sortedWith(compareBy({ it.startTime }, { it.number }))
        }

        val cleanReplacements = replacements.groupBy { it.date to it.number }
            .map { (_, groupLessons) ->
                groupLessons.first().copy(
                    otUnits = groupLessons.flatMap { it.otUnits }.distinct()
                )
            }

        val minTime = LocalTime(0, 0)
        val unionSchedule = mutableMapOf<Int, Pair<LessonDto?, LessonDto?>>()
        cleanReplacements.forEach { unionSchedule[it.number] = it to null }
        cleanBaseLessons.forEach { unionSchedule[it.number] = unionSchedule[it.number]?.first to it }

        return unionSchedule.mapNotNull { (_, pair) ->
            val replacement = pair.first
            val lesson = pair.second

            if (replacement == null && lesson != null) {
                // Пары нет в планшетке замен, хотя для группы были замены в этот день -> пара отменена
                lesson.copy(state = LessonStateDto.REMOVED)
            } else if (replacement != null && lesson == null) {
                // Добавленная пара
                val lessonTime = timeSchedule.firstOrNull { it.number == replacement.number }
                val repTeacher = replacement.otUnits.firstOrNull()?.teacher.orEmpty().trim()
                val repGroup = replacement.otUnits.firstOrNull()?.group.orEmpty().trim()
                val resolvedSubject = if (repTeacher.isNotEmpty()) subjectResolver?.invoke(repTeacher, repGroup) else null

                val isClassHour = replacement.number == 0 ||
                    replacement.subject.contains("Классный", ignoreCase = true) ||
                    replacement.type == LessonTypeDto.CLASS_HOUR

                val subject = when {
                    isClassHour -> "Классный час"
                    replacement.subject.isNotBlank() -> replacement.subject
                    !resolvedSubject.isNullOrBlank() -> resolvedSubject
                    else -> "Предмет не указан"
                }

                val classHourStart = LocalTime(13, 5)
                val classHourEnd = LocalTime(14, 5)
                val start = lessonTime?.start
                    ?: if (isClassHour) classHourStart else replacement.startTime.takeIf { it != minTime } ?: minTime
                val end = lessonTime?.end
                    ?: if (isClassHour) classHourEnd else replacement.endTime.takeIf { it != minTime } ?: minTime

                replacement.copy(
                    number = if (isClassHour) 0 else replacement.number,
                    state = LessonStateDto.ADDED,
                    startTime = start,
                    endTime = end,
                    subject = subject,
                    type = if (isClassHour) LessonTypeDto.CLASS_HOUR else replacement.type
                )
            } else if (replacement != null && lesson != null) {
                val isClassHour = replacement.number == 0 ||
                    replacement.subject.contains("Классный", ignoreCase = true) ||
                    replacement.type == LessonTypeDto.CLASS_HOUR ||
                    lesson.number == 0 ||
                    lesson.type == LessonTypeDto.CLASS_HOUR ||
                    lesson.subject.contains("Классный", ignoreCase = true)

                val classHourStart = LocalTime(13, 5)
                val classHourEnd = LocalTime(14, 5)

                if (lesson.equalsWithReplacement(replacement)) {
                    // Преподаватель и аудитория совпадают с базовым расписанием -> пара НЕ изменена
                    if (isClassHour) {
                        val start = lesson.startTime.takeIf { it != minTime } ?: classHourStart
                        val end = lesson.endTime.takeIf { it != minTime } ?: classHourEnd
                        lesson.copy(number = 0, type = LessonTypeDto.CLASS_HOUR, startTime = start, endTime = end)
                    } else {
                        lesson
                    }
                } else {
                    // Преподаватель или аудитория изменились -> пара изменена
                    val lessonTime = timeSchedule.firstOrNull { it.number == replacement.number }
                    val repTeacher = replacement.otUnits.firstOrNull()?.teacher.orEmpty().trim()
                    val repGroup = replacement.otUnits.firstOrNull()?.group.orEmpty().trim()
                    val sameTeacher = lesson.otUnits.any { isSameTeacher(it.teacher, repTeacher) }

                    val subject = when {
                        isClassHour -> "Классный час"
                        replacement.subject.isNotBlank() -> replacement.subject
                        sameTeacher -> lesson.subject.ifEmpty { "Предмет не указан" }
                        else -> {
                            val resolved = if (repTeacher.isNotEmpty()) subjectResolver?.invoke(repTeacher, repGroup) else null
                            if (!resolved.isNullOrBlank()) resolved else "Предмет не указан"
                        }
                    }

                    val targetType = when {
                        isClassHour -> LessonTypeDto.CLASS_HOUR
                        sameTeacher -> lesson.type
                        else -> LessonTypeDto.COMMON
                    }

                    val start = lesson.startTime.takeIf { it != minTime }
                        ?: (lessonTime?.start ?: if (isClassHour) classHourStart else replacement.startTime.takeIf { it != minTime } ?: minTime)
                    val end = lesson.endTime.takeIf { it != minTime }
                        ?: (lessonTime?.end ?: if (isClassHour) classHourEnd else replacement.endTime.takeIf { it != minTime } ?: minTime)

                    replacement.copy(
                        number = if (isClassHour) 0 else replacement.number,
                        state = LessonStateDto.CHANGED,
                        startTime = start,
                        endTime = end,
                        subject = subject,
                        type = targetType
                    )
                }
            } else {
                null
            }
        }.sortedWith(compareBy({ it.startTime }, { it.number }))
    }
}

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

            val lessonNumber = if ("Пара" !in sheetName) 0
            else sheetName.split(" ").last().toIntOrNull() ?: 0

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
                        lessons.add(
                            LessonDto(
                                date = date,
                                number = lessonNumber,
                                startTime = LocalTime(0, 0),
                                endTime = LocalTime(0, 0),
                                otUnits = otUnits.toList(),
                                subject = if (lessonNumber == 0) "Классный час" else "",
                                type = if (lessonNumber == 0) LessonTypeDto.CLASS_HOUR else LessonTypeDto.OTHER
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

                val subject = when {
                    replacement.number == 0 -> "Классный час"
                    replacement.subject.isNotBlank() -> replacement.subject
                    !resolvedSubject.isNullOrBlank() -> resolvedSubject
                    else -> "Предмет не указан"
                }

                replacement.copy(
                    state = LessonStateDto.ADDED,
                    startTime = lessonTime?.start ?: minTime,
                    endTime = lessonTime?.end ?: minTime,
                    subject = subject
                )
            } else if (replacement != null && lesson != null) {
                if (lesson.equalsWithReplacement(replacement)) {
                    // Преподаватель и аудитория совпадают с базовым расписанием -> пара НЕ изменена
                    lesson
                } else {
                    // Преподаватель или аудитория изменились -> пара изменена
                    val lessonTime = timeSchedule.firstOrNull { it.number == replacement.number }
                    val repTeacher = replacement.otUnits.firstOrNull()?.teacher.orEmpty().trim()
                    val repGroup = replacement.otUnits.firstOrNull()?.group.orEmpty().trim()
                    val sameTeacher = lesson.otUnits.any { isSameTeacher(it.teacher, repTeacher) }

                    val subject = when {
                        replacement.number == 0 -> "Классный час"
                        replacement.subject.isNotBlank() -> replacement.subject
                        sameTeacher -> lesson.subject.ifEmpty { "Предмет не указан" }
                        else -> {
                            val resolved = if (repTeacher.isNotEmpty()) subjectResolver?.invoke(repTeacher, repGroup) else null
                            if (!resolved.isNullOrBlank()) resolved else "Предмет не указан"
                        }
                    }

                    replacement.copy(
                        state = LessonStateDto.CHANGED,
                        startTime = lesson.startTime.takeIf { it != minTime } ?: (lessonTime?.start ?: minTime),
                        endTime = lesson.endTime.takeIf { it != minTime } ?: (lessonTime?.end ?: minTime),
                        subject = subject,
                        type = if (sameTeacher) lesson.type else LessonTypeDto.COMMON
                    )
                }
            } else {
                null
            }
        }.sortedWith(compareBy({ it.startTime }, { it.number }))
    }
}

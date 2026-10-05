package app.what.schedule.rksi.parser

import app.what.schedule.core.models.*
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
        val scrapedBase = baseLessons.map { it.toScrapedLesson() }
        val scrapedRep = replacements.map { it.toScrapedLesson() }
        val slots = timeSchedule.map { it.toTimeSlot() }
        val merged = app.what.foundation.scraper.engine.ReconciliationEngine.applyReplacements(
            scrapedBase,
            scrapedRep,
            slots,
            subjectResolver
        )
        return merged.map { it.toLessonDto() }
    }
}

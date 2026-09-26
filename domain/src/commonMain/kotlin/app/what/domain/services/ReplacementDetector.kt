package app.what.domain.services

import app.what.domain.models.DaySchedule
import app.what.domain.models.Lesson
import app.what.domain.models.LessonState
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

data class ReplacementItem(
    val day: DaySchedule,
    val lesson: Lesson,
    val signature: String
)

data class ReplacementDetectionResult(
    val hasNewReplacements: Boolean,
    val newReplacements: List<ReplacementItem>,
    val allCurrentReplacements: List<ReplacementItem>,
    val updatedSignatures: Set<String>
)

object ReplacementDetector {

    /**
     * Формирует уникальную сигнатуру конкретной замены
     */
    fun buildSignature(
        searchId: String,
        date: LocalDate,
        lesson: Lesson
    ): String {
        val units = lesson.otUnits.joinToString(",") { "${it.teacher.name}:${it.auditory}" }
        return "$searchId|$date|${lesson.number}|${lesson.subject}|${lesson.state}|${lesson.startTime}|${lesson.endTime}|$units"
    }

    /**
     * Извлекает дату из сигнатуры ("searchId|YYYY-MM-DD|...")
     */
    fun extractDate(signature: String): LocalDate? {
        val parts = signature.split("|")
        return if (parts.size >= 2) {
            runCatching { LocalDate.parse(parts[1]) }.getOrNull()
        } else null
    }

    /**
     * Выделяет только новые замены на сегодня и ближайшие [daysAhead] дней
     *
     * @param searchId Идентификатор выбранной группы или преподавателя
     * @param schedules Список дней расписания
     * @param today Текущая дата
     * @param knownSignatures Ранее отправленные сигнатуры замен
     * @param daysAhead На сколько дней вперед отслеживать (по умолчанию 2: сегодня, завтра, послезавтра)
     */
    fun detect(
        searchId: String,
        schedules: List<DaySchedule>,
        today: LocalDate,
        knownSignatures: Set<String>,
        daysAhead: Int = 2
    ): ReplacementDetectionResult {
        val relevantDates = (0..daysAhead).map { today.plus(it, DateTimeUnit.DAY) }.toSet()
        val relevantDays = schedules.filter { it.date in relevantDates }

        val currentReplacements = mutableListOf<ReplacementItem>()
        for (day in relevantDays) {
            for (lesson in day.lessons) {
                if (lesson.state != LessonState.COMMON) {
                    val sig = buildSignature(searchId, day.date, lesson)
                    currentReplacements.add(ReplacementItem(day, lesson, sig))
                }
            }
        }

        // Новые замены - те, чьих сигнатур еще нет в известных
        val newReplacements = currentReplacements.filter { it.signature !in knownSignatures }

        // Обновляем сигнатуры: удаляем дни в прошлом (< today) и сохраняем текущие актуальные
        val updatedSignatures = knownSignatures
            .filter { sig ->
                val date = extractDate(sig)
                date == null || date >= today
            }
            .toMutableSet()
            .apply {
                addAll(currentReplacements.map { it.signature })
            }

        return ReplacementDetectionResult(
            hasNewReplacements = newReplacements.isNotEmpty(),
            newReplacements = newReplacements,
            allCurrentReplacements = currentReplacements,
            updatedSignatures = updatedSignatures
        )
    }

    fun serializeSignatures(signatures: Set<String>): String =
        signatures.joinToString("\n")

    fun deserializeSignatures(raw: String?): Set<String> {
        if (raw.isNullOrBlank()) return emptySet()
        return raw.lineSequence().filter { it.isNotBlank() }.toSet()
    }
}

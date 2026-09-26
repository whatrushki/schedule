package app.what.domain.services

import app.what.domain.models.Lesson
import app.what.domain.models.LessonState
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

data class FormattedReplacementNotification(
    val title: String,
    val summary: String,
    val details: List<String>
)

object ReplacementNotificationFormatter {

    fun format(
        searchName: String,
        newReplacements: List<ReplacementItem>,
        today: LocalDate
    ): FormattedReplacementNotification {
        if (newReplacements.isEmpty()) {
            return FormattedReplacementNotification(
                title = "Замены в расписании ($searchName)",
                summary = "Нет новых замен",
                details = emptyList()
            )
        }

        val dates = newReplacements.map { it.day.date }.distinct().sorted()
        val isSingleDay = dates.size == 1
        val singleDate = dates.first()

        // 1. Формируем заголовок с указанием дня, если все замены приходятся на один день
        val title = if (isSingleDay) {
            val dayPrefix = when (singleDate) {
                today -> "Замены на сегодня"
                today.plus(1, DateTimeUnit.DAY) -> "Замены на завтра"
                today.plus(2, DateTimeUnit.DAY) -> "Замены на послезавтра"
                else -> "Замены на ${formatDate(singleDate)}"
            }
            "$dayPrefix ($searchName)"
        } else {
            "Замены в расписании ($searchName)"
        }

        // 2. Формируем подробные строки для раскрытого вида (InboxStyle)
        val details = newReplacements.map { item ->
            formatDetailLine(
                item = item,
                today = today,
                includeDayPrefix = !isSingleDay
            )
        }

        // 3. Формируем лаконичный однострочный текст для свернутого уведомления
        val summary = formatSummary(
            newReplacements = newReplacements,
            today = today,
            isSingleDay = isSingleDay
        )

        return FormattedReplacementNotification(
            title = title,
            summary = summary,
            details = details
        )
    }

    private fun formatDetailLine(
        item: ReplacementItem,
        today: LocalDate,
        includeDayPrefix: Boolean
    ): String {
        val lesson = item.lesson
        val day = item.day

        val dayPrefix = if (includeDayPrefix) {
            when (day.date) {
                today -> "Сегодня: "
                today.plus(1, DateTimeUnit.DAY) -> "Завтра: "
                today.plus(2, DateTimeUnit.DAY) -> "Послезавтра: "
                else -> "${formatDate(day.date)}: "
            }
        } else ""

        val sign = when (lesson.state) {
            LessonState.REMOVED -> "–"
            LessonState.ADDED -> "+"
            LessonState.CHANGED -> "~"
            LessonState.COMMON -> "•"
        }

        val pairPrefix = if (lesson.number > 0) "${lesson.number} пара: " else ""
        val subject = lesson.subject.ifBlank { "Пара ${lesson.number}" }

        val room = extractRoom(lesson)
        val extra = when (lesson.state) {
            LessonState.REMOVED -> " (отмена)"
            LessonState.ADDED -> if (room != null) " (ауд. $room)" else ""
            LessonState.CHANGED -> if (room != null) " (ауд. $room)" else " (изм.)"
            LessonState.COMMON -> ""
        }

        return "$dayPrefix$sign $pairPrefix$subject$extra"
    }

    private fun formatSummary(
        newReplacements: List<ReplacementItem>,
        today: LocalDate,
        isSingleDay: Boolean
    ): String {
        val compactItems = newReplacements.map { item ->
            formatCompactItem(item, today, includeDay = !isSingleDay)
        }

        val buffer = mutableListOf<String>()
        var currentLength = 0
        var addedCount = 0

        for (itemStr in compactItems) {
            val projectedLength = if (buffer.isEmpty()) itemStr.length else currentLength + 2 + itemStr.length
            if (projectedLength <= 60 || buffer.isEmpty()) {
                buffer.add(itemStr)
                currentLength = projectedLength
                addedCount++
            } else {
                break
            }
        }

        val remaining = compactItems.size - addedCount
        return if (remaining > 0) {
            "${buffer.joinToString(", ")} (+$remaining)"
        } else {
            buffer.joinToString(", ")
        }
    }

    private fun formatCompactItem(
        item: ReplacementItem,
        today: LocalDate,
        includeDay: Boolean
    ): String {
        val lesson = item.lesson
        val day = item.day

        val dayPrefix = if (includeDay) {
            when (day.date) {
                today -> "Сегодня "
                today.plus(1, DateTimeUnit.DAY) -> "Завтра "
                else -> "${formatDate(day.date)} "
            }
        } else ""

        val sign = when (lesson.state) {
            LessonState.REMOVED -> "–"
            LessonState.ADDED -> "+"
            LessonState.CHANGED -> "~"
            LessonState.COMMON -> ""
        }

        val pair = if (lesson.number > 0) "${lesson.number} п. " else ""
        val subject = lesson.subject.ifBlank { "Пара ${lesson.number}" }

        return "$dayPrefix$sign $pair$subject".trim()
    }

    private fun extractRoom(lesson: Lesson): String? {
        val room = lesson.otUnits.firstOrNull()?.auditory?.trim()?.removeSuffix(".0")
        return if (!room.isNullOrBlank() && room != "-" && !room.equals("empty", ignoreCase = true)) {
            room
        } else null
    }

    private fun formatDate(date: LocalDate): String {
        val day = date.dayOfMonth.toString().padStart(2, '0')
        val month = date.monthNumber.toString().padStart(2, '0')
        return "$day.$month"
    }
}

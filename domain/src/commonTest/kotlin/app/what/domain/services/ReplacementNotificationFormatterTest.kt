package app.what.domain.services

import app.what.domain.models.DaySchedule
import app.what.domain.models.Group
import app.what.domain.models.Lesson
import app.what.domain.models.LessonState
import app.what.domain.models.LessonType
import app.what.domain.models.LessonsScheduleType
import app.what.domain.models.OneTimeUnit
import app.what.domain.models.Teacher
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReplacementNotificationFormatterTest {

    private val today = LocalDate(2026, 9, 26)
    private val group = "ИС-31"

    private fun createLesson(
        date: LocalDate,
        number: Int,
        subject: String,
        state: LessonState,
        auditory: String = "-"
    ): Pair<DaySchedule, Lesson> {
        val unit = OneTimeUnit(Group("ИС-31"), Teacher("Иванов И.И."), auditory, "1")
        val lesson = Lesson(
            date = date,
            number = number,
            startTime = LocalTime(9, 0),
            endTime = LocalTime(10, 30),
            subject = subject,
            otUnits = listOf(unit),
            type = LessonType.COMMON,
            state = state
        )
        val day = DaySchedule(
            date = date,
            scheduleType = LessonsScheduleType.COMMON,
            lessons = listOf(lesson)
        )
        return day to lesson
    }

    private fun toItem(pair: Pair<DaySchedule, Lesson>): ReplacementItem {
        val sig = ReplacementDetector.buildSignature("ИС-31", pair.first.date, pair.second)
        return ReplacementItem(pair.first, pair.second, sig)
    }

    @Test
    fun testSingleDayTodayFormatting() {
        val (day, lesson) = createLesson(today, 2, "Физика", LessonState.REMOVED)
        val items = listOf(toItem(day to lesson))

        val result = ReplacementNotificationFormatter.format(group, items, today)

        assertEquals("Замены на сегодня (ИС-31)", result.title)
        assertEquals("– 2 п. Физика", result.summary)
        assertEquals(1, result.details.size)
        assertEquals("– 2 пара: Физика (отмена)", result.details.first())
        // Проверяем, что нет лишнего слова "Сегодня:" в строке пары
        assertFalse(result.details.first().contains("Сегодня"))
    }

    @Test
    fun testSingleDayTomorrowFormatting() {
        val tomorrow = today.plus(1, DateTimeUnit.DAY)
        val (day, lesson) = createLesson(tomorrow, 1, "Математика", LessonState.ADDED, auditory = "302")
        val items = listOf(toItem(day to lesson))

        val result = ReplacementNotificationFormatter.format(group, items, today)

        assertEquals("Замены на завтра (ИС-31)", result.title)
        assertEquals("+ 1 п. Математика", result.summary)
        assertEquals("+ 1 пара: Математика (ауд. 302)", result.details.first())
    }

    @Test
    fun testMultipleReplacementsSameDay() {
        val (day1, l1) = createLesson(today, 2, "Физика", LessonState.REMOVED)
        val (day2, l2) = createLesson(today, 3, "Информатика", LessonState.CHANGED, auditory = "105")
        val items = listOf(toItem(day1 to l1), toItem(day2 to l2))

        val result = ReplacementNotificationFormatter.format(group, items, today)

        assertEquals("Замены на сегодня (ИС-31)", result.title)
        assertEquals("– 2 п. Физика, ~ 3 п. Информатика", result.summary)
        assertEquals(2, result.details.size)
        assertEquals("– 2 пара: Физика (отмена)", result.details[0])
        assertEquals("~ 3 пара: Информатика (ауд. 105)", result.details[1])
    }

    @Test
    fun testMultipleDaysFormatting() {
        val tomorrow = today.plus(1, DateTimeUnit.DAY)
        val (day1, l1) = createLesson(today, 2, "Физика", LessonState.REMOVED)
        val (day2, l2) = createLesson(tomorrow, 1, "Математика", LessonState.ADDED, auditory = "302")
        val items = listOf(toItem(day1 to l1), toItem(day2 to l2))

        val result = ReplacementNotificationFormatter.format(group, items, today)

        assertEquals("Замены в расписании (ИС-31)", result.title)
        assertTrue(result.details[0].startsWith("Сегодня: – 2 пара: Физика (отмена)"))
        assertTrue(result.details[1].startsWith("Завтра: + 1 пара: Математика (ауд. 302)"))
    }

    @Test
    fun testManyReplacementsSummaryHasRemainingCount() {
        val items = (1..6).map { num ->
            val (d, l) = createLesson(today, num, "Предмет $num", LessonState.CHANGED)
            toItem(d to l)
        }

        val result = ReplacementNotificationFormatter.format(group, items, today)

        assertEquals("Замены на сегодня (ИС-31)", result.title)
        assertTrue(result.summary.contains("(+"))
    }
}

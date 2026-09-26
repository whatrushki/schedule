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

class ReplacementDetectorTest {

    private val baseDate = LocalDate(2026, 9, 28)

    private fun createLesson(
        date: LocalDate,
        number: Int,
        subject: String,
        state: LessonState = LessonState.COMMON,
        startTime: LocalTime = LocalTime(8, 0),
        endTime: LocalTime = LocalTime(9, 30),
        teacherName: String = "Иванов И.И.",
        auditory: String = "101"
    ): Lesson {
        return Lesson(
            date = date,
            number = number,
            startTime = startTime,
            endTime = endTime,
            subject = subject,
            otUnits = listOf(
                OneTimeUnit(
                    group = Group("ИС-31"),
                    teacher = Teacher(teacherName),
                    auditory = auditory,
                    building = "Главный"
                )
            ),
            type = LessonType.COMMON,
            state = state
        )
    }

    private fun createDay(date: LocalDate, vararg lessons: Lesson): DaySchedule {
        return DaySchedule(
            date = date,
            scheduleType = LessonsScheduleType.COMMON,
            lessons = lessons.toList()
        )
    }

    @Test
    fun testCommonLessonsIgnored() {
        val schedule = listOf(
            createDay(
                baseDate,
                createLesson(baseDate, 1, "Математика", LessonState.COMMON),
                createLesson(baseDate, 2, "Физика", LessonState.COMMON)
            )
        )

        val result = ReplacementDetector.detect(
            searchId = "ИС-31",
            schedules = schedule,
            today = baseDate,
            knownSignatures = emptySet()
        )

        assertFalse(result.hasNewReplacements)
        assertTrue(result.newReplacements.isEmpty())
        assertTrue(result.allCurrentReplacements.isEmpty())
    }

    @Test
    fun testNewReplacementsDetectedOnFirstRun() {
        val schedule = listOf(
            createDay(
                baseDate,
                createLesson(baseDate, 1, "Математика", LessonState.CHANGED),
                createLesson(baseDate, 2, "Информатика", LessonState.ADDED)
            )
        )

        val result = ReplacementDetector.detect(
            searchId = "ИС-31",
            schedules = schedule,
            today = baseDate,
            knownSignatures = emptySet()
        )

        assertTrue(result.hasNewReplacements)
        assertEquals(2, result.newReplacements.size)
        assertEquals(2, result.updatedSignatures.size)
    }

    @Test
    fun testSecondRunDoesNotNotifyAgain() {
        val schedule = listOf(
            createDay(
                baseDate,
                createLesson(baseDate, 1, "Математика", LessonState.CHANGED)
            )
        )

        // Первая проверка - замена найдена
        val firstResult = ReplacementDetector.detect(
            searchId = "ИС-31",
            schedules = schedule,
            today = baseDate,
            knownSignatures = emptySet()
        )
        assertTrue(firstResult.hasNewReplacements)

        // Вторая проверка с тем же расписанием и сохраненными сигнатурами
        val secondResult = ReplacementDetector.detect(
            searchId = "ИС-31",
            schedules = schedule,
            today = baseDate,
            knownSignatures = firstResult.updatedSignatures
        )

        // Повторного уведомления НЕТ!
        assertFalse(secondResult.hasNewReplacements)
        assertTrue(secondResult.newReplacements.isEmpty())
        assertEquals(1, secondResult.allCurrentReplacements.size)
    }

    @Test
    fun testOnlyNewReplacementReportedWhenOneAdded() {
        val tomorrow = baseDate.plus(1, DateTimeUnit.DAY)

        // Сначала была замена только на сегодня
        val initialSchedule = listOf(
            createDay(baseDate, createLesson(baseDate, 1, "Математика", LessonState.CHANGED))
        )
        val firstResult = ReplacementDetector.detect(
            searchId = "ИС-31",
            schedules = initialSchedule,
            today = baseDate,
            knownSignatures = emptySet()
        )

        // Позже колледж опубликовал замену на завтра (Физика)
        val updatedSchedule = listOf(
            createDay(baseDate, createLesson(baseDate, 1, "Математика", LessonState.CHANGED)),
            createDay(tomorrow, createLesson(tomorrow, 2, "Физика", LessonState.ADDED))
        )
        val secondResult = ReplacementDetector.detect(
            searchId = "ИС-31",
            schedules = updatedSchedule,
            today = baseDate,
            knownSignatures = firstResult.updatedSignatures
        )

        // Задетектировано появление новой замены
        assertTrue(secondResult.hasNewReplacements)
        // В newReplacements должна быть ТОЛЬКО Физика, старая Математика не дублируется!
        assertEquals(1, secondResult.newReplacements.size)
        assertEquals("Физика", secondResult.newReplacements.first().lesson.subject)
        assertEquals(tomorrow, secondResult.newReplacements.first().day.date)

        // При этом allCurrentReplacements содержит обе замены
        assertEquals(2, secondResult.allCurrentReplacements.size)
    }

    @Test
    fun testReplacementStateChangeIsDetected() {
        // Урок был изменен (CHANGED)
        val initial = listOf(
            createDay(baseDate, createLesson(baseDate, 1, "Математика", LessonState.CHANGED))
        )
        val firstResult = ReplacementDetector.detect("ИС-31", initial, baseDate, emptySet())

        // Затем статус изменился на отмененный (REMOVED)
        val updated = listOf(
            createDay(baseDate, createLesson(baseDate, 1, "Математика", LessonState.REMOVED))
        )
        val secondResult = ReplacementDetector.detect("ИС-31", updated, baseDate, firstResult.updatedSignatures)

        // Изменение статуса распознается как новая замена
        assertTrue(secondResult.hasNewReplacements)
        assertEquals(1, secondResult.newReplacements.size)
        assertEquals(LessonState.REMOVED, secondResult.newReplacements.first().lesson.state)
    }

    @Test
    fun testDaysBeyondTwoDaysIgnored() {
        val threeDaysLater = baseDate.plus(3, DateTimeUnit.DAY)
        val schedule = listOf(
            createDay(threeDaysLater, createLesson(threeDaysLater, 1, "Химия", LessonState.ADDED))
        )

        val result = ReplacementDetector.detect(
            searchId = "ИС-31",
            schedules = schedule,
            today = baseDate,
            knownSignatures = emptySet()
        )

        // Замена через 3 дня пока не входит в окно [today..today+2]
        assertFalse(result.hasNewReplacements)
    }

    @Test
    fun testPastReplacementsPruned() {
        val yesterday = baseDate.plus(-1, DateTimeUnit.DAY)
        val pastSig = "ИС-31|$yesterday|1|История|CHANGED|08:00|09:30|Петров:202"

        val schedule = listOf(
            createDay(baseDate, createLesson(baseDate, 1, "Математика", LessonState.CHANGED))
        )

        val result = ReplacementDetector.detect(
            searchId = "ИС-31",
            schedules = schedule,
            today = baseDate,
            knownSignatures = setOf(pastSig)
        )

        assertTrue(result.hasNewReplacements)
        // Сигнатура вчерашнего дня должна быть очищена из updatedSignatures
        assertFalse(result.updatedSignatures.contains(pastSig))
    }

    @Test
    fun testGroupIsolation() {
        val scheduleGroupA = listOf(
            createDay(baseDate, createLesson(baseDate, 1, "Математика", LessonState.CHANGED))
        )
        val resultA = ReplacementDetector.detect("ИС-31", scheduleGroupA, baseDate, emptySet())

        // Та же пара у группы ИС-32 не должна глушиться сигнатурой ИС-31
        val scheduleGroupB = listOf(
            createDay(baseDate, createLesson(baseDate, 1, "Математика", LessonState.CHANGED))
        )
        val resultB = ReplacementDetector.detect("ИС-32", scheduleGroupB, baseDate, resultA.updatedSignatures)

        assertTrue(resultB.hasNewReplacements)
    }

    @Test
    fun testSerializationRoundtrip() {
        val sigs = setOf(
            "ИС-31|2026-09-28|1|Математика|CHANGED",
            "ИС-31|2026-09-29|2|Физика|ADDED"
        )
        val serialized = ReplacementDetector.serializeSignatures(sigs)
        val deserialized = ReplacementDetector.deserializeSignatures(serialized)

        assertEquals(sigs, deserialized)
    }
}

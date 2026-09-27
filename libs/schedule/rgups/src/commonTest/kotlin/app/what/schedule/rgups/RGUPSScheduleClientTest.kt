package app.what.schedule.rgups

import app.what.schedule.core.models.LessonTypeDto
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RGUPSScheduleClientTest {

    @Test
    fun testParseFaculties() {
        val html = """
        <div class="schedule-section faculty">
            <a href="#" data-element="schedule" class="btn btn-default" data-fac-id="111">Энергетический</a>
            <a href="#" data-element="schedule" class="btn btn-default" data-fac-id="5">Электромеханический</a>
        </div>
        """.trimIndent()

        val faculties = RgupsHtmlParser.parseFaculties(html)
        assertEquals(2, faculties.size)
        assertEquals("111", faculties[0].id)
        assertEquals("Энергетический", faculties[0].name)
    }

    @Test
    fun testParseFacultiesWithSiblingText() {
        val html = """
        <div class="schedule-section faculty" data-action="course">
            <a class="btn btn-primary" data-element="schedule" data-fac-id="316" href="#"></a>Аспирантура и докторантура
            <a class="btn btn-primary" data-element="schedule" data-fac-id="8" href="#"></a>Гуманитарный
        </div>
        """.trimIndent()

        val faculties = RgupsHtmlParser.parseFaculties(html)
        assertEquals(2, faculties.size)
        assertEquals("316", faculties[0].id)
        assertEquals("Аспирантура и докторантура", faculties[0].name)
        assertEquals("8", faculties[1].id)
        assertEquals("Гуманитарный", faculties[1].name)
    }

    @Test
    fun testParseCourses() {
        val html = """
        <div class="schedule-section btn-group" data-action="groups"> 
            <a href="#" data-element="schedule" class="btn btn-default" data-course-id="1" data-fac-id="111">1 курс</a>
            <a href="#" data-element="schedule" class="btn btn-default" data-course-id="2" data-fac-id="111">2 курс</a>
        </div>
        """.trimIndent()

        val courses = RgupsHtmlParser.parseCourses(html)
        assertEquals(listOf(1, 2), courses)
    }

    @Test
    fun testParseGroups() {
        val html = """
        <div class="schedule-section groups" data-action="timetable">
            <a href="#" data-course-id="4" data-fac-id="111" data-group-id="29372">ЭЖС-4-200</a>
            <a href="#" data-course-id="4" data-fac-id="111" data-group-id="29398">ЭЖС-4-201</a>
        </div>
        """.trimIndent()

        val groups = RgupsHtmlParser.parseGroups(html, facId = "111", courseId = 4, eduType = "internal")
        assertEquals(2, groups.size)
        assertEquals("29372", groups[0].id)
        assertEquals("ЭЖС-4-200", groups[0].name)
    }

    @Test
    fun testParseTimetable() {
        val tableHtml = """
        <table class="table">
            <tr>
                <th class=" info" colspan="6">Понедельник (завтра)</th>
            </tr>
            <tr>
                <td>4</td>
                <td>13.55-15.25</td>
                <td>обе недели</td>
                <td>Электромагнитные расчеты (ЛЕК)</td>
                <td>Замшина Л.Л.</td>
                <td>Э106</td>
            </tr>
        </table>
        """.trimIndent()

        val monday = LocalDate(2026, 9, 28)
        val schedule = RgupsHtmlParser.parseTimetable(tableHtml, monday)
        assertEquals(1, schedule.size)
        assertEquals(monday, schedule[0].date)
        assertEquals(1, schedule[0].lessons.size)
        val lesson = schedule[0].lessons[0]
        assertEquals(4, lesson.number)
        assertEquals("Электромагнитные расчеты", lesson.subject)
        assertEquals(LessonTypeDto.LECTURE, lesson.type)
        assertEquals("Замшина Л.Л.", lesson.otUnits.firstOrNull()?.teacher)
        assertEquals("Э106", lesson.otUnits.firstOrNull()?.room)
    }
}

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

    @Test
    fun testParseTimetableSplitWeekAndSubgroupsAndCancelled() {
        val tableHtml = """
        <table class="table">
            <tr><th class=" info" colspan="6">Понедельник (завтра)</th></tr>
            <!-- Split week: active nad chertoy, disabled pod chertoy -->
            <tr>
                <td rowspan="2">4</td>
                <td rowspan="2">13.55-15.25</td>
                <td>над чертой</td>
                <td>Управление эксплуатационной работой (ПРАК)</td>
                <td>Мусиенко Н.Н.</td>
                <td>Г206</td>
            </tr>
            <tr>
                <td class="disable">под чертой</td>
                <td class="disable">Физико-химические основы перевозки грузов (ПРАК)</td>
                <td class="disable">Шишияну Д.Н.</td>
                <td class="disable">А325</td>
            </tr>
            <tr><th colspan="6">Вторник</th></tr>
            <!-- Subgroups: 2 rows for active week, 1 row for disabled week -->
            <tr>
                <td rowspan="4">3</td>
                <td rowspan="4">12.00-13.30</td>
                <td rowspan="3">над чертой</td>
                <td>Безопасность жизнедеятельности (ЛАБ)</td>
                <td>Порческо А.А. [1]</td>
                <td>М156</td>
            </tr>
            <tr>
                <td>Безопасность жизнедеятельности (ЛАБ)</td>
                <td>Воробинская Л.И. [2]</td>
                <td>М158</td>
            </tr>
            <tr>
                <td class="disable">под чертой</td>
                <td class="disable">Основы логистики (ПРАК)</td>
                <td class="disable">Ковалева Н.А.</td>
                <td class="disable">Д512</td>
            </tr>
            <tr><th colspan="6">Среда</th></tr>
            <!-- Cancelled lesson: active is dash —, disabled is subject -->
            <tr>
                <td rowspan="2">4</td>
                <td rowspan="2">13.55-15.25</td>
                <td>над чертой</td>
                <td>—</td>
                <td></td>
                <td></td>
            </tr>
            <tr>
                <td class="disable">под чертой</td>
                <td class="disable">Управление грузовой и коммерческой работой (ПРАК)</td>
                <td class="disable">Пасечная Е.В.</td>
                <td class="disable">Д208</td>
            </tr>
        </table>
        """.trimIndent()

        val monday = LocalDate(2026, 9, 28)
        val schedule = RgupsHtmlParser.parseTimetable(tableHtml, monday)
        assertEquals(3, schedule.size)

        // Monday: only 1 lesson (active) with state CHANGED
        val monLessons = schedule[0].lessons
        assertEquals(1, monLessons.size)
        assertEquals(4, monLessons[0].number)
        assertEquals("Управление эксплуатационной работой", monLessons[0].subject)
        assertEquals(app.what.schedule.core.models.LessonStateDto.CHANGED, monLessons[0].state)
        assertEquals("Мусиенко Н.Н.", monLessons[0].otUnits[0].teacher)

        // Tuesday: 1 lesson with 2 otUnits (subgroups [1] and [2]) with state CHANGED
        val tueLessons = schedule[1].lessons
        assertEquals(1, tueLessons.size)
        assertEquals(3, tueLessons[0].number)
        assertEquals("Безопасность жизнедеятельности", tueLessons[0].subject)
        assertEquals(app.what.schedule.core.models.LessonStateDto.CHANGED, tueLessons[0].state)
        assertEquals(2, tueLessons[0].otUnits.size)
        assertEquals("Порческо А.А. [1]", tueLessons[0].otUnits[0].teacher)
        assertEquals("Воробинская Л.И. [2]", tueLessons[0].otUnits[1].teacher)

        // Wednesday: 1 lesson with state REMOVED (cancelled)
        val wedLessons = schedule[2].lessons
        assertEquals(1, wedLessons.size)
        assertEquals(4, wedLessons[0].number)
        assertEquals("Управление грузовой и коммерческой работой", wedLessons[0].subject)
        assertEquals(app.what.schedule.core.models.LessonStateDto.REMOVED, wedLessons[0].state)
        assertEquals("Пасечная Е.В.", wedLessons[0].otUnits[0].teacher)
    }
}


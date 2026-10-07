package app.what.schedule.rgups

import app.what.schedule.core.models.LessonTypeDto
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        assertEquals("Электромагнитные расчеты (лекция)", lesson.subject)
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
        assertEquals("Управление эксплуатационной работой (практика)", monLessons[0].subject)
        assertEquals(app.what.schedule.core.models.LessonStateDto.CHANGED, monLessons[0].state)
        assertEquals("Мусиенко Н.Н.", monLessons[0].otUnits[0].teacher)

        // Tuesday: 1 lesson with 2 otUnits (subgroups [1] and [2] stripped) with state CHANGED
        val tueLessons = schedule[1].lessons
        assertEquals(1, tueLessons.size)
        assertEquals(3, tueLessons[0].number)
        assertEquals("Безопасность жизнедеятельности (лабораторная)", tueLessons[0].subject)
        assertEquals(app.what.schedule.core.models.LessonStateDto.CHANGED, tueLessons[0].state)
        assertEquals(2, tueLessons[0].otUnits.size)
        assertEquals("Порческо А.А.", tueLessons[0].otUnits[0].teacher)
        assertEquals("Воробинская Л.И.", tueLessons[0].otUnits[1].teacher)

        // Wednesday: 1 lesson with state REMOVED (cancelled)
        val wedLessons = schedule[2].lessons
        assertEquals(1, wedLessons.size)
        assertEquals(4, wedLessons[0].number)
        assertEquals("Управление грузовой и коммерческой работой (практика)", wedLessons[0].subject)
        assertEquals(app.what.schedule.core.models.LessonStateDto.REMOVED, wedLessons[0].state)
        assertEquals("Пасечная Е.В.", wedLessons[0].otUnits[0].teacher)
    }

    @Test
    fun testNewsSmartParagraphsAndLists() {
        val detailHtml = """
        <div class="news-detail">
            <h1>Всероссийские соревнования</h1>
            <div class="text">
                <p>
                    В сентябре состоялись соревнования. РГУПС достойно представили:<br>
                    - Даниил Сапухин<br>
                    - Савелий Шаров<br>
                    По итогам соревнований победу одержал Даниил.<br>
                    Поздравляем ребят с победой!
                </p>
            </div>
        </div>
        """.trimIndent()

        // Dummy client since parseNewsDetail doesn't use HTTP
        val client = RGUPSNewsClient()
        val detail = client.parseNewsDetail(detailHtml, "https://www.rgups.ru/news/test/")
        assertEquals(4, detail.contentBlocks.size)
        assertTrue(detail.contentBlocks[0] is app.what.schedule.core.models.NewContentBlockDto.Text)
        assertTrue(detail.contentBlocks[1] is app.what.schedule.core.models.NewContentBlockDto.UnsortedList)
        val listBlock = detail.contentBlocks[1] as app.what.schedule.core.models.NewContentBlockDto.UnsortedList
        assertEquals(2, listBlock.items.size)
        assertEquals("Даниил Сапухин", listBlock.items[0])
        assertEquals("Савелий Шаров", listBlock.items[1])
        assertTrue(detail.contentBlocks[2] is app.what.schedule.core.models.NewContentBlockDto.Text)
        assertTrue(detail.contentBlocks[3] is app.what.schedule.core.models.NewContentBlockDto.Text)
    }

    @Test
    fun testNewsConsecutiveNumberedListAndEmptyParagraphs() {
        val html = """
        <div class="text">
            <p>Начинается регистрация участников из числа научно-педагогических работников в Международном конкурсе лучших педагогических практик «Лидеры транспортного образования».</p>
            <p>Цель конкурса – выявление, поддержка и тиражирование эффективных педагогических практик в сфере инженерного и транспортного образования, формирование профессионального сообщества преподавателей, ориентированных на развитие практико-ориентированного подхода и внедрение современных образовательных технологий.</p>
            <p>Регистрация на платформе конкурса осуществляется в период с 01.09.2026 по 25.09.2026 на платформе конкурса <a href="https://edtech.rut-miit.ru/">https://edtech.rut-miit.ru/</a></p>
            <p>Призы и награды:</p>
            <p>1. Денежные призы за 1 место в каждой номинации;</p>
            <p>2. Публикация лучших практик в сборнике;</p>
            <p>3. Дипломы для участников, призеров и победителей;</p>
            <p>4. Повышение квалификации с выдачей удостоверения.</p>
            <p style="margin-top:12px">&nbsp;</p>
            <p><strong>Контактное лицо: </strong>Мироненко Екатерина Игоревна, кабинет А-209, <a href="mailto:umu@rgups.ru">umu@rgups.ru</a></p>
            <img src="/site/assets/files/237921/ped__konkurs_na_sait_page-0001-1.jpg">
        </div>
        """.trimIndent()

        val client = RGUPSNewsClient()
        val detail = client.parseNewsDetail(html, "https://www.rgups.ru/news/test/")

        // Verify empty spacing paragraph was ignored
        // Expected blocks: Text(Начинается), Text(Цель), Text(Регистрация), Text(Призы), SortedList(4 items), Text(Контактное), Image
        assertEquals(7, detail.contentBlocks.size)
        assertTrue(detail.contentBlocks[0] is app.what.schedule.core.models.NewContentBlockDto.Text)
        assertTrue(detail.contentBlocks[1] is app.what.schedule.core.models.NewContentBlockDto.Text)
        assertTrue(detail.contentBlocks[2] is app.what.schedule.core.models.NewContentBlockDto.Text)
        assertTrue(detail.contentBlocks[3] is app.what.schedule.core.models.NewContentBlockDto.Text)

        // The 4 separate <p> items must be merged into 1 single SortedList
        assertTrue(detail.contentBlocks[4] is app.what.schedule.core.models.NewContentBlockDto.SortedList)
        val sortedList = detail.contentBlocks[4] as app.what.schedule.core.models.NewContentBlockDto.SortedList
        assertEquals(4, sortedList.items.size)
        assertEquals("Денежные призы за 1 место в каждой номинации;", sortedList.items[0])
        assertEquals("Публикация лучших практик в сборнике;", sortedList.items[1])
        assertEquals("Дипломы для участников, призеров и победителей;", sortedList.items[2])
        assertEquals("Повышение квалификации с выдачей удостоверения.", sortedList.items[3])

        assertTrue(detail.contentBlocks[5] is app.what.schedule.core.models.NewContentBlockDto.Text)
        assertTrue(detail.contentBlocks[6] is app.what.schedule.core.models.NewContentBlockDto.Image)
        val img = detail.contentBlocks[6] as app.what.schedule.core.models.NewContentBlockDto.Image
        assertEquals("https://www.rgups.ru/site/assets/files/237921/ped__konkurs_na_sait_page-0001-1.jpg", img.url)
    }

    @Test
    fun testNewsHeaderWithLinkPreserved() {
        val html = """
        <div class="text">
            <p>Продолжается набор на занятия по мобильной робототехнике.&nbsp;</p>
            <p>Занятия проводятся каждую субботу с <strong>10:00 до 13:00.</strong></p>
            <h3><a href="/university/struktura-i-organy-upravleniia-1632/strukturnye-podrazdeleniia/crk/otdel-dovuzovskoi-podgotovki/tvorcheskie-kruzhki-dlya-shkolnikov/mobil-naia-robototekhnika/">Ознакомиться с кружком по ссылке</a></h3>
            <img src="/site/assets/files/237167/banner_robototekhnika_1.jpg">
        </div>
        """.trimIndent()

        val client = RGUPSNewsClient()
        val detail = client.parseNewsDetail(html, "https://www.rgups.ru/news/test/")

        assertEquals(4, detail.contentBlocks.size)
        assertTrue(detail.contentBlocks[0] is app.what.schedule.core.models.NewContentBlockDto.Text)
        assertTrue(detail.contentBlocks[1] is app.what.schedule.core.models.NewContentBlockDto.Text)

        // Header with <a> link must be preserved as Text with absolute URL so the link is clickable
        assertTrue(detail.contentBlocks[2] is app.what.schedule.core.models.NewContentBlockDto.Text)
        val headerText = (detail.contentBlocks[2] as app.what.schedule.core.models.NewContentBlockDto.Text).html
        assertTrue(headerText.contains("https://www.rgups.ru/university/struktura-i-organy-upravleniia-1632/"))
        assertTrue(headerText.contains("Ознакомиться с кружком по ссылке"))

        assertTrue(detail.contentBlocks[3] is app.what.schedule.core.models.NewContentBlockDto.Image)
    }

    @Test
    fun testEmptyOrUnderscoreTeacherAndRoomBecomeDash() {
        val tableHtml = """
        <table class="table">
            <tr><th colspan="6">Понедельник</th></tr>
            <tr>
                <td>1</td>
                <td>08.30-10.00</td>
                <td>обе недели</td>
                <td>Информатика</td>
                <td>_</td>
                <td></td>
            </tr>
        </table>
        """.trimIndent()

        val monday = LocalDate(2026, 9, 28)
        val schedule = RgupsHtmlParser.parseTimetable(tableHtml, monday, groupName = "_")
        assertEquals(1, schedule.size)
        val lesson = schedule[0].lessons[0]
        val unit = lesson.otUnits[0]
        assertEquals("-", unit.teacher)
        assertEquals("-", unit.room)
        assertEquals("-", unit.group)
    }

    @Test
    fun testDateAtStartNotTreatedAsNumberedList() {
        val html = """
        <div class="text">
            <p>24.09.26 г. на Электромеханическом факультете состоялась открытая лекция заместителя начальника Северо-Кавказкой дирекции моторвагонного подвижного состава по кадрам и социальным вопросам Коблева Мадина Мадиновича на тему: «РЖД сегодня и завтра» для студентов 4 курса, обучающихся по специальности «Подвижной состав железных дорог».</p>
        </div>
        """.trimIndent()

        val client = RGUPSNewsClient()
        val detail = client.parseNewsDetail(html, "https://www.rgups.ru/news/test/")

        assertEquals(1, detail.contentBlocks.size)
        assertTrue(detail.contentBlocks[0] is app.what.schedule.core.models.NewContentBlockDto.Text)
        val text = (detail.contentBlocks[0] as app.what.schedule.core.models.NewContentBlockDto.Text).html
        assertTrue(text.startsWith("24.09.26 г."))
    }
}


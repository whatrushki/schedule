package app.what.schedule.sfedu

import app.what.schedule.sfedu.models.*
import com.fleeksoft.ksoup.Ksoup
import io.ktor.client.HttpClient
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SFEDUScheduleClientTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun testParseTimeslot() {
        val client = SFEDUScheduleClient(HttpClient())
        val slot1 = client.parseTimeslot("(0,13:45:00,15:20:00,upper)")
        assertNotNull(slot1)
        assertEquals(0, slot1.dayOfWeek)
        assertEquals(LocalTime(13, 45), slot1.startTime)
        assertEquals(LocalTime(15, 20), slot1.endTime)
        assertEquals("upper", slot1.weekType)

        val slot2 = client.parseTimeslot("(4,08:00:00,09:35:00,all)")
        assertNotNull(slot2)
        assertEquals(4, slot2.dayOfWeek)
        assertEquals(LocalTime(8, 0), slot2.startTime)
        assertEquals(LocalTime(9, 35), slot2.endTime)
        assertEquals("all", slot2.weekType)
    }

    @Test
    fun testParseScheduleResponseJson() {
        val rawJson = """
            {
                "lessons": [
                    {
                        "id": 101,
                        "timeslot": "(0,13:45:00,15:20:00,upper)",
                        "ctype": true,
                        "info": "Лекция",
                        "uberid": 185
                    }
                ],
                "curricula": [
                    {
                        "lessonid": 101,
                        "subjectname": "Математический анализ",
                        "subjectabbr": "Матан",
                        "teachername": "Карякин М.И.",
                        "roomname": "ауд. 120"
                    }
                ],
                "groups": [
                    {
                        "id": 185,
                        "name": "ММ и ИИ",
                        "num": 7
                    }
                ]
            }
        """.trimIndent()

        val parsed = json.decodeFromString<SfeduScheduleResponse>(rawJson)
        assertEquals(1, parsed.lessons.size)
        assertEquals(101, parsed.lessons[0].id)
        assertEquals(true, parsed.lessons[0].ctype)
        assertEquals(1, parsed.curricula.size)
        assertEquals("Математический анализ", parsed.curricula[0].subjectname)
        assertEquals("Карякин М.И.", parsed.curricula[0].teachername)
        assertEquals("ауд. 120", parsed.curricula[0].roomname)
        assertEquals(1, parsed.groups.size)
        assertEquals("ММ и ИИ", parsed.groups[0].name)
    }

    @Test
    fun testParseGradesAndGroupsJson() {
        val gradesJson = """[{"id":1,"num":1,"degree":"bachelor"}]"""
        val groupsJson = """[{"id":185,"name":"ММ и ИИ","num":7,"gradeid":1,"grorder":1}]"""

        val grades = json.decodeFromString<List<SfeduGrade>>(gradesJson)
        val groups = json.decodeFromString<List<SfeduGroup>>(groupsJson)

        assertEquals(1, grades.size)
        assertEquals("bachelor", grades[0].degree)
        assertEquals(1, groups.size)
        assertEquals(185, groups[0].id)
        assertEquals("ММ и ИИ", groups[0].name)
    }

    @Test
    fun testParseSFEDUNewsHtml() {
        val sampleHtml = """
            <div class="news_item_f">
                <h2 class="article_title">
                    <a href="/novosti/123-den-matematika">День математика в институте</a>
                </h2>
                <div class="createdate">15.09.2026</div>
                <div class="newsitem_text">
                    <img src="/images/math_day.jpg" />
                    <p>Состоялось торжественное празднование Дня математика на мехмате ЮФУ.</p>
                </div>
            </div>
        """.trimIndent()

        val doc = Ksoup.parse(sampleHtml)
        val title = doc.select(".article_title a").text().trim()
        val href = doc.select(".article_title a").attr("href").trim()
        val date = doc.select(".createdate").text().trim()
        val img = doc.select("img").attr("src").trim()
        val desc = doc.select(".newsitem_text p").text().trim()

        assertEquals("День математика в институте", title)
        assertEquals("/novosti/123-den-matematika", href)
        assertEquals("15.09.2026", date)
        assertEquals("/images/math_day.jpg", img)
        assertTrue(desc.contains("Дня математика"))
    }
}

package app.what.schedule.rinh

import app.what.schedule.rinh.models.RINHApi
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RINHScheduleClientTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Test
    fun testParseScheduleSearch() {
        val jsonString = """
        [
            {"id": 1, "name": "ПИ-411"},
            {"id": 2, "name": "Иванов И.И."}
        ]
        """.trimIndent()

        val items = json.decodeFromString<List<RINHApi.Schedule.Responses.ScheduleSearch>>(jsonString)
        assertEquals(2, items.size)
        assertEquals("ПИ-411", items[0].name)
        assertEquals("Иванов И.И.", items[1].name)

        val groups = items.filter { "," !in it.name && "." !in it.name && "№" !in it.name && it.name.isNotBlank() }
        val teachers = items.filter { ("," in it.name || "." in it.name || "№" in it.name) && it.name.isNotBlank() }

        assertEquals(1, groups.size)
        assertEquals("ПИ-411", groups[0].name)
        assertEquals(1, teachers.size)
        assertEquals("Иванов И.И.", teachers[0].name)
    }

    @Test
    fun testParseGetSchedule() {
        val jsonString = """
        {
            "kind": "group",
            "instance": "ПИ-411",
            "weeks": [
                {
                    "id": 1,
                    "name": "Неделя 1",
                    "current": true,
                    "parity": 1,
                    "days": [
                        {
                            "id": 1,
                            "date": "2026-09-01",
                            "name": "Вторник",
                            "pairs": [
                                {
                                    "id": 1,
                                    "startTime": "08:30:00",
                                    "endTime": "10:00:00",
                                    "lessons": [
                                        {
                                            "id": 101,
                                            "teacher": {"id": 1, "name": "Иванов И.И."},
                                            "subgroup": {"id": 1, "name": "Вся группа"},
                                            "subject": "Базы данных",
                                            "group": "ПИ-411",
                                            "kind": {"id": 1, "name": "Лекция", "shortName": "лек."},
                                            "audience": "101"
                                        }
                                    ]
                                }
                            ]
                        }
                    ]
                }
            ]
        }
        """.trimIndent()

        val parsed = json.decodeFromString<RINHApi.Schedule.Responses.GetSchedule>(jsonString)
        assertEquals("ПИ-411", parsed.instance)
        assertEquals(1, parsed.weeks.size)
        val day = parsed.weeks[0].days[0]
        assertEquals("2026-09-01", day.date)
        assertEquals(1, day.pairs.size)
        val lesson = day.pairs[0].lessons[0]
        assertEquals("Базы данных", lesson.subject)
        assertEquals("Иванов И.И.", lesson.teacher.name)
    }

    @Test
    fun testParseRINHNewsDetail() {
        val sampleHtml = """
            <html>
                <body>
                    <div id="content-news">
                        <h1>В РИНХе прошла научная конференция</h1>
                        <div id="text-news">
                            <p>В актовом зале собрались ведущие экономисты региона.</p>
                            <p>Обсуждались перспективы цифровой экономики.</p>
                        </div>
                        <div class="slider-news">
                            <a href="/upload/photo1.jpg"><img src="/upload/photo1_thumb.jpg"/></a>
                            <a href="/upload/photo2.jpg"><img src="/upload/photo2_thumb.jpg"/></a>
                        </div>
                    </div>
                </body>
            </html>
        """.trimIndent()

        val doc = com.fleeksoft.ksoup.Ksoup.parse(sampleHtml)
        val title = doc.selectFirst("#content-news h1")?.text()?.trim() ?: ""
        val textContainer = doc.selectFirst("#text-news")
        val paragraphs = textContainer?.select("p")?.map { it.text().trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        val images = doc.select(".slider-news a")
            .map { it.attr("href").trim() }
            .filter { it.isNotEmpty() }

        assertEquals("В РИНХе прошла научная конференция", title)
        assertEquals(2, paragraphs.size)
        assertEquals("В актовом зале собрались ведущие экономисты региона.", paragraphs[0])
        assertEquals(2, images.size)
    }
}
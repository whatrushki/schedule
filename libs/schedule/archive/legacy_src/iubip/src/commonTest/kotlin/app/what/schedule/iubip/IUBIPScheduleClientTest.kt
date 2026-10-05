package app.what.schedule.iubip

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IUBIPScheduleClientTest {

    @Test
    fun testParseIubipGroupsJson() {
        val sampleGroupsJson = """
        {
            "Колледж права": {
                "Ю101": 1,
                "Ю201": 2
            },
            "Информационные технологии": {
                "ИТ101": 1,
                "ИТ201": 2,
                "ИТ301": 3
            }
        }
        """.trimIndent()

        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        val root = json.parseToJsonElement(sampleGroupsJson)
        assertTrue(root is JsonObject)

        val groups = mutableListOf<String>()
        (root as JsonObject).values.forEach { facultyElem ->
            if (facultyElem is JsonObject) {
                facultyElem.entries.forEach { (groupName, _) ->
                    groups.add(groupName.trim())
                }
            }
        }

        assertEquals(5, groups.size)
        assertTrue(groups.contains("Ю101"))
        assertTrue(groups.contains("ИТ101"))
    }

    @Test
    fun testLessonsScheduleCommonTimes() {
        val times = IUBIPLessonsSchedule.COMMON
        assertEquals(8, times.size)
        assertEquals(1, times.first().number)
        assertEquals("08:20", times.first().start.toString())
        assertEquals("09:50", times.first().end.toString())
        assertEquals("20:20", times.last().start.toString())
    }

    @Test
    fun testParseIUBIPNewsDetail() {
        val sampleHtml = """
            <html>
                <body>
                    <h1 class="detail-news__title">Студенты ИУБиП победили в хакатоне</h1>
                    <div class="detail-news__date">14.09.2026</div>
                    <div class="detail-news__text">
                        <p>В Ростове-на-Дону завершился масштабный IT-хакатон.</p>
                        <p>Команда института заняла первое место.</p>
                    </div>
                    <div class="univer-gallery__sliders">
                        <img src="/upload/photo1.jpg" />
                        <img src="/upload/photo2.jpg" />
                    </div>
                </body>
            </html>
        """.trimIndent()

        val doc = com.fleeksoft.ksoup.Ksoup.parse(sampleHtml)
        val title = doc.selectFirst(".detail-news__title")?.text()?.trim() ?: ""
        val textContainer = doc.selectFirst(".detail-news__text")
        val paragraphs = textContainer?.select("p")?.map { it.text().trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        val galleryImgs = doc.select(".univer-gallery__sliders img")
            .map { it.attr("src").trim() }
            .filter { it.isNotEmpty() }

        assertEquals("Студенты ИУБиП победили в хакатоне", title)
        assertEquals(2, paragraphs.size)
        assertEquals("В Ростове-на-Дону завершился масштабный IT-хакатон.", paragraphs[0])
        assertEquals(2, galleryImgs.size)
    }
}
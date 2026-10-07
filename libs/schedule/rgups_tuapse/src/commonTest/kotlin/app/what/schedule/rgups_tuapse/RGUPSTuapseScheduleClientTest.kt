package app.what.schedule.rgups_tuapse

import app.what.schedule.core.models.LessonTypeDto
import app.what.schedule.rgups_tuapse.models.TuapseTimetableResponse
import io.ktor.client.HttpClient
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RGUPSTuapseScheduleClientTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun testParseTimetableResponse() {
        val sampleJson = """
        {
            "from": "20260901",
            "to": "20260930",
            "times": ["08:20-09:50", "10:05-11:35"],
            "types": ["лек", "пр"],
            "subjects": ["Физика", "Математика"],
            "audiences": ["101", "102"],
            "teachers": {"1654": "Иванов И.И."},
            "groups": [{"id": 1081, "n": "СТ-3-006", "c": 3, "f": "СПО"}],
            "rows": [
                [20260921, 1, 0, 0, 0, 1654, 1081, 0]
            ]
        }
        """.trimIndent()

        val parsed = json.decodeFromString<TuapseTimetableResponse>(sampleJson)
        assertEquals(1, parsed.groups.size)
        assertEquals("СТ-3-006", parsed.groups[0].n)
        assertEquals(1, parsed.teachers.size)
        assertEquals("Иванов И.И.", parsed.teachers["1654"])
        assertEquals(1, parsed.rows.size)
    }

    @Test
    fun testLessonTypeParsing() {
        assertEquals(LessonTypeDto.LECTURE, LessonTypeDto.fromString("лек"))
        assertEquals(LessonTypeDto.PRACTICE, LessonTypeDto.fromString("пр"))
        assertEquals(LessonTypeDto.LABORATORY, LessonTypeDto.fromString("лаб"))
    }

    @Test
    fun testDoubleDashAndLinkPreservedNotList() {
        val html = """
        <div class="com-content-article__body">
            <p style="text-align: center;">
                Поздравляем наших выпускников и желаем им больших успехов на профессиональном пути!<br><br>
                --<a href="https://t.me/rgupsisxodnik" target="_blank" rel="noopener noreferrer" data-link-id="432">https://t.me/rgupsisxodnik</a>&nbsp;- ссылка для скачивания<br>фотографий с выпускного
            </p>
        </div>
        """.trimIndent()

        val client = RGUPSTuapseNewsClient()
        val detail = client.parseNewsDetail(html, "https://rgups-tuapse.ru/news/test/")

        assertEquals(3, detail.contentBlocks.size)
        assertTrue(detail.contentBlocks[0] is app.what.schedule.core.models.NewContentBlockDto.Text)
        assertTrue(detail.contentBlocks[1] is app.what.schedule.core.models.NewContentBlockDto.Text)
        assertTrue(detail.contentBlocks[2] is app.what.schedule.core.models.NewContentBlockDto.Text)
        val linkBlock = (detail.contentBlocks[1] as app.what.schedule.core.models.NewContentBlockDto.Text).html
        assertTrue(linkBlock.contains("https://t.me/rgupsisxodnik"))
        assertTrue(linkBlock.contains("ссылка для скачивания"))
        val thirdBlock = (detail.contentBlocks[2] as app.what.schedule.core.models.NewContentBlockDto.Text).html
        assertTrue(thirdBlock.contains("фотографий с выпускного"))
        kotlin.test.assertFalse(detail.contentBlocks.any { it is app.what.schedule.core.models.NewContentBlockDto.UnsortedList })
    }

    @Test
    fun testRepeatedTitleRemovedFromContent() {
        val html = """
        <div class="item-page">
            <h2>Торжественная церемония вручения дипломов</h2>
            <div class="com-content-article__body">
                <p>Торжественная церемония вручения дипломов</p>
                <p>30 июня 2025 года в филиале РГУПС города Туапсе состоялось долгожданное событие.</p>
            </div>
        </div>
        """.trimIndent()

        val client = RGUPSTuapseNewsClient()
        val detail = client.parseNewsDetail(html, "https://rgups-tuapse.ru/news/test/")

        assertEquals(1, detail.contentBlocks.size)
        assertTrue(detail.contentBlocks[0] is app.what.schedule.core.models.NewContentBlockDto.Text)
        val text = (detail.contentBlocks[0] as app.what.schedule.core.models.NewContentBlockDto.Text).html
        assertTrue(text.contains("30 июня 2025 года"))
        kotlin.test.assertFalse(text.contains("Торжественная церемония вручения дипломов"))
    }
}

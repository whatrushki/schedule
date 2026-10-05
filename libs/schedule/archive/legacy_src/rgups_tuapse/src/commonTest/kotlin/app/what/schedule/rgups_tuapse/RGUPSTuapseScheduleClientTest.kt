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
}

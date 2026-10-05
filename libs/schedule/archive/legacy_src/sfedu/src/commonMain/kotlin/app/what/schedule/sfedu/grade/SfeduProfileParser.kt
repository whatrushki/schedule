package app.what.schedule.sfedu.grade

import com.fleeksoft.ksoup.Ksoup

data class SfeduParsedProfile(
    val fullName: String,
    val faculty: String? = null,
    val direction: String? = null,
    val degree: String? = null,
    val course: Int? = null,
    val group: String? = null,
    val email: String? = null
)

object SfeduProfileParser {
    fun parse(html: String): SfeduParsedProfile? {
        return try {
            val doc = Ksoup.parse(html)
            val username = doc.select(".username").text().trim().ifBlank {
                doc.select("#username").text().trim()
            }

            var faculty: String? = null
            var direction: String? = null
            var degree: String? = null
            var course: Int? = null
            var group: String? = null
            var email: String? = null

            val rows = doc.select(".clearFix, #profileInfo > div")
            for (row in rows) {
                val label = row.select(".label").text().trim()
                val content = row.select(".content").text().trim()
                if (label.isBlank() || content.isBlank()) continue

                when {
                    label.contains("Подразделение", ignoreCase = true) -> faculty = content
                    label.contains("Направление", ignoreCase = true) -> direction = content
                    label.contains("E-Mail", ignoreCase = true) -> email = content
                    label.contains("Курс", ignoreCase = true) || label.contains("группа", ignoreCase = true) -> {
                        // "Бакалавриат, 2 курс, 5 группа"
                        val parts = content.split(",").map { it.trim() }
                        for (part in parts) {
                            when {
                                part.contains("курс", ignoreCase = true) -> {
                                    course = part.filter { it.isDigit() }.toIntOrNull()
                                }
                                part.contains("группа", ignoreCase = true) -> {
                                    group = part
                                }
                                part.contains("Бакалавр", ignoreCase = true) ||
                                part.contains("Магистр", ignoreCase = true) ||
                                part.contains("Специал", ignoreCase = true) -> {
                                    degree = part
                                }
                            }
                        }
                    }
                }
            }

            if (username.isBlank() && faculty == null && direction == null) return null

            SfeduParsedProfile(
                fullName = username,
                faculty = faculty,
                direction = direction,
                degree = degree,
                course = course,
                group = group,
                email = email
            )
        } catch (_: Exception) {
            null
        }
    }
}

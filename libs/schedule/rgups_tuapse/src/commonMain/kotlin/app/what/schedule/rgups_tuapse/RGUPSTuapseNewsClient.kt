package app.what.schedule.rgups_tuapse

import app.what.schedule.core.clients.NewsClient
import app.what.schedule.core.models.NewContentBlockDto
import app.what.schedule.core.models.NewDetailDto
import app.what.schedule.core.models.NewListItemDto
import com.fleeksoft.ksoup.Ksoup
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class RGUPSTuapseNewsClient(
    private val client: HttpClient,
    private val baseUrl: String = "https://rgups-tuapse.ru",
    private val log: ((String) -> Unit)? = null
) : NewsClient {

    override suspend fun getNews(page: Int): List<NewListItemDto> {
        return try {
            val start = (page.coerceAtLeast(1) - 1) * 6
            val url = "$baseUrl/?start=$start"
            val html = client.get(url).bodyAsText()
            val doc = Ksoup.parse(html)

            val items = doc.select(".blog-item, .com-content-category-blog__item")
            items.mapNotNull { item ->
                val titleElem = item.selectFirst("h2 a, h3 a, .item-title a") ?: return@mapNotNull null
                val title = titleElem.text().trim()
                if (title.isBlank()) return@mapNotNull null
                val href = titleElem.attr("href").trim()
                val id = href

                val imgElem = item.selectFirst("img")
                val imgSrc = imgElem?.attr("src")?.trim()?.ifEmpty { null }
                val imageUrl = imgSrc?.let { if (it.startsWith("http")) it else "$baseUrl$it" }

                val dateElem = item.selectFirst(".published, time, dd.published")
                val date = parseDate(dateElem?.text().orEmpty())

                val descElem = item.selectFirst("p, .intro")
                val desc = descElem?.text()?.trim().orEmpty()

                NewListItemDto(
                    id = id,
                    title = title,
                    description = desc,
                    date = date,
                    imageUrl = imageUrl,
                    sourceUrl = if (href.startsWith("http")) href else "$baseUrl$href"
                )
            }
        } catch (e: Exception) {
            log?.invoke("Error fetching RGUPSTuapse news: ${e.message}")
            emptyList()
        }
    }

    override suspend fun getNewDetail(id: String): NewDetailDto {
        val url = if (id.startsWith("http")) id else "$baseUrl$id"
        val html = client.get(url).bodyAsText()
        val doc = Ksoup.parse(html)

        val title = doc.selectFirst("h1, h2.item-title")?.text()?.trim() ?: ""
        val bodyElem = doc.selectFirst(".com-content-article__body, .item-page, article")

        val dateElem = doc.selectFirst(".published, time, dd.published")
        val date = parseDate(dateElem?.text().orEmpty())

        val images = bodyElem?.select("img")?.mapNotNull { img ->
            val src = img.attr("src").trim().ifEmpty { null } ?: return@mapNotNull null
            if (src.startsWith("http")) src else "$baseUrl$src"
        } ?: emptyList()

        val fullText = bodyElem?.select("p")?.joinToString("\n\n") { it.text().trim() } ?: ""

        val blocks = mutableListOf<NewContentBlockDto>()
        bodyElem?.children()?.forEach { child ->
            when (child.tagName().lowercase()) {
                "p" -> {
                    val pText = child.text().trim()
                    if (pText.isNotEmpty()) blocks.add(NewContentBlockDto.Text(pText))
                }
                "img" -> {
                    val src = child.attr("src").trim()
                    if (src.isNotEmpty()) {
                        val fullSrc = if (src.startsWith("http")) src else "$baseUrl$src"
                        blocks.add(NewContentBlockDto.Image(fullSrc))
                    }
                }
            }
        }
        if (blocks.isEmpty() && fullText.isNotEmpty()) {
            blocks.add(NewContentBlockDto.Text(fullText))
        }

        return NewDetailDto(
            id = id,
            title = title,
            fullText = fullText,
            descriptionHtml = bodyElem?.html(),
            date = date,
            bannerUrl = images.firstOrNull(),
            images = images,
            sourceUrl = url,
            contentBlocks = blocks
        )
    }

    private fun parseDate(str: String): LocalDate {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        return try {
            val clean = str.replace("Опубликовано:", "").replace("Дата:", "").trim()
            val parts = clean.split(" ").filter { it.isNotBlank() }
            if (parts.size >= 3) {
                val day = parts[0].toIntOrNull() ?: today.dayOfMonth
                val month = parseMonth(parts[1])
                val year = parts[2].toIntOrNull() ?: today.year
                LocalDate(year, month, day)
            } else {
                today
            }
        } catch (_: Exception) {
            today
        }
    }

    private fun parseMonth(name: String): Int = when (name.lowercase().take(3)) {
        "янв" -> 1
        "фев" -> 2
        "мар" -> 3
        "апр" -> 4
        "май", "мая" -> 5
        "июн" -> 6
        "июл" -> 7
        "авг" -> 8
        "сен" -> 9
        "окт" -> 10
        "ноя" -> 11
        "дек" -> 12
        else -> 1
    }
}

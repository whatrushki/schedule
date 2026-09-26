package app.what.schedule.rgups

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

class RGUPSNewsClient(
    private val client: HttpClient,
    private val baseUrl: String = "https://www.rgups.ru",
    private val log: ((String) -> Unit)? = null
) : NewsClient {

    override suspend fun getNews(page: Int): List<NewListItemDto> {
        return try {
            val url = if (page <= 1) "$baseUrl/news/" else "$baseUrl/news/page$page/"
            val html = client.get(url).bodyAsText()
            val doc = Ksoup.parse(html)

            val items = doc.select(".news-item")
            items.mapNotNull { item ->
                val linkElem = item.selectFirst("a") ?: return@mapNotNull null
                val href = linkElem.attr("href").trim()
                if (href.isBlank()) return@mapNotNull null

                val title = item.selectFirst("a:not([class*='more']), h2, h3, .title")?.text()?.trim()
                    ?: linkElem.text().trim()
                if (title.isBlank()) return@mapNotNull null

                val imgElem = item.selectFirst("img")
                val imgSrc = imgElem?.attr("src")?.trim()?.ifEmpty { null }
                val imageUrl = imgSrc?.let { if (it.startsWith("http")) it else "$baseUrl$it" }

                val dateStr = item.selectFirst(".date, time")?.text()?.trim()
                    ?: Regex("\\b(\\d{2}\\.\\d{2}\\.\\d{4})\\b").find(item.text())?.value.orEmpty()
                val date = parseDate(dateStr)

                val desc = item.selectFirst("p, .desc, .text")?.text()?.trim().orEmpty()

                NewListItemDto(
                    id = href,
                    title = title,
                    description = desc,
                    date = date,
                    imageUrl = imageUrl,
                    sourceUrl = if (href.startsWith("http")) href else "$baseUrl$href"
                )
            }
        } catch (e: Exception) {
            log?.invoke("Error fetching RGUPS news: ${e.message}")
            emptyList()
        }
    }

    override suspend fun getNewDetail(id: String): NewDetailDto {
        val url = if (id.startsWith("http")) id else "$baseUrl$id"
        val html = client.get(url).bodyAsText()
        val doc = Ksoup.parse(html)

        val title = doc.selectFirst("h1, h2.title")?.text()?.trim() ?: ""
        val bodyElem = doc.selectFirst(".content, .news-detail, article, main")

        val dateStr = doc.selectFirst(".date, time")?.text()?.trim()
            ?: Regex("\\b(\\d{2}\\.\\d{2}\\.\\d{4})\\b").find(doc.text())?.value.orEmpty()
        val date = parseDate(dateStr)

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
            val parts = str.split(".").map { it.trim().toInt() }
            if (parts.size == 3) {
                LocalDate(parts[2], parts[1], parts[0])
            } else {
                today
            }
        } catch (_: Exception) {
            today
        }
    }
}

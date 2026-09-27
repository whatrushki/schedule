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
                val linkElem = item.selectFirst(".caption a, .thumbnail a, a[href*='/news/']") ?: return@mapNotNull null
                val href = linkElem.attr("href").trim()
                if (href.isBlank() || href == "/news/") return@mapNotNull null

                val imgElem = item.selectFirst(".thumbnail img, img")
                val imgSrc = imgElem?.attr("src")?.trim()?.ifEmpty { null }
                val imageUrl = imgSrc?.let { if (it.startsWith("http")) it else "$baseUrl$it" }

                val dateStr = item.selectFirst("time.news-item__time, time, .date")?.text()?.trim()
                    ?: Regex("""\b(\d{2}\.\d{2}\.\d{4})\b""").find(item.text())?.value.orEmpty()
                val date = parseDate(dateStr)

                // RGUPS news card does not have a separate title tag; text is in caption <p>
                val captionPs = item.select(".caption p")
                val textP = captionPs.firstOrNull { p ->
                    p.select("a").isEmpty() && !p.text().contains("Подробнее", ignoreCase = true)
                } ?: captionPs.firstOrNull { !it.text().contains("Подробнее", ignoreCase = true) }

                val desc = textP?.text()?.trim().orEmpty()
                if (desc.isBlank()) return@mapNotNull null

                NewListItemDto(
                    id = href,
                    title = desc,
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

        val title = doc.selectFirst("h1")?.text()?.trim()
            ?: doc.selectFirst("h2, .page-header h2")?.text()?.trim()
            ?: ""
        val bodyElem = doc.selectFirst("div.text, .container .row .col-md-12, .content, .news-detail, article, main")

        val dateStr = doc.selectFirst("time.publication-time, time, .date")?.text()?.trim()
            ?: Regex("""\b(\d{2}\.\d{2}\.\d{4})\b""").find(doc.text())?.value.orEmpty()
        val date = parseDate(dateStr)

        val allImages = mutableListOf<String>()
        val blocks = mutableListOf<NewContentBlockDto>()

        bodyElem?.children()?.forEach { child ->
            when (child.tagName().lowercase()) {
                "p" -> {
                    val imgs = child.select("img")
                    for (img in imgs) {
                        val src = img.attr("src").trim()
                        if (src.isNotEmpty()) {
                            val fullSrc = if (src.startsWith("http")) src else "$baseUrl$src"
                            blocks.add(NewContentBlockDto.Image(fullSrc))
                            allImages.add(fullSrc)
                        }
                    }
                    val textOnly = child.clone()
                    textOnly.select("img").remove()
                    val pText = textOnly.text().trim()
                    if (pText.isNotEmpty()) {
                        blocks.add(NewContentBlockDto.Text(pText))
                    }
                }
                "img" -> {
                    val src = child.attr("src").trim()
                    if (src.isNotEmpty()) {
                        val fullSrc = if (src.startsWith("http")) src else "$baseUrl$src"
                        blocks.add(NewContentBlockDto.Image(fullSrc))
                        allImages.add(fullSrc)
                    }
                }
                "h2", "h3", "h4" -> {
                    val text = child.text().trim()
                    if (text.isNotEmpty()) blocks.add(NewContentBlockDto.Subtitle(text))
                }
                "ul" -> {
                    val items = child.select("li").map { it.text().trim() }.filter { it.isNotEmpty() }
                    if (items.isNotEmpty()) blocks.add(NewContentBlockDto.UnsortedList(items))
                }
                "ol" -> {
                    val items = child.select("li").map { it.text().trim() }.filter { it.isNotEmpty() }
                    if (items.isNotEmpty()) blocks.add(NewContentBlockDto.SortedList(items))
                }
            }
        }

        val fullText = blocks.filterIsInstance<NewContentBlockDto.Text>().joinToString("\n\n") { it.html }
            .ifEmpty { bodyElem?.select("p")?.joinToString("\n\n") { it.text().trim() } ?: "" }

        if (blocks.isEmpty() && fullText.isNotEmpty()) {
            blocks.add(NewContentBlockDto.Text(fullText))
        }

        return NewDetailDto(
            id = id,
            title = title,
            fullText = fullText,
            descriptionHtml = bodyElem?.html(),
            date = date,
            bannerUrl = allImages.firstOrNull(),
            images = allImages,
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

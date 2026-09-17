package app.what.schedule.sfedu

import app.what.schedule.core.clients.NewsClient
import app.what.schedule.core.models.NewContentBlockDto
import app.what.schedule.core.models.NewDetailDto
import app.what.schedule.core.models.NewListItemDto
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class SFEDUNewsClient(
    private val client: HttpClient,
    private val baseUrl: String = "https://mmcs.sfedu.ru",
    private val log: ((String) -> Unit)? = null
) : NewsClient {

    private data class CachedNewsItem(
        val id: String,
        val title: String,
        val description: String,
        val date: LocalDate,
        val bannerUrl: String?,
        val sourceUrl: String,
        val hasDetailPage: Boolean,
        val detailUrl: String?,
        val contentBlocks: List<NewContentBlockDto>,
        val descriptionHtml: String?
    )

    private val itemsCache = mutableMapOf<String, CachedNewsItem>()

    private fun formatUrl(url: String): String = when {
        url.isEmpty() -> ""
        url.startsWith("http://") || url.startsWith("https://") -> url
        url.startsWith("//") -> "https:$url"
        url.startsWith("/") -> "$baseUrl$url"
        else -> "$baseUrl/$url"
    }

    override suspend fun getNews(page: Int): List<NewListItemDto> {
        return try {
            val start = (page.coerceAtLeast(1) - 1) * 9
            val url = if (start == 0) baseUrl else "$baseUrl/?start=$start"
            val response = client.get(url).bodyAsText()
            val document = Ksoup.parse(response)

            val items = document.select(".news_item_f, .item, .items-row")
            if (items.isEmpty()) {
                val fallbackItems = document.select("article, .blog .item-page")
                if (fallbackItems.isEmpty()) return emptyList()
            }

            items.mapNotNull { element ->
                try {
                    val textContainer = element.selectFirst(".newsitem_text") ?: element
                    val titleElement = element.selectFirst(".article_title, h2, h1")
                    val rawTitle = titleElement?.text()?.trim()

                    val title = if (!rawTitle.isNullOrBlank()) {
                        rawTitle
                    } else {
                        val firstP = textContainer.selectFirst("p")?.text()?.trim() ?: ""
                        if (firstP.isNotBlank()) {
                            firstP.split(".", "\n", ":").firstOrNull { it.isNotBlank() }?.trim() ?: "Объявление"
                        } else {
                            "Объявление"
                        }
                    }

                    // Check for readmore / detail button
                    val readmoreTag = element.select("a.readon, a.readmore, .readmore a, a[class*='readmore']").firstOrNull()
                        ?: element.select("a").firstOrNull { a ->
                            val text = a.text().lowercase()
                            text.contains("подробнее") || text.contains("читать")
                        }

                    val hasDetailPage = readmoreTag != null
                    val detailHref = readmoreTag?.attr("href")?.trim()
                    val detailUrl = if (!detailHref.isNullOrBlank()) formatUrl(detailHref) else null

                    val id = if (!detailHref.isNullOrBlank()) {
                        detailHref.removePrefix("/").ifBlank { detailUrl ?: title }
                    } else {
                        "sfedu_${title.hashCode()}_${element.elementSiblingIndex()}"
                    }

                    val dateText = element.select(".createdate, .create-date, time").firstOrNull()?.text()?.trim()
                    val date = parseDate(dateText)

                    val imgTag = element.select("img").firstOrNull()
                    val bannerUrl = imgTag?.attr("src")?.takeIf { it.isNotBlank() }?.let { formatUrl(it) }

                    val descText = textContainer.text().trim()
                    val descHtml = textContainer.html().trim()

                    val listContentBlocks = parseElementBlocks(textContainer)

                    val cached = CachedNewsItem(
                        id = id,
                        title = title,
                        description = descText,
                        date = date,
                        bannerUrl = bannerUrl,
                        sourceUrl = detailUrl ?: baseUrl,
                        hasDetailPage = hasDetailPage,
                        detailUrl = detailUrl,
                        contentBlocks = listContentBlocks,
                        descriptionHtml = descHtml
                    )
                    itemsCache[id] = cached

                    NewListItemDto(
                        id = id,
                        title = title,
                        description = descText,
                        date = date,
                        imageUrl = bannerUrl,
                        sourceUrl = detailUrl ?: baseUrl
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            log?.invoke("Error fetching SFEDU news: ${e.message}")
            emptyList()
        }
    }

    override suspend fun getNewDetail(id: String): NewDetailDto {
        val cached = itemsCache[id]
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

        // If this news item doesn't have a separate detail page, return the full list content directly!
        if (cached != null && !cached.hasDetailPage) {
            return NewDetailDto(
                id = cached.id,
                title = cached.title,
                fullText = cached.description,
                descriptionHtml = cached.descriptionHtml,
                date = cached.date,
                bannerUrl = cached.bannerUrl,
                images = cached.bannerUrl?.let { listOf(it) } ?: emptyList(),
                sourceUrl = cached.sourceUrl,
                contentBlocks = cached.contentBlocks
            )
        }

        val targetUrl = cached?.detailUrl ?: formatUrl(id)
        return try {
            val response = client.get(targetUrl).bodyAsText()
            val document = Ksoup.parse(response)

            val container = document.select(".news_item_a, .item-page, article, #mainbody").firstOrNull()
                ?: document.body()

            val title = container.select(".article_title, h2, h1").firstOrNull()?.text()?.trim()
                ?: cached?.title
                ?: "Новость"

            val dateText = container.select(".createdate, time").firstOrNull()?.text()?.trim()
            val date = parseDate(dateText).takeIf { dateText != null } ?: cached?.date ?: today

            val imgTags = container.select("img")
            val images = imgTags.mapNotNull { it.attr("src").takeIf { src -> src.isNotBlank() }?.let { src -> formatUrl(src) } }
            val bannerUrl = images.firstOrNull() ?: cached?.bannerUrl

            val contentBlocks = parseElementBlocks(container)

            NewDetailDto(
                id = id,
                title = title,
                fullText = container.text().trim(),
                descriptionHtml = container.html(),
                date = date,
                bannerUrl = bannerUrl,
                images = images,
                sourceUrl = targetUrl,
                contentBlocks = if (contentBlocks.isNotEmpty()) contentBlocks else cached?.contentBlocks ?: emptyList()
            )
        } catch (e: Exception) {
            log?.invoke("Error fetching SFEDU news detail for $id: ${e.message}")
            if (cached != null) {
                // Fallback to cached list item content
                NewDetailDto(
                    id = cached.id,
                    title = cached.title,
                    fullText = cached.description,
                    descriptionHtml = cached.descriptionHtml,
                    date = cached.date,
                    bannerUrl = cached.bannerUrl,
                    images = cached.bannerUrl?.let { listOf(it) } ?: emptyList(),
                    sourceUrl = cached.sourceUrl,
                    contentBlocks = cached.contentBlocks
                )
            } else {
                NewDetailDto(
                    id = id,
                    title = "Ошибка загрузки",
                    fullText = "",
                    date = today,
                    sourceUrl = targetUrl
                )
            }
        }
    }

    private fun parseElementBlocks(container: Element): List<NewContentBlockDto> {
        val contentBlocks = mutableListOf<NewContentBlockDto>()
        val elements = container.select("p, h3, h4, img, blockquote, ul, ol")

        for (el in elements) {
            if (el.tagName() == "img") {
                val src = el.attr("src")
                if (src.isNotBlank()) {
                    contentBlocks.add(NewContentBlockDto.Image(formatUrl(src)))
                }
            } else if (el.tagName() in listOf("h3", "h4")) {
                val text = el.text().trim()
                if (text.isNotBlank()) {
                    contentBlocks.add(NewContentBlockDto.Subtitle(text))
                }
            } else if (el.tagName() == "ul") {
                val items = el.select("li").map { it.text().trim() }.filter { it.isNotBlank() }
                if (items.isNotEmpty()) {
                    contentBlocks.add(NewContentBlockDto.UnsortedList(items))
                }
            } else if (el.tagName() == "ol") {
                val items = el.select("li").map { it.text().trim() }.filter { it.isNotBlank() }
                if (items.isNotEmpty()) {
                    contentBlocks.add(NewContentBlockDto.SortedList(items))
                }
            } else {
                // p or other text container: preserve HTML so <a> links are rendered clickable!
                val html = el.html().trim()
                if (html.isNotBlank() && el.text().trim().isNotBlank()) {
                    contentBlocks.add(NewContentBlockDto.Text(html))
                }
            }
        }
        return contentBlocks
    }

    private fun parseDate(raw: String?): LocalDate {
        if (raw.isNullOrBlank()) {
            return Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        }
        return try {
            val parts = raw.split(".").mapNotNull { it.trim().toIntOrNull() }
            if (parts.size >= 3) {
                LocalDate(parts[2], parts[1], parts[0])
            } else {
                Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            }
        } catch (_: Exception) {
            Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        }
    }
}

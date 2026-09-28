package app.what.schedule.tvgu

import app.what.schedule.core.clients.NewsClient
import app.what.schedule.core.models.AuthorInfoDto
import app.what.schedule.core.models.NewContentBlockDto
import app.what.schedule.core.models.NewDetailDto
import app.what.schedule.core.models.NewListItemDto
import app.what.schedule.core.utils.parseMonth
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class TvGUNewsClient(
    private val client: HttpClient,
    private val baseUrl: String = "https://tversu.ru",
    private val log: ((String) -> Unit)? = null
) : NewsClient {

    private fun formatUrl(url: String): String = when {
        url.isEmpty() -> ""
        url.startsWith("http://") || url.startsWith("https://") -> url
        url.startsWith("//") -> "https:$url"
        url.startsWith("/") -> "$baseUrl$url"
        else -> "$baseUrl/$url"
    }

    override suspend fun getNews(page: Int): List<NewListItemDto> {
        val url = if (page <= 1) "$baseUrl/news" else "$baseUrl/news?page=$page"
        return try {
            val response = client.get(url) {
                header(HttpHeaders.UserAgent, USER_AGENT)
                header(HttpHeaders.Accept, "text/html,application/xhtml+xml")
            }.bodyAsText()

            val document = Ksoup.parse(response)
            val items = document.getElementsByClass("tvsu-news-item")

            items.mapNotNull { el ->
                try {
                    val href = el.attr("href").ifEmpty {
                        el.getElementsByTag("a").firstOrNull()?.attr("href").orEmpty()
                    }
                    if (href.isEmpty()) return@mapNotNull null

                    val id = href.trim('/').split("/").lastOrNull() ?: href
                    val title = el.getElementsByClass("tvsu-news-item__title").firstOrNull()?.text()?.trim().orEmpty()
                    if (title.isEmpty()) return@mapNotNull null

                    val style = el.attr("style")
                    val bgImage = Regex("""url\(['"]?([^'")]+)['"]?\)""").find(style)?.groupValues?.get(1)
                    val bannerUrl = bgImage?.let { formatUrl(it) }

                    val dateText = el.getElementsByClass("tvsu-news-item__date").firstOrNull()?.text()?.trim().orEmpty()
                    val date = parseTvGuDate(dateText)

                    val itemTags = el.select(".tvsu-news-tags a, .tvsu-news-item__tags a, [class*='tag'] a")
                        .map { it.text().trim() }
                        .filter { it.isNotEmpty() }
                        .distinct()

                    NewListItemDto(
                        id = id,
                        title = title,
                        description = "",
                        date = date,
                        imageUrl = bannerUrl,
                        sourceUrl = formatUrl(href),
                        tags = itemTags
                    )
                } catch (_: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            log?.invoke("TvGU getNews error: ${e.message}")
            emptyList()
        }
    }

    override suspend fun getNewDetail(id: String): NewDetailDto {
        val url = if (id.startsWith("http")) id else "$baseUrl/news/$id"
        return try {
            val response = client.get(url) {
                header(HttpHeaders.UserAgent, USER_AGENT)
                header(HttpHeaders.Accept, "text/html,application/xhtml+xml")
            }.bodyAsText()

            val document = Ksoup.parse(response)
            val title = document.selectFirst(".tvsu-news-show__title, .tvsu-news_show__title, h1")?.text()?.trim().orEmpty()
            val dateText = document.selectFirst(".tvsu-news-show__date, .tvsu-news_show__date, .tvsu-news-item__date, .date")?.text()?.trim().orEmpty()
            val date = parseTvGuDate(dateText)

            val tags = document.select(".tvsu-news_show__tags a, .tvsu-news-show__tags a, .tvsu-news__tags a")
                .map { it.text().trim() }
                .filter { it.isNotEmpty() }
                .distinct()

            val contentElement = document.selectFirst(".tvsu-ck-content")
                ?: document.getElementsByClass("tvsu-ck-content").firstOrNull()
                ?: document.getElementsByClass("tvsu-news__content").firstOrNull()
                ?: document.getElementsByClass("tvsu-article__content").firstOrNull()
                ?: document.getElementsByTag("article").firstOrNull()
                ?: document.body()

            val (blocks, images, leadImage) = parseContent(contentElement)

            NewDetailDto(
                id = id,
                title = title,
                fullText = contentElement.text().trim(),
                date = date,
                bannerUrl = leadImage ?: images.firstOrNull(),
                images = images,
                sourceUrl = url,
                contentBlocks = blocks,
                tags = tags
            )
        } catch (e: Exception) {
            log?.invoke("TvGU getNewDetail error for $id: ${e.message}")
            NewDetailDto(
                id = id,
                title = "",
                fullText = "",
                date = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
                bannerUrl = null,
                images = emptyList(),
                sourceUrl = url,
                contentBlocks = emptyList(),
                tags = emptyList()
            )
        }
    }

    private fun parseContent(container: Element): Triple<List<NewContentBlockDto>, List<String>, String?> {
        val blocks = mutableListOf<NewContentBlockDto>()
        val allImages = mutableListOf<String>()
        var leadImage: String? = null
        var isFirstBlock = true

        val elements = container.select("p, h1, h2, h3, h4, h5, h6, figure, img, blockquote, ul, ol, iframe, table")
        val topLevelElements = elements.filter { el ->
            el.parents().none { parent -> parent in elements }
        }

        for (el in topLevelElements) {
            when {
                el.tagName() == "figure" -> {
                    val figureImages = el.getElementsByTag("img")
                    for ((imgIdx, img) in figureImages.withIndex()) {
                        val src = img.attr("src").ifEmpty { img.attr("data-src") }.trim()
                        if (src.isNotEmpty()) {
                            val imgUrl = formatUrl(src)
                            allImages.add(imgUrl)
                            if (isFirstBlock && imgIdx == 0) {
                                leadImage = imgUrl
                            } else {
                                blocks.add(NewContentBlockDto.Image(imgUrl))
                            }
                        }
                    }
                    val caption = el.getElementsByTag("figcaption").firstOrNull()?.text()?.trim().orEmpty()
                    if (caption.isNotEmpty()) {
                        blocks.add(NewContentBlockDto.Text(caption))
                    }
                    isFirstBlock = false
                }

                el.tagName() == "img" -> {
                    val src = el.attr("src").ifEmpty { el.attr("data-src") }.trim()
                    if (src.isNotEmpty()) {
                        val imgUrl = formatUrl(src)
                        allImages.add(imgUrl)
                        if (isFirstBlock) {
                            leadImage = imgUrl
                        } else {
                            blocks.add(NewContentBlockDto.Image(imgUrl))
                        }
                    }
                    isFirstBlock = false
                }

                el.tagName() == "p" -> {
                    val nestedImgs = el.getElementsByTag("img")
                    val textOnly = el.clone()
                    textOnly.getElementsByTag("img").remove()
                    val hasText = textOnly.text().trim().isNotEmpty()

                    for ((imgIdx, img) in nestedImgs.withIndex()) {
                        val src = img.attr("src").ifEmpty { img.attr("data-src") }.trim()
                        if (src.isNotEmpty()) {
                            val imgUrl = formatUrl(src)
                            allImages.add(imgUrl)
                            if (isFirstBlock && !hasText && imgIdx == 0) {
                                leadImage = imgUrl
                            } else {
                                blocks.add(NewContentBlockDto.Image(imgUrl))
                            }
                        }
                    }

                    // Ensure all <a> links have absolute URLs
                    for (a in textOnly.getElementsByTag("a")) {
                        val href = a.attr("href").trim()
                        if (href.isNotEmpty()) {
                            a.attr("href", formatUrl(href))
                        }
                    }

                    val html = textOnly.html().trim()
                        .replace(Regex("""^(&nbsp;|\s|\u00A0)+"""), "")
                        .replace(Regex("""(&nbsp;|\s|\u00A0)+$"""), "")
                    if (html.isNotEmpty() && hasText) {
                        blocks.add(NewContentBlockDto.Text(html))
                    }
                    isFirstBlock = false
                }

                el.tagName() in setOf("h1", "h2", "h3", "h4", "h5", "h6") -> {
                    val heading = el.text().trim()
                    if (heading.isNotEmpty()) {
                        blocks.add(NewContentBlockDto.Subtitle(heading))
                    }
                }

                el.tagName() == "ul" -> {
                    val items = el.getElementsByTag("li").map { it.text().trim() }.filter { it.isNotEmpty() }
                    if (items.isNotEmpty()) {
                        blocks.add(NewContentBlockDto.UnsortedList(items))
                    }
                }

                el.tagName() == "ol" -> {
                    val items = el.getElementsByTag("li").map { it.text().trim() }.filter { it.isNotEmpty() }
                    if (items.isNotEmpty()) {
                        blocks.add(NewContentBlockDto.SortedList(items))
                    }
                }

                el.tagName() == "blockquote" -> {
                    val quote = el.text().trim()
                    if (quote.isNotEmpty()) {
                        blocks.add(NewContentBlockDto.Quote(author = AuthorInfoDto(), text = quote))
                    }
                }

                el.tagName() == "iframe" || el.getElementsByTag("iframe").isNotEmpty() -> {
                    val iframe = if (el.tagName() == "iframe") el else el.getElementsByTag("iframe").first()
                    val src = iframe?.attr("src").orEmpty().trim()
                    if (src.isNotEmpty()) {
                        blocks.add(NewContentBlockDto.VideoVK(src))
                    }
                }

                el.tagName() == "table" -> {
                    val text = el.text().trim()
                    if (text.isNotEmpty()) {
                        blocks.add(NewContentBlockDto.Text(text))
                    }
                }
            }
        }

        return Triple(blocks, allImages, leadImage)
    }

    private fun parseTvGuDate(text: String): LocalDate {
        return try {
            val parts = text.split(" ")
            if (parts.size >= 3) {
                val day = parts[0].toIntOrNull() ?: 1
                val month = parseMonth(parts[1])
                val year = parts[2].toIntOrNull() ?: Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year
                LocalDate(year, month, day)
            } else {
                Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            }
        } catch (_: Exception) {
            Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        }
    }

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
    }
}

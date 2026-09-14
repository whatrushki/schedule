package app.what.schedule.iubip

import app.what.schedule.core.clients.NewsClient
import app.what.schedule.core.models.NewContentBlockDto
import app.what.schedule.core.models.NewDetailDto
import app.what.schedule.core.models.NewListItemDto
import app.what.schedule.core.utils.parseMonth
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

class IUBIPNewsClient(
    private val client: HttpClient,
    private val baseUrl: String = "https://iubip.ru",
    private val log: ((String) -> Unit)? = null
) : NewsClient {

    private fun formatImageUrl(url: String): String = when {
        url.isEmpty() -> ""
        url.startsWith("http") -> url
        url.startsWith("//") -> "https:$url"
        url.startsWith("/") -> "$baseUrl$url"
        else -> "$baseUrl/$url"
    }

    override suspend fun getNews(page: Int): List<NewListItemDto> {
        return try {
            val response = client.get("$baseUrl/news/?PAGEN_1=$page").bodyAsText()
            val document = Ksoup.parse(response)
            val rawData = document.getElementsByClass("news__item")

            rawData.mapNotNull {
                try {
                    val anchor = it.getElementsByTag("a").firstOrNull() ?: return@mapNotNull null
                    val url = anchor.attr("href")
                    val id = url.trim('/').split("/").lastOrNull() ?: url
                    val style = it.getElementsByClass("news__item-image").attr("style")
                    val bannerUrl = Regex("""url\(['"]?([^'")]+)['"]?\)""").find(style)?.groupValues?.get(1)?.let { path ->
                        formatImageUrl(path)
                    }

                    val title = it.getElementsByClass("news__item-name").firstOrNull()?.text()?.trim().orEmpty()
                    val description = it.getElementsByClass("news__item-text").firstOrNull()?.text()?.trim().orEmpty()
                    val dateText = it.getElementsByClass("news__item-date").text()
                    val date = try {
                        val tmp = dateText.split(" |").first().split(" ")
                        LocalDate(tmp[2].toInt(), parseMonth(tmp[1]), tmp[0].toInt())
                    } catch (_: Exception) {
                        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
                    }

                    NewListItemDto(
                        id = id,
                        title = title,
                        description = description,
                        date = date,
                        imageUrl = bannerUrl,
                        sourceUrl = if (url.startsWith("http")) url else baseUrl + url
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            log?.invoke("Ошибка получения новостей ИУБиП: ${e.message}")
            emptyList()
        }
    }

    override suspend fun getNewDetail(id: String): NewDetailDto {
        val url = "$baseUrl/news/$id/"
        return try {
            val response = client.get(url).bodyAsText()
            val document = Ksoup.parse(response)

            val title = document.getElementsByTag("h1").firstOrNull()?.text()?.trim().orEmpty()
            val dateText = document.getElementsByClass("head-block__content-right_date").firstOrNull()?.text()?.trim().orEmpty()
            val date = try {
                val tmp = dateText.split(" |").first().split(" ")
                if (tmp.size >= 3) {
                    LocalDate(tmp[2].toInt(), parseMonth(tmp[1]), tmp[0].toInt())
                } else {
                    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
                }
            } catch (_: Exception) {
                Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            }

            val contentElement = document.getElementsByClass("content-block__detail-news").firstOrNull()
                ?: document.getElementsByClass("content-block").firstOrNull()

            val (contentBlocks, parsedImages) = if (contentElement != null) {
                parseContent(contentElement)
            } else {
                Pair(emptyList(), emptyList())
            }

            val bannerUrl = parsedImages.firstOrNull()

            NewDetailDto(
                id = id,
                title = title,
                fullText = contentElement?.text()?.trim().orEmpty(),
                date = date,
                bannerUrl = bannerUrl,
                images = parsedImages,
                sourceUrl = url,
                contentBlocks = contentBlocks
            )
        } catch (e: Exception) {
            log?.invoke("Ошибка получения новости ИУБиП $id: ${e.message}")
            NewDetailDto(
                id = id,
                title = "",
                fullText = "",
                date = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
                sourceUrl = url
            )
        }
    }

    private fun parseContent(contentElement: Element): Pair<List<NewContentBlockDto>, List<String>> {
        val blocks = mutableListOf<NewContentBlockDto>()
        val allImages = mutableListOf<String>()

        for (child in contentElement.children()) {
            when {
                child.hasClass("detail-news__text") -> {
                    val rawHtml = child.html()
                    val paragraphs = rawHtml.split(Regex("""(?:<br\s*/?>\s*){2,}"""))
                    for (p in paragraphs) {
                        val cleanHtml = p.trim()
                        if (cleanHtml.isNotBlank()) {
                            blocks.add(NewContentBlockDto.Text(cleanHtml))
                        }
                    }
                }

                child.hasClass("univer-gallery__sliders") || child.getElementsByClass("univer-gallery__sliders").isNotEmpty() -> {
                    val galleryImgs = child.getElementsByClass("univer-gallery__sliders-top-item").mapNotNull { item ->
                        item.getElementsByTag("img").firstOrNull()?.attr("src")?.takeIf { it.isNotBlank() }?.let { formatImageUrl(it) }
                    }.ifEmpty {
                        child.getElementsByTag("img").mapNotNull { img ->
                            img.attr("src").takeIf { it.isNotBlank() }?.let { formatImageUrl(it) }
                        }
                    }
                    if (galleryImgs.isNotEmpty()) {
                        blocks.add(NewContentBlockDto.ImageCarousel(galleryImgs))
                        allImages.addAll(galleryImgs)
                    }
                }

                child.tagName() == "p" && child.getElementsByTag("img").isNotEmpty() -> {
                    child.getElementsByTag("img").forEach { img ->
                        val src = img.attr("src")
                        if (src.isNotBlank()) {
                            val formatted = formatImageUrl(src)
                            blocks.add(NewContentBlockDto.Image(formatted))
                            allImages.add(formatted)
                        }
                    }
                    val textOnly = child.clone()
                    textOnly.getElementsByTag("img").remove()
                    val html = textOnly.html().trim()
                    if (html.isNotBlank() && textOnly.text().trim().isNotBlank()) {
                        blocks.add(NewContentBlockDto.Text(html))
                    }
                }

                child.tagName() == "p" -> {
                    val html = child.html().trim()
                    if (html.isNotBlank() && child.text().trim().isNotBlank()) {
                        blocks.add(NewContentBlockDto.Text(html))
                    }
                }

                child.tagName() in setOf("h2", "h3", "h4") -> {
                    val text = child.text().trim()
                    if (text.isNotBlank()) blocks.add(NewContentBlockDto.Subtitle(text))
                }

                child.tagName() == "ul" -> {
                    val items = child.getElementsByTag("li").map { it.text().trim() }.filter { it.isNotBlank() }
                    if (items.isNotEmpty()) blocks.add(NewContentBlockDto.UnsortedList(items))
                }

                child.tagName() == "ol" -> {
                    val items = child.getElementsByTag("li").map { it.text().trim() }.filter { it.isNotBlank() }
                    if (items.isNotEmpty()) blocks.add(NewContentBlockDto.SortedList(items))
                }
            }
        }

        if (blocks.none { it is NewContentBlockDto.ImageCarousel }) {
            val slider = contentElement.parent()?.getElementsByClass("univer-gallery__sliders")?.firstOrNull()
            if (slider != null) {
                val galleryImgs = slider.getElementsByClass("univer-gallery__sliders-top-item").mapNotNull { item ->
                    item.getElementsByTag("img").firstOrNull()?.attr("src")?.takeIf { it.isNotBlank() }?.let { formatImageUrl(it) }
                }.ifEmpty {
                    slider.getElementsByTag("img").mapNotNull { img ->
                        img.attr("src").takeIf { it.isNotBlank() }?.let { formatImageUrl(it) }
                    }
                }
                if (galleryImgs.isNotEmpty()) {
                    blocks.add(NewContentBlockDto.ImageCarousel(galleryImgs))
                    allImages.addAll(galleryImgs)
                }
            }
        }

        return Pair(blocks, allImages)
    }
}

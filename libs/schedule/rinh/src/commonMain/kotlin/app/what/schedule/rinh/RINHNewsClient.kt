package app.what.schedule.rinh

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

class RINHNewsClient(
    private val client: HttpClient,
    private val baseUrl: String = "https://rsue.ru",
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
            val url = if (page <= 1) "$baseUrl/universitet/novosti/" else "$baseUrl/universitet/novosti/?PAGEN_2=$page"
            val response = client.get(url).bodyAsText()
            val document = Ksoup.parse(response)
            val rawData = document.getElementsByClass("news-item")

            rawData.mapNotNull {
                try {
                    val anchor = it.getElementsByTag("a").firstOrNull() ?: return@mapNotNull null
                    val url = anchor.attr("href")
                    val id = url.split("=").lastOrNull() ?: url
                    val imgTag = it.getElementsByTag("img").firstOrNull()
                    val bannerUrl = imgTag?.attr("src")?.let { src -> formatImageUrl(src) }
                    val title = anchor.text().trim()
                    val dateText = it.getElementById("news-date")?.text()?.trim()
                    val date = try {
                        val tmp = dateText?.split(" ")
                        if (tmp != null && tmp.size >= 3) {
                            LocalDate(tmp[2].toInt(), parseMonth(tmp[1]), tmp[0].toInt())
                        } else Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
                    } catch (_: Exception) {
                        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
                    }

                    NewListItemDto(
                        id = id,
                        title = title,
                        description = "",
                        date = date,
                        imageUrl = bannerUrl,
                        sourceUrl = if (url.startsWith("http")) url else baseUrl + url
                    )
                } catch (e: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            log?.invoke("Ошибка получения новостей РГЭУ РИНХ: ${e.message}")
            emptyList()
        }
    }

    override suspend fun getNewDetail(id: String): NewDetailDto {
        val url = "$baseUrl/universitet/novosti/novosti.php?ELEMENT_ID=$id"
        return try {
            val response = client.get(url).bodyAsText()
            val document = Ksoup.parse(response)

            val title = document.getElementsByTag("h1").firstOrNull()?.text()?.trim().orEmpty()
            val imgTag = document.getElementsByTag("img").firstOrNull()
            val firstImg = imgTag?.attr("src")?.let { src -> formatImageUrl(src) }

            val dateText = document.getElementById("date-news")?.text()?.trim()
            val date = try {
                val tmp = dateText?.split(" ")
                if (tmp != null && tmp.size >= 3) {
                    LocalDate(tmp[2].toInt(), parseMonth(tmp[1]), tmp[0].toInt())
                } else Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            } catch (_: Exception) {
                Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            }

            val textElement = document.getElementById("text-news")
            val (contentBlocks, parsedImages) = if (textElement != null) {
                parseContent(textElement, document)
            } else {
                Pair(emptyList(), emptyList())
            }

            val allImages = mutableListOf<String>()
            if (firstImg != null) allImages.add(firstImg)
            for (img in parsedImages) {
                if (!allImages.contains(img)) allImages.add(img)
            }

            NewDetailDto(
                id = id,
                title = title,
                fullText = textElement?.text()?.trim().orEmpty(),
                date = date,
                bannerUrl = allImages.firstOrNull(),
                images = allImages,
                sourceUrl = url,
                contentBlocks = contentBlocks
            )
        } catch (e: Exception) {
            log?.invoke("Ошибка получения новости РГЭУ РИНХ $id: ${e.message}")
            NewDetailDto(
                id = id,
                title = "",
                fullText = "",
                date = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
                sourceUrl = url
            )
        }
    }

    private fun parseContent(textElement: Element, document: Element): Pair<List<NewContentBlockDto>, List<String>> {
        val blocks = mutableListOf<NewContentBlockDto>()
        val allImages = mutableListOf<String>()

        for (child in textElement.children()) {
            when {
                child.tagName() in setOf("h2", "h3", "h4") -> {
                    val text = child.text().trim()
                    if (text.isNotBlank()) blocks.add(NewContentBlockDto.Subtitle(text))
                }

                child.hasClass("owl-carousel") || child.getElementsByClass("owl-carousel").isNotEmpty() -> {
                    val imgs = child.getElementsByTag("img").mapNotNull { img ->
                        img.attr("src").takeIf { it.isNotBlank() }?.let { formatImageUrl(it) }
                    }
                    if (imgs.isNotEmpty()) {
                        blocks.add(NewContentBlockDto.ImageCarousel(imgs))
                        allImages.addAll(imgs)
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
            val slider = document.getElementsByClass("slider-news").firstOrNull()
                ?: document.getElementsByClass("owl-carousel").firstOrNull()
            if (slider != null) {
                val imgs = slider.getElementsByTag("img").mapNotNull { img ->
                    img.attr("src").takeIf { it.isNotBlank() }?.let { formatImageUrl(it) }
                }
                if (imgs.isNotEmpty()) {
                    blocks.add(NewContentBlockDto.ImageCarousel(imgs))
                    allImages.addAll(imgs)
                }
            }
        }

        return Pair(blocks, allImages)
    }
}

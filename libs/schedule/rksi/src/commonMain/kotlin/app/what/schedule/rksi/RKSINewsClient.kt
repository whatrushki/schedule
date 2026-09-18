package app.what.schedule.rksi

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

class RKSINewsClient(
    private val client: HttpClient,
    private val baseUrl: String = "https://rksi.ru"
) : NewsClient {

    private fun formatImageUrl(url: String): String = when {
        url.isEmpty() -> ""
        url.startsWith("http") -> url
        url.startsWith("//") -> "https:$url"
        url.startsWith("/") -> "$baseUrl$url"
        else -> "$baseUrl/$url"
    }

    override suspend fun getNews(page: Int): List<NewListItemDto> = try {
        val url = if (page <= 1) "$baseUrl/news" else "$baseUrl/news/$page"
        val response = client.get(url).bodyAsText()
        val document = Ksoup.parse(response)
        val rawData = document.getElementsByClass("flexnews")

        rawData.mapNotNull { element ->
            val link = element.getElementsByTag("a").firstOrNull() ?: return@mapNotNull null
            val itemUrl = baseUrl + link.attr("href")
            val id = itemUrl.split("_").lastOrNull() ?: return@mapNotNull null
            val imgTag = element.getElementsByTag("img").firstOrNull()
            val bannerUrl = imgTag?.attr("src")?.let { formatImageUrl(it) } ?: "$baseUrl/img/news/$id.jpg"
            val title = element.getElementsByTag("h4").firstOrNull()?.text()?.trim() ?: ""
            val fullDesc = element.getElementsByTag("div").firstOrNull()?.text()?.trim() ?: ""
            val dateText = element.getElementsByTag("span").firstOrNull()?.text()?.trim() ?: ""

            var description = fullDesc
            if (dateText.isNotBlank() && description.startsWith(dateText)) {
                description = description.removePrefix(dateText).trim()
            }
            if (title.isNotBlank() && description.startsWith(title)) {
                description = description.removePrefix(title).trim()
            }

            val date = try {
                val tmp = dateText.split(".").map { it.trim().toInt() }
                LocalDate(tmp[2], tmp[1], tmp[0])
            } catch (_: Exception) {
                Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            }

            NewListItemDto(
                id = id,
                title = title,
                description = description,
                date = date,
                imageUrl = bannerUrl.takeIf { it.isNotBlank() },
                sourceUrl = itemUrl
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    override suspend fun getNewDetail(id: String): NewDetailDto = try {
        val url = "$baseUrl/news/n_$id"
        val response = client.get(url).bodyAsText()
        val document = Ksoup.parse(response)

        val titleRaw = document.getElementsByTag("h1").firstOrNull()?.text()?.trim() ?: ""
        val title = titleRaw.split(" ").dropLast(1).joinToString(" ").ifEmpty { titleRaw }
        val dateText = titleRaw.split(" ").lastOrNull()?.trim('(', ')') ?: ""
        val date = try {
            val tmp = dateText.split(".").map { it.trim().toInt() }
            LocalDate(tmp[2], tmp[1], tmp[0])
        } catch (_: Exception) {
            Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        }

        val mainElement = document.getElementsByTag("main").firstOrNull()
        val descTag = mainElement?.getElementsByTag("b")?.firstOrNull()
            ?: mainElement?.getElementsByTag("strong")?.firstOrNull()
        val descriptionHtml = descTag?.html()?.trim()?.takeIf { it.isNotBlank() }

        val (contentBlocks, parsedImages) = if (mainElement != null) {
            parseContent(mainElement)
        } else {
            Pair(emptyList(), emptyList())
        }

        val bannerUrl = "$baseUrl/img/news/$id.jpg"
        val allImages = if (parsedImages.contains(bannerUrl)) parsedImages else listOf(bannerUrl) + parsedImages

        NewDetailDto(
            id = id,
            title = title,
            fullText = mainElement?.text()?.trim() ?: "",
            descriptionHtml = descriptionHtml,
            date = date,
            bannerUrl = bannerUrl,
            images = allImages,
            sourceUrl = url,
            contentBlocks = contentBlocks
        )
    } catch (_: Exception) {
        NewDetailDto(
            id = id,
            title = "",
            fullText = "",
            date = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
            sourceUrl = "$baseUrl/news/n_$id"
        )
    }

    private fun parseContent(mainElement: Element): Pair<List<NewContentBlockDto>, List<String>> {
        val blocks = mutableListOf<NewContentBlockDto>()
        val allImages = mutableListOf<String>()

        val children = mainElement.children()
        var skippedLead = false

        for (element in children) {
            if (!skippedLead) {
                if (element.tagName() == "hr") {
                    skippedLead = true
                    continue
                }
                if (element.tagName() == "p" && (element.getElementsByTag("b").isNotEmpty() || element.getElementsByTag("strong").isNotEmpty())) {
                    skippedLead = true
                    continue
                }
            }

            when {
                element.tagName() in setOf("h2", "h3", "h4") -> {
                    val text = element.text().trim()
                    if (text.isNotBlank()) {
                        blocks.add(NewContentBlockDto.Subtitle(text))
                    }
                }

                element.hasClass("img50") -> {
                    val img50List = mutableListOf<String>()
                    element.getElementsByTag("p").forEach { p ->
                        val style = p.attr("style")
                        val bgUrl = if ("background-image" in style) {
                            Regex("""url\(['"]?([^'")]+)['"]?\)""").find(style)?.groupValues?.get(1)
                        } else null
                        val src = bgUrl ?: p.getElementsByTag("img").firstOrNull()?.attr("src")
                        if (!src.isNullOrBlank()) {
                            val formatted = formatImageUrl(src)
                            img50List.add(formatted)
                            allImages.add(formatted)
                        }
                    }
                    element.children().filter { it.tagName() == "img" }.forEach { img ->
                        val src = img.attr("src")
                        if (src.isNotBlank()) {
                            val formatted = formatImageUrl(src)
                            img50List.add(formatted)
                            allImages.add(formatted)
                        }
                    }
                    for (imgUrl in img50List) {
                        blocks.add(NewContentBlockDto.Image(imgUrl))
                    }
                }

                element.tagName() == "p" && element.getElementsByTag("img").isNotEmpty() -> {
                    element.getElementsByTag("img").forEach { img ->
                        val src = img.attr("src")
                        if (src.isNotBlank()) {
                            val formatted = formatImageUrl(src)
                            blocks.add(NewContentBlockDto.Image(formatted))
                            allImages.add(formatted)
                        }
                    }
                    val textOnly = element.clone()
                    textOnly.getElementsByTag("img").remove()
                    val html = textOnly.html().trim()
                        .replace(Regex("""^(&nbsp;|\s|\u00A0)+"""), "")
                        .replace(Regex("""^<p>(&nbsp;|\s|\u00A0)+"""), "<p>")
                    if (html.isNotBlank() && textOnly.text().trim().isNotBlank()) {
                        blocks.add(NewContentBlockDto.Text(html))
                    }
                }

                element.tagName() == "p" -> {
                    val html = element.html().trim()
                        .replace(Regex("""^(&nbsp;|\s|\u00A0)+"""), "")
                        .replace(Regex("""^<p>(&nbsp;|\s|\u00A0)+"""), "<p>")
                    if (html.isNotBlank() && element.text().trim().isNotBlank()) {
                        blocks.add(NewContentBlockDto.Text(html))
                    }
                }

                element.tagName() == "ul" -> {
                    val items = element.getElementsByTag("li").map { it.text().trim() }.filter { it.isNotBlank() }
                    if (items.isNotEmpty()) {
                        blocks.add(NewContentBlockDto.UnsortedList(items))
                    }
                }

                element.tagName() == "ol" -> {
                    val items = element.getElementsByTag("li").map { it.text().trim() }.filter { it.isNotBlank() }
                    if (items.isNotEmpty()) {
                        blocks.add(NewContentBlockDto.SortedList(items))
                    }
                }

                element.hasClass("video-container") || element.getElementsByTag("iframe").isNotEmpty() -> {
                    val src = element.getElementsByTag("iframe").attr("src")
                    if (src.isNotBlank()) {
                        blocks.add(NewContentBlockDto.VideoVK(src))
                    }
                }
            }
        }

        // Group trailing consecutive images into ImageCarousel (like original RKSI parser)
        val trailingImages = mutableListOf<String>()
        for (i in blocks.indices.reversed()) {
            val block = blocks[i]
            if (block is NewContentBlockDto.Image) {
                trailingImages.add(0, block.url)
                blocks.removeAt(i)
            } else {
                break
            }
        }
        if (trailingImages.size > 1) {
            blocks.add(NewContentBlockDto.ImageCarousel(trailingImages))
        } else if (trailingImages.size == 1) {
            blocks.add(NewContentBlockDto.Image(trailingImages.first()))
        }

        return Pair(blocks, allImages)
    }
}

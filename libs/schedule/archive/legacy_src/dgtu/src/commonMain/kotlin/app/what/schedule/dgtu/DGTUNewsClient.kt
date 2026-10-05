package app.what.schedule.dgtu

import app.what.schedule.core.clients.NewsClient
import app.what.schedule.core.models.AuthorInfoDto
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

class DGTUNewsClient(
    private val client: HttpClient,
    private val baseUrl: String = "https://news.donstu.ru"
) : NewsClient {

    private fun formatImageUrl(url: String): String = when {
        url.isEmpty() -> ""
        url.startsWith("http") -> url
        url.startsWith("//") -> "https:$url"
        url.startsWith("/") -> "$baseUrl$url"
        else -> "$baseUrl/$url"
    }

    override suspend fun getNews(page: Int): List<NewListItemDto> = try {
        val url = if (page <= 1) "$baseUrl/news/" else "$baseUrl/news/?PAGEN_2=$page"
        val response = client.get(url).bodyAsText()
        val document = Ksoup.parse(response)
        val rawData = document.getElementsByClass("news-card")

        rawData.mapNotNull { element ->
            val link = element.getElementsByClass("news-card__link").firstOrNull()
                ?: element.getElementsByTag("a").firstOrNull() ?: return@mapNotNull null
            val urlPath = link.attr("href")
            val id = urlPath.trim('/').split("/").lastOrNull() ?: return@mapNotNull null
            val bannerUrl = formatImageUrl(element.getElementsByTag("img").attr("src"))
            val title = element.getElementsByClass("news-card__title").firstOrNull()?.text()?.trim()
                ?: element.getElementsByTag("h4").firstOrNull()?.text()?.trim() ?: ""

            val dateText = element.getElementsByClass("news-card__date").firstOrNull()?.attr("datetime")
                ?: element.getElementsByTag("time").firstOrNull()?.attr("datetime")
                ?: ""
            val date = try {
                val tmp = dateText.split(" ").first().split(".").map { it.trim().toInt() }
                LocalDate(tmp[2], tmp[1], tmp[0])
            } catch (_: Exception) {
                Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            }

            val tag = element.getElementsByClass("tag").firstOrNull()?.text()?.trim() ?: ""
            val tags = if (tag.isNotBlank()) listOf(tag) else emptyList()
            val descText = element.getElementsByClass("news-card__text").firstOrNull()?.text()?.trim()
                ?: element.getElementsByClass("news-card__description").firstOrNull()?.text()?.trim()
                ?: element.getElementsByClass("news-card__lead").firstOrNull()?.text()?.trim()
                ?: element.getElementsByTag("p").firstOrNull { !it.hasClass("tag") }?.text()?.trim()
                ?: ""

            NewListItemDto(
                id = id,
                title = title,
                description = descText,
                date = date,
                imageUrl = bannerUrl.takeIf { it.isNotBlank() },
                sourceUrl = if (urlPath.startsWith("http")) urlPath else "$baseUrl$urlPath",
                tags = tags
            )
        }
    } catch (_: Exception) {
        emptyList()
    }

    override suspend fun getNewDetail(id: String): NewDetailDto = try {
        val url = "$baseUrl/news/$id"
        val response = client.get(url).bodyAsText()
        val document = Ksoup.parse(response)

        val title = document.getElementsByTag("h1").firstOrNull()?.text()?.trim() ?: ""
        val subtitle = document.getElementsByClass("detail-hero__subtitle").firstOrNull()?.html()?.trim()?.takeIf { it.isNotBlank() }

        val heroImg = document.getElementsByClass("detail-hero__img").firstOrNull()?.getElementsByTag("img")?.firstOrNull()?.attr("src")
            ?: document.getElementsByTag("img").firstOrNull()?.attr("src")
        val bannerUrl = heroImg?.let { formatImageUrl(it) }

        val dateText = document.getElementsByTag("time").firstOrNull()?.attr("datetime") ?: ""
        val date = try {
            val tmp = dateText.split(" ").first().split(".").map { it.trim().toInt() }
            LocalDate(tmp[2], tmp[1], tmp[0])
        } catch (_: Exception) {
            Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        }

        val contentElement = document.getElementsByClass("text-content").firstOrNull()
            ?: document.getElementsByClass("news-detail").firstOrNull()
            ?: document.getElementsByTag("article").firstOrNull()
            ?: document.getElementsByTag("main").firstOrNull()

        val (contentBlocks, parsedImages) = if (contentElement != null) {
            parseContent(contentElement, document)
        } else {
            Pair(emptyList(), emptyList())
        }

        val allImages = mutableListOf<String>()
        if (bannerUrl != null) allImages.add(bannerUrl)
        for (img in parsedImages) {
            if (!allImages.contains(img)) allImages.add(img)
        }

        val heroTag = document.getElementsByClass("detail-hero__tag").firstOrNull()?.text()?.trim()
            ?: document.getElementsByClass("detail-hero").firstOrNull()?.getElementsByClass("tag")?.firstOrNull()?.text()?.trim()
            ?: document.getElementsByClass("news-detail__tag").firstOrNull()?.text()?.trim()
        val detailTags = if (!heroTag.isNullOrBlank()) listOf(heroTag) else emptyList()

        NewDetailDto(
            id = id,
            title = title,
            fullText = contentElement?.text()?.trim() ?: "",
            descriptionHtml = subtitle,
            date = date,
            bannerUrl = bannerUrl,
            images = allImages,
            sourceUrl = url,
            contentBlocks = contentBlocks,
            tags = detailTags
        )
    } catch (_: Exception) {
        NewDetailDto(
            id = id,
            title = "",
            fullText = "",
            date = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
            sourceUrl = "$baseUrl/news/$id"
        )
    }

    private fun parseContent(contentElement: Element, document: Element): Pair<List<NewContentBlockDto>, List<String>> {
        val blocks = mutableListOf<NewContentBlockDto>()
        val allImages = mutableListOf<String>()

        for (it in contentElement.children()) {
            when {
                it.tagName() in setOf("h2", "h3", "h4") -> {
                    val text = it.text().trim()
                    if (text.isNotBlank()) blocks.add(NewContentBlockDto.Subtitle(text))
                }

                it.hasClass("gallery") || it.getElementsByClass("gallery").isNotEmpty() -> {
                    val galleryImgs = it.getElementsByClass("gallery__thumbs-item").mapNotNull { thumb ->
                        thumb.getElementsByTag("img").firstOrNull()?.attr("src")?.takeIf { s -> s.isNotBlank() }?.let { s -> formatImageUrl(s) }
                    }.ifEmpty {
                        it.getElementsByClass("gallery__main-item").mapNotNull { item ->
                            item.getElementsByTag("img").firstOrNull()?.attr("src")?.takeIf { s -> s.isNotBlank() }?.let { s -> formatImageUrl(s) }
                        }
                    }.ifEmpty {
                        it.getElementsByTag("img").mapNotNull { img ->
                            img.attr("src").takeIf { s -> s.isNotBlank() }?.let { s -> formatImageUrl(s) }
                        }
                    }
                    if (galleryImgs.isNotEmpty()) {
                        blocks.add(NewContentBlockDto.ImageCarousel(galleryImgs))
                        allImages.addAll(galleryImgs)
                    }
                }

                it.tagName() == "blockquote" -> {
                    val avatar = it.selectFirst(".blockquote__img img")?.attr("src")
                        ?: it.selectFirst(".blockqoute__img img")?.attr("src")
                    val authorName = it.getElementsByClass("blockquote__author-name").text().ifEmpty {
                        it.getElementsByClass("blockqoute__author-name").text()
                    }
                    val authorRole = it.getElementsByClass("blockquote__author-post").text().ifEmpty {
                        it.getElementsByClass("blockqoute__author-post").text()
                    }
                    val quoteText = it.getElementsByClass("blockquote__content").firstOrNull()?.getElementsByTag("p")?.firstOrNull()?.text()
                        ?: it.getElementsByClass("blockqoute__content").firstOrNull()?.getElementsByTag("p")?.firstOrNull()?.text()
                        ?: it.text().trim()

                    blocks.add(
                        NewContentBlockDto.Quote(
                            author = AuthorInfoDto(
                                avatarUrl = avatar?.let { formatImageUrl(it) },
                                name = authorName,
                                role = authorRole
                            ),
                            text = quoteText
                        )
                    )
                }

                it.hasClass("highlight") -> {
                    val text = it.getElementsByClass("highlight__content").firstOrNull()?.getElementsByTag("p")?.firstOrNull()?.text()
                        ?: it.text().trim()
                    if (text.isNotBlank()) {
                        blocks.add(NewContentBlockDto.Info(text))
                    }
                }

                it.tagName() == "p" && it.getElementsByTag("img").isNotEmpty() -> {
                    it.getElementsByTag("img").forEach { img ->
                        val src = img.attr("src")
                        if (src.isNotBlank()) {
                            val formatted = formatImageUrl(src)
                            blocks.add(NewContentBlockDto.Image(formatted))
                            allImages.add(formatted)
                        }
                    }
                    val textOnly = it.clone()
                    textOnly.getElementsByTag("img").remove()
                    val html = textOnly.html().trim()
                    if (html.isNotBlank() && textOnly.text().trim().isNotBlank()) {
                        blocks.add(NewContentBlockDto.Text(html))
                    }
                }

                it.tagName() == "p" -> {
                    val html = it.html().trim()
                    if (html.isNotBlank() && it.text().trim().isNotBlank()) {
                        blocks.add(NewContentBlockDto.Text(html))
                    }
                }

                it.tagName() == "ul" -> {
                    val items = it.getElementsByTag("li").map { li -> li.text().trim() }.filter { s -> s.isNotBlank() }
                    if (items.isNotEmpty()) {
                        blocks.add(NewContentBlockDto.UnsortedList(items))
                    }
                }

                it.tagName() == "ol" -> {
                    val items = it.getElementsByTag("li").map { li -> li.text().trim() }.filter { s -> s.isNotBlank() }
                    if (items.isNotEmpty()) {
                        blocks.add(NewContentBlockDto.SortedList(items))
                    }
                }
            }
        }

        // Check if gallery exists outside contentElement
        if (blocks.none { it is NewContentBlockDto.ImageCarousel }) {
            val standaloneGallery = document.getElementsByClass("gallery").firstOrNull()
            if (standaloneGallery != null) {
                val galleryImgs = standaloneGallery.getElementsByClass("gallery__thumbs-item").mapNotNull { thumb ->
                    thumb.getElementsByTag("img").firstOrNull()?.attr("src")?.takeIf { s -> s.isNotBlank() }?.let { s -> formatImageUrl(s) }
                }.ifEmpty {
                    standaloneGallery.getElementsByTag("img").mapNotNull { img ->
                        img.attr("src").takeIf { s -> s.isNotBlank() }?.let { s -> formatImageUrl(s) }
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

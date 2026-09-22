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

    private fun formatUrl(url: String): String {
        val trimmed = url.trim()
        return when {
            trimmed.isEmpty() -> ""
            trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("mailto:") || trimmed.startsWith("tel:") -> trimmed
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("www.") || trimmed.startsWith("vk.com") || trimmed.startsWith("t.me") -> "https://$trimmed"
            trimmed.startsWith("/") -> "$baseUrl$trimmed"
            else -> "$baseUrl/$trimmed"
        }
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
            val banner = cached.bannerUrl
            val finalBlocks = if (banner != null) {
                removeBannerImageFromBlocks(cached.contentBlocks, banner)
            } else cached.contentBlocks

            return NewDetailDto(
                id = cached.id,
                title = cached.title,
                fullText = cached.description,
                descriptionHtml = null,
                date = cached.date,
                bannerUrl = banner,
                images = banner?.let { listOf(it) } ?: emptyList(),
                sourceUrl = cached.sourceUrl,
                contentBlocks = finalBlocks
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

            val detailContentBlocks = parseElementBlocks(container)
            val mergedBlocks = smartMergeBlocks(
                listBlocks = cached?.contentBlocks ?: emptyList(),
                detailBlocks = detailContentBlocks,
                bannerUrl = bannerUrl
            )
            val finalBlocks = if (bannerUrl != null) {
                removeBannerImageFromBlocks(mergedBlocks, bannerUrl)
            } else mergedBlocks

            NewDetailDto(
                id = id,
                title = title,
                fullText = container.text().trim(),
                descriptionHtml = null,
                date = date,
                bannerUrl = bannerUrl,
                images = images,
                sourceUrl = targetUrl,
                contentBlocks = if (finalBlocks.isNotEmpty()) finalBlocks else cached?.contentBlocks ?: emptyList()
            )
        } catch (e: Exception) {
            log?.invoke("Error fetching SFEDU news detail for $id: ${e.message}")
            if (cached != null) {
                val banner = cached.bannerUrl
                val finalBlocks = if (banner != null) {
                    removeBannerImageFromBlocks(cached.contentBlocks, banner)
                } else cached.contentBlocks

                // Fallback to cached list item content
                NewDetailDto(
                    id = cached.id,
                    title = cached.title,
                    fullText = cached.description,
                    descriptionHtml = null,
                    date = cached.date,
                    bannerUrl = banner,
                    images = banner?.let { listOf(it) } ?: emptyList(),
                    sourceUrl = cached.sourceUrl,
                    contentBlocks = finalBlocks
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
        val topLevelElements = elements.filter { el ->
            el.parents().none { parent -> parent in elements }
        }

        for (el in topLevelElements) {
            if (el.tagName() == "img") {
                val src = el.attr("src").trim()
                if (src.isNotBlank()) {
                    contentBlocks.add(NewContentBlockDto.Image(formatUrl(src)))
                }
                continue
            }

            // Extract any nested <img> tags first so they become standalone Image blocks
            val images = el.select("img")
            for (img in images) {
                val src = img.attr("src").trim()
                if (src.isNotBlank()) {
                    contentBlocks.add(NewContentBlockDto.Image(formatUrl(src)))
                }
                img.remove()
            }

            // Ensure all <a> links have absolute URLs
            for (a in el.select("a")) {
                val href = a.attr("href").trim()
                if (href.isNotBlank()) {
                    a.attr("href", formatUrl(href))
                }
            }

            if (el.tagName() in listOf("h3", "h4")) {
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
                val html = el.html().trim()
                if (html.isNotBlank() && el.text().trim().isNotBlank()) {
                    val paragraphs = splitParagraphs(html)
                    for (para in paragraphs) {
                        contentBlocks.add(NewContentBlockDto.Text(para))
                    }
                }
            }
        }
        return contentBlocks
    }

    private val knownAbbreviations = setOf(
        "г", "ул", "д", "кв", "ауд", "им", "тел", "руб", "коп", "тыс", "млн", "млрд",
        "проф", "доц", "акад", "канд", "док", "сент", "окт", "нояб", "дек", "янв",
        "февр", "апр", "авг", "т", "п", "э", "к", "ф", "м", "н", "ч", "стр"
    )

    private fun isSentenceBoundary(fullText: String, matchStart: Int): Boolean {
        val punc = fullText[matchStart]
        if (punc == '!' || punc == '?') return true

        val before = fullText.substring(0, matchStart).trim()
        val words = before.split(Regex("\\s+"))
        if (words.isEmpty()) return false

        val lastWord = words.last().lowercase().trimEnd('.')
        if (lastWord.length <= 1) return false

        val cleaned = lastWord.replace(Regex("[^а-яa-z]"), "")
        if (cleaned in knownAbbreviations) return false

        return true
    }

    private fun splitHugeParagraph(html: String, minChunkLen: Int = 300, maxChunkLen: Int = 550): List<String> {
        val clean = html.replace(Regex("<[^>]+>"), " ").trim()
        if (clean.length <= maxChunkLen) return listOf(html)

        val pattern = Regex("([.!?])\\s+(?=[A-ZА-ЯЁ«])")
        val splits = mutableListOf<String>()
        var lastPos = 0

        for (match in pattern.findAll(html)) {
            val puncPos = match.range.first
            val afterSpacePos = match.range.last + 1

            val chunkSoFar = html.substring(lastPos, puncPos + 1)
            val cleanChunk = chunkSoFar.replace(Regex("<[^>]+>"), " ").trim()

            if (cleanChunk.length >= minChunkLen) {
                if (isSentenceBoundary(html, puncPos)) {
                    splits.add(html.substring(lastPos, puncPos + 1).trim())
                    lastPos = afterSpacePos
                }
            }
        }

        if (lastPos < html.length) {
            val rem = html.substring(lastPos).trim()
            if (rem.isNotBlank()) {
                val cleanRem = rem.replace(Regex("<[^>]+>"), " ").trim()
                if (cleanRem.length < 100 && splits.isNotEmpty()) {
                    splits[splits.lastIndex] = splits.last() + " " + rem
                } else {
                    splits.add(rem)
                }
            }
        }

        return if (splits.isNotEmpty()) splits else listOf(html)
    }

    private fun splitParagraphs(html: String): List<String> {
        val cleaned = html.replace("\u200b", "").trim()
        val delim = "___PARAGRAPH_BREAK___"

        // 1. Multiple consecutive <br> tags
        var s = cleaned.replace(Regex("(?:<br\\s*/?>\\s*(?:&nbsp;|\\s)*){2,}", RegexOption.IGNORE_CASE), delim)

        // 2. <br> after sentence ending punctuation followed by capital letter, quote, or dash
        s = s.replace(Regex("([.!?…:])\\s*(?:</[^>]+>\\s*)*<br\\s*/?>\\s*(?:<[^>]+>\\s*)*(?=[A-ZА-ЯЁ«\"—\\-–\\d])", RegexOption.IGNORE_CASE), "$1$delim")

        // 3. <br> after strong/b heading: </strong><br>, </b><br>
        s = s.replace(Regex("(</(?:strong|b)>)\\s*<br\\s*/?>\\s*", RegexOption.IGNORE_CASE), "$1$delim")

        // 4. <br> before list bullet / number: <br>—, <br>1), <br>•
        s = s.replace(Regex("<br\\s*/?>\\s*(?:<[^>]+>\\s*)*(?=[—\\-–•]|\\d+[\\).])", RegexOption.IGNORE_CASE), delim)

        val rawParts = s.split(delim)
        val result = mutableListOf<String>()

        for (part in rawParts) {
            val clean = part.replace(Regex("<[^>]+>"), " ").trim()
            if (clean.isNotBlank()) {
                val subparts = splitHugeParagraph(part.trim())
                result.addAll(subparts.filter { it.replace(Regex("<[^>]+>"), " ").isNotBlank() })
            }
        }

        return if (result.isNotEmpty()) result else listOf(html)
    }

    private fun extractBlockText(block: NewContentBlockDto): String = when (block) {
        is NewContentBlockDto.Text -> block.html.replace(Regex("<[^>]+>"), " ").replace(Regex("&[a-zA-Z0-9#]+;"), " ")
        is NewContentBlockDto.Subtitle -> block.text
        is NewContentBlockDto.UnsortedList -> block.items.joinToString(" ")
        is NewContentBlockDto.SortedList -> block.items.joinToString(" ")
        is NewContentBlockDto.Info -> block.text
        is NewContentBlockDto.Quote -> block.text
        else -> ""
    }

    private fun normalize(text: String): String =
        text.replace(Regex("<[^>]+>"), " ")
            .replace(Regex("&[a-zA-Z0-9#]+;"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase()

    private fun smartMergeBlocks(
        listBlocks: List<NewContentBlockDto>,
        detailBlocks: List<NewContentBlockDto>,
        bannerUrl: String? = null
    ): List<NewContentBlockDto> {
        if (listBlocks.isEmpty()) return detailBlocks
        if (detailBlocks.isEmpty()) return listBlocks

        val listAllText = normalize(listBlocks.joinToString(" ") { extractBlockText(it) })
        val detailAllText = normalize(detailBlocks.joinToString(" ") { extractBlockText(it) })

        // If detail already contains all list text, detail blocks are sufficient
        if (listAllText.isNotBlank() && detailAllText.contains(listAllText)) {
            return detailBlocks
        }

        // If list contains all detail text, list blocks are sufficient
        if (detailAllText.isNotBlank() && listAllText.contains(detailAllText)) {
            return listBlocks
        }

        val detailWords = detailAllText.split(' ').filter { it.isNotBlank() }
        val uniquePrefixBlocks = mutableListOf<NewContentBlockDto>()

        for (b in listBlocks) {
            when (b) {
                is NewContentBlockDto.Image -> {
                    val isDuplicate = (bannerUrl != null && isSameOrDerivativeImage(b.url, bannerUrl)) ||
                            detailBlocks.any { it is NewContentBlockDto.Image && isSameOrDerivativeImage(it.url, b.url) }
                    if (!isDuplicate) {
                        uniquePrefixBlocks.add(b)
                    }
                }
                is NewContentBlockDto.Text, is NewContentBlockDto.Subtitle -> {
                    val bNorm = normalize(extractBlockText(b))
                    if (bNorm.isBlank()) continue

                    if (detailAllText.contains(bNorm)) {
                        continue
                    }

                    val bWords = bNorm.split(' ').filter { it.isNotBlank() }
                    var overlapFound = false

                    val maxK = minOf(bWords.size, detailWords.size)
                    for (k in maxK downTo 3) {
                        if (bWords.takeLast(k) == detailWords.take(k)) {
                            overlapFound = true
                            val uniqueWordsCount = bWords.size - k
                            if (uniqueWordsCount > 0) {
                                val origWords = extractBlockText(b).trim().split(Regex("\\s+"))
                                val uniqueText = origWords.take(uniqueWordsCount).joinToString(" ")
                                if (uniqueText.isNotBlank()) {
                                    uniquePrefixBlocks.add(NewContentBlockDto.Text("<p>$uniqueText</p>"))
                                }
                            }
                            break
                        }
                    }

                    if (!overlapFound) {
                        var matchedStart = -1
                        val checkLength = minOf(10, bWords.size)
                        for (k in checkLength downTo 3) {
                            for (s in (bWords.size - k) downTo 1) {
                                val chunk = bWords.subList(s, s + k).joinToString(" ")
                                if (detailAllText.take(400).contains(chunk)) {
                                    matchedStart = s
                                    break
                                }
                            }
                            if (matchedStart != -1) break
                        }

                        if (matchedStart > 0) {
                            val origWords = extractBlockText(b).trim().split(Regex("\\s+"))
                            val uniqueText = origWords.take(matchedStart).joinToString(" ")
                            if (uniqueText.isNotBlank()) {
                                uniquePrefixBlocks.add(NewContentBlockDto.Text("<p>$uniqueText</p>"))
                            }
                        } else {
                            uniquePrefixBlocks.add(b)
                        }
                    }
                }
                else -> {
                    val bNorm = normalize(extractBlockText(b))
                    if (bNorm.isNotBlank() && !detailAllText.contains(bNorm)) {
                        uniquePrefixBlocks.add(b)
                    }
                }
            }
        }

        return uniquePrefixBlocks + detailBlocks
    }

    private fun isSameOrDerivativeImage(url1: String, url2: String): Boolean {
        val u1 = url1.trim().lowercase()
        val u2 = url2.trim().lowercase()
        if (u1 == u2) return true

        val file1 = u1.substringAfterLast('/')
        val file2 = u2.substringAfterLast('/')
        if (file1 == file2) return true

        val base1 = file1.substringBeforeLast('.').replace(Regex("[-_]?(small|thumb|preview)"), "")
        val base2 = file2.substringBeforeLast('.').replace(Regex("[-_]?(small|thumb|preview)"), "")
        if (base1.isNotBlank() && base1 == base2) return true

        return false
    }

    private fun removeBannerImageFromBlocks(
        blocks: List<NewContentBlockDto>,
        bannerUrl: String
    ): List<NewContentBlockDto> {
        val firstImageIndex = blocks.indexOfFirst { it is NewContentBlockDto.Image }
        if (firstImageIndex != -1) {
            val firstImage = blocks[firstImageIndex] as NewContentBlockDto.Image
            if (isSameOrDerivativeImage(firstImage.url, bannerUrl)) {
                return blocks.filterIndexed { index, _ -> index != firstImageIndex }
            }
        }
        return blocks
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

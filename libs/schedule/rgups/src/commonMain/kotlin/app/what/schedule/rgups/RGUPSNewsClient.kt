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
    private val client: HttpClient? = null,
    private val baseUrl: String = "https://www.rgups.ru",
    private val log: ((String) -> Unit)? = null
) : NewsClient {

    override suspend fun getNews(page: Int): List<NewListItemDto> {
        val httpClient = client ?: return emptyList()
        return try {
            val url = if (page <= 1) "$baseUrl/news/" else "$baseUrl/news/page$page/"
            val html = httpClient.get(url).bodyAsText()
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
                    description = "",
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
        val httpClient = client ?: throw IllegalStateException("HttpClient is required for fetching network news")
        val url = if (id.startsWith("http")) id else "$baseUrl$id"
        val html = httpClient.get(url).bodyAsText()
        return parseNewsDetail(html, url, id)
    }

    fun parseNewsDetail(html: String, url: String, id: String = url): NewDetailDto {
        val doc = Ksoup.parse(html)

        val title = doc.selectFirst("h1")?.text()?.trim()
            ?: doc.selectFirst("h2, .page-header h2")?.text()?.trim()
            ?: ""
        val bodyElem = doc.selectFirst("div.text, .text")
            ?: doc.selectFirst(".news-detail, article, main")

        // Normalize relative URLs in <a> tags
        bodyElem?.select("a")?.forEach { a ->
            val href = a.attr("href").trim()
            if (href.startsWith("/")) {
                a.attr("href", "$baseUrl$href")
            }
        }

        val dateStr = doc.selectFirst("time.publication-time, time, .date")?.text()?.trim()
            ?: Regex("""\b(\d{2}\.\d{2}\.\d{4})\b""").find(doc.text())?.value.orEmpty()
        val date = parseDate(dateStr)

        val allImages = mutableListOf<String>()
        val blocks = mutableListOf<NewContentBlockDto>()

        fun processP(p: com.fleeksoft.ksoup.nodes.Element) {
            val imgs = p.select("img")
            for (img in imgs) {
                val src = img.attr("src").trim()
                if (src.isNotEmpty()) {
                    val fullSrc = if (src.startsWith("http")) src else "$baseUrl$src"
                    blocks.add(NewContentBlockDto.Image(fullSrc))
                    allImages.add(fullSrc)
                }
            }
            val textOnly = p.clone()
            textOnly.select("img").remove()
            val cleanText = textOnly.text().replace("\u00A0", " ").trim()
            if (cleanText.isEmpty() && textOnly.select("iframe, video").isEmpty()) {
                return // Ignore empty paragraph spacing
            }
            val pHtml = textOnly.html().trim()
            if (pHtml.isNotEmpty()) {
                blocks.addAll(smartParseTextToBlocks(pHtml))
            }
        }

        bodyElem?.children()?.forEach { child ->
            when (child.tagName().lowercase()) {
                "p" -> processP(child)
                "div" -> {
                    val nestedPs = child.select("p")
                    if (nestedPs.isNotEmpty()) {
                        nestedPs.forEach { processP(it) }
                    } else {
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
                        val cleanText = textOnly.text().replace("\u00A0", " ").trim()
                        if (cleanText.isNotEmpty() || textOnly.select("iframe, video").isNotEmpty()) {
                            val divHtml = textOnly.html().trim()
                            if (divHtml.isNotEmpty()) {
                                blocks.addAll(smartParseTextToBlocks(divHtml))
                            }
                        }
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
                "h1", "h2", "h3", "h4", "h5", "h6" -> {
                    val text = child.text().replace("\u00A0", " ").trim()
                    if (text.isNotEmpty() && !text.equals(title, ignoreCase = true)) {
                        val links = child.select("a")
                        if (links.isNotEmpty()) {
                            // Subtitle contains links: preserve as bold Text block so links are clickable!
                            blocks.add(NewContentBlockDto.Text("<strong>${child.html().trim()}</strong>"))
                        } else {
                            blocks.add(NewContentBlockDto.Subtitle(text))
                        }
                    }
                }
                "ul" -> {
                    val items = child.select("li").map { it.text().replace("\u00A0", " ").trim() }.filter { it.isNotEmpty() }
                    if (items.isNotEmpty()) blocks.add(NewContentBlockDto.UnsortedList(items))
                }
                "ol" -> {
                    val items = child.select("li").map { it.text().replace("\u00A0", " ").trim() }.filter { it.isNotEmpty() }
                    if (items.isNotEmpty()) blocks.add(NewContentBlockDto.SortedList(items))
                }
                "blockquote" -> {
                    val text = child.text().replace("\u00A0", " ").trim()
                    if (text.isNotEmpty()) blocks.add(NewContentBlockDto.Info(text))
                }
            }
        }

        // Merge consecutive list blocks of same type
        val mergedListBlocks = mutableListOf<NewContentBlockDto>()
        for (block in blocks) {
            val last = mergedListBlocks.lastOrNull()
            if (last is NewContentBlockDto.SortedList && block is NewContentBlockDto.SortedList) {
                mergedListBlocks[mergedListBlocks.size - 1] = NewContentBlockDto.SortedList(last.items + block.items)
            } else if (last is NewContentBlockDto.UnsortedList && block is NewContentBlockDto.UnsortedList) {
                mergedListBlocks[mergedListBlocks.size - 1] = NewContentBlockDto.UnsortedList(last.items + block.items)
            } else {
                mergedListBlocks.add(block)
            }
        }
        blocks.clear()
        blocks.addAll(mergedListBlocks)

        // Combine trailing series of photos (>= 2 images) into a carousel
        val trailingImages = mutableListOf<String>()
        var i = blocks.size - 1
        while (i >= 0 && blocks[i] is NewContentBlockDto.Image) {
            trailingImages.add(0, (blocks[i] as NewContentBlockDto.Image).url)
            i--
        }
        if (trailingImages.size >= 2) {
            while (blocks.size > i + 1) {
                blocks.removeAt(blocks.size - 1)
            }
            blocks.add(NewContentBlockDto.ImageCarousel(trailingImages))
        }

        // Remove duplicated title at the beginning of content if present
        if (title.isNotBlank() && blocks.isNotEmpty()) {
            fun normalizeForComparison(s: String) = s.lowercase()
                .replace(Regex("""[«»"“”',.!?:;\-\s]+"""), "")
                .trim()

            val cleanTitle = normalizeForComparison(title)
            if (cleanTitle.isNotEmpty()) {
                val firstTextIdx = blocks.indexOfFirst { it is NewContentBlockDto.Text || it is NewContentBlockDto.Subtitle }
                if (firstTextIdx != -1) {
                    val block = blocks[firstTextIdx]
                    val blockText = when (block) {
                        is NewContentBlockDto.Text -> Ksoup.parseBodyFragment(block.html).text().trim()
                        is NewContentBlockDto.Subtitle -> block.text.trim()
                        else -> ""
                    }
                    val cleanBlock = normalizeForComparison(blockText)
                    if (cleanBlock.isNotEmpty()) {
                        if (cleanTitle == cleanBlock || 
                            (cleanBlock.startsWith(cleanTitle) && cleanBlock.length <= cleanTitle.length + 15) ||
                            (cleanTitle.startsWith(cleanBlock) && cleanTitle.length <= cleanBlock.length + 15)) {
                            blocks.removeAt(firstTextIdx)
                        } else if (cleanBlock.startsWith(cleanTitle)) {
                            val rawClean = Ksoup.parseBodyFragment(blockText).text().trim()
                            val afterTitle = rawClean.removePrefix(title).trim()
                                .removePrefix(".").removePrefix(":").removePrefix("-").removePrefix("—").trim()
                            if (afterTitle.isNotEmpty()) {
                                blocks[firstTextIdx] = NewContentBlockDto.Text(afterTitle)
                            } else {
                                blocks.removeAt(firstTextIdx)
                            }
                        }
                    }
                }
            }
        }

        val fullText = blocks.filterIsInstance<NewContentBlockDto.Text>().joinToString("\n\n") { it.html }

        if (blocks.isEmpty() && fullText.isNotEmpty()) {
            blocks.add(NewContentBlockDto.Text(fullText))
        }

        return NewDetailDto(
            id = id,
            title = title,
            fullText = fullText,
            descriptionHtml = null,
            date = date,
            bannerUrl = allImages.firstOrNull(),
            images = allImages.distinct(),
            sourceUrl = url,
            contentBlocks = blocks
        )
    }

    private fun smartParseTextToBlocks(rawHtml: String): List<NewContentBlockDto> {
        val cleanHtml = rawHtml.trim()
        if (cleanHtml.isEmpty()) return emptyList()

        val textCheck = Ksoup.parseBodyFragment(cleanHtml).text().replace("\u00A0", " ").trim()
        if (textCheck.isEmpty() && !cleanHtml.contains("<img", ignoreCase = true) && !cleanHtml.contains("<iframe", ignoreCase = true)) {
            return emptyList()
        }

        // Normalize breaks: double breaks -> \n\n, single break -> \n
        val normalized = cleanHtml
            .replace(Regex("""(<br\s*/?>\s*){2,}""", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
            .replace("\r\n", "\n")
            .replace("\r", "\n")

        val rawLines = normalized.split("\n").map { it.trim() }
        val result = mutableListOf<NewContentBlockDto>()

        var currentParagraph = StringBuilder()
        val currentUnsortedList = mutableListOf<String>()
        val currentSortedList = mutableListOf<String>()

        fun flushList() {
            if (currentUnsortedList.isNotEmpty()) {
                result.add(NewContentBlockDto.UnsortedList(currentUnsortedList.toList()))
                currentUnsortedList.clear()
            }
            if (currentSortedList.isNotEmpty()) {
                result.add(NewContentBlockDto.SortedList(currentSortedList.toList()))
                currentSortedList.clear()
            }
        }

        fun flushParagraph() {
            val text = currentParagraph.toString().trim()
            if (text.isNotEmpty()) {
                val clean = Ksoup.parseBodyFragment(text).text().replace("\u00A0", " ").trim()
                if (clean.isNotEmpty()) {
                    val balanced = Ksoup.parseBodyFragment(text).body().html().trim()
                    if (balanced.isNotEmpty()) {
                        result.add(NewContentBlockDto.Text(balanced))
                    }
                }
            }
            currentParagraph = StringBuilder()
        }

        val bulletRegex = Regex("""^(?:[•*]|[-—–](?![-—–]))\s+(.+)""")
        val numberRegex = Regex("""^(\d{1,3})[\.)]\s+(.+)""")

        for (rawLine in rawLines) {
            val line = if (rawLine.startsWith("--")) {
                "— " + rawLine.removePrefix("--").trimStart()
            } else {
                rawLine
            }
            val lineClean = Ksoup.parseBodyFragment(line).text().replace("\u00A0", " ").trim()
            if (lineClean.isEmpty()) {
                flushParagraph()
                flushList()
                continue
            }

            val hasLinksOrMedia = line.contains("<a ", ignoreCase = true) || 
                                  line.contains("<iframe", ignoreCase = true) || 
                                  line.contains("<video", ignoreCase = true)

            val isDateAtStart = Regex("""^\d{1,2}\.\d{1,2}(\.\d{2,4})?(\s*г\.?|\s*года)?\b""").containsMatchIn(lineClean)

            val bulletMatch = if (!hasLinksOrMedia) bulletRegex.find(line) else null
            val numberMatch = if (!hasLinksOrMedia && !isDateAtStart) numberRegex.find(line) else null

            if (bulletMatch != null) {
                flushParagraph()
                if (currentSortedList.isNotEmpty()) flushList()
                val itemText = bulletMatch.groups[1]?.value?.trim().orEmpty()
                val cleanItem = Ksoup.parseBodyFragment(itemText).text().replace("\u00A0", " ").trim()
                if (cleanItem.isNotEmpty()) {
                    currentUnsortedList.add(cleanItem)
                }
                continue
            }

            if (numberMatch != null) {
                flushParagraph()
                if (currentUnsortedList.isNotEmpty()) flushList()
                val itemText = numberMatch.groups[2]?.value?.trim().orEmpty()
                val cleanItem = Ksoup.parseBodyFragment(itemText).text().replace("\u00A0", " ").trim()
                if (cleanItem.isNotEmpty()) {
                    currentSortedList.add(cleanItem)
                }
                continue
            }

            // Normal text line
            flushList()

            if (currentParagraph.isEmpty()) {
                currentParagraph.append(line)
            } else {
                val prevText = currentParagraph.toString().trim()
                val prevClean = Ksoup.parseBodyFragment(prevText).body().text().trim()
                val currClean = Ksoup.parseBodyFragment(line).body().text().trim()

                val prevEndsWithSentencePunct = prevClean.isNotEmpty() &&
                    (prevClean.endsWith(".") || prevClean.endsWith("!") || prevClean.endsWith("?") ||
                     prevClean.endsWith(":") || prevClean.endsWith("»") || prevClean.endsWith("\"") ||
                     prevClean.endsWith(";"))

                val currStartsWithUpper = currClean.isNotEmpty() &&
                    (currClean[0].isUpperCase() || currClean.startsWith("«") || currClean.startsWith("\""))

                val currStartsWithLower = currClean.isNotEmpty() && currClean[0].isLowerCase()

                if (prevEndsWithSentencePunct && currStartsWithUpper) {
                    flushParagraph()
                    currentParagraph.append(line)
                } else if (currStartsWithLower) {
                    currentParagraph.append(" ").append(line)
                } else if (prevClean.endsWith(":")) {
                    flushParagraph()
                    currentParagraph.append(line)
                } else if (currStartsWithUpper && prevClean.length > 50) {
                    flushParagraph()
                    currentParagraph.append(line)
                } else {
                    currentParagraph.append(" ").append(line)
                }
            }
        }

        flushParagraph()
        flushList()

        return result
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

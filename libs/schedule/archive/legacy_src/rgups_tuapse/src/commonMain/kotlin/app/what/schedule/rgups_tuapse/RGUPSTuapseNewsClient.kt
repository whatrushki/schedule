package app.what.schedule.rgups_tuapse

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

class RGUPSTuapseNewsClient(
    private val client: HttpClient,
    private val baseUrl: String = "https://rgups-tuapse.ru",
    private val log: ((String) -> Unit)? = null
) : NewsClient {

    override suspend fun getNews(page: Int): List<NewListItemDto> {
        return try {
            val start = (page.coerceAtLeast(1) - 1) * 6
            val url = "$baseUrl/?start=$start"
            val html = client.get(url).bodyAsText()
            val doc = Ksoup.parse(html)

            val items = doc.select(".blog-item, .com-content-category-blog__item")
            items.mapNotNull { item ->
                val titleElem = item.selectFirst("h2 a, h3 a, .item-title a") ?: return@mapNotNull null
                val title = titleElem.text().trim()
                if (title.isBlank()) return@mapNotNull null
                val href = titleElem.attr("href").trim()
                val id = href

                val imgElem = item.selectFirst(".item-image img, figure img, img")
                val imgSrc = imgElem?.attr("src")?.trim()?.ifEmpty { null }
                val imageUrl = imgSrc?.let { if (it.startsWith("http")) it else "$baseUrl$it" }

                val dateElem = item.selectFirst(".published, time, dd.published")
                val date = parseDate(dateElem?.text().orEmpty())

                val descElem = item.select("p").firstOrNull { it.select("img").isEmpty() && it.text().isNotBlank() }
                val desc = descElem?.text()?.trim().orEmpty()

                NewListItemDto(
                    id = id,
                    title = title,
                    description = "",
                    date = date,
                    imageUrl = imageUrl,
                    sourceUrl = if (href.startsWith("http")) href else "$baseUrl$href"
                )
            }
        } catch (e: Exception) {
            log?.invoke("Error fetching RGUPSTuapse news: ${e.message}")
            emptyList()
        }
    }

    override suspend fun getNewDetail(id: String): NewDetailDto {
        val url = if (id.startsWith("http")) id else "$baseUrl$id"
        val html = client.get(url).bodyAsText()
        val doc = Ksoup.parse(html)

        var title = doc.selectFirst(".item-page h2, .page-header h2, h2")?.text()?.trim().orEmpty()
        if (title.isEmpty() || title.equals("Новости", ignoreCase = true)) {
            val pageTitle = doc.selectFirst("title")?.text()?.trim().orEmpty()
            title = pageTitle.substringBefore(" - ").substringBefore(" | ").trim()
        }
        if (title.isEmpty() || title.equals("Новости", ignoreCase = true)) {
            title = doc.selectFirst("h1")?.text()?.trim().orEmpty()
        }

        val bannerSrc = doc.selectFirst(".item-image img, figure.item-image img, figure img")?.attr("src")?.trim()?.ifEmpty { null }
        val bannerUrl = bannerSrc?.let { if (it.startsWith("http")) it else "$baseUrl$it" }

        val bodyElem = doc.selectFirst(".com-content-article__body")
            ?: doc.selectFirst(".item-page, article")

        // Normalize relative URLs in <a> tags
        bodyElem?.select("a")?.forEach { a ->
            val href = a.attr("href").trim()
            if (href.startsWith("/")) {
                a.attr("href", "$baseUrl$href")
            }
        }

        val dateElem = doc.selectFirst(".published, time, dd.published")
        val date = parseDate(dateElem?.text().orEmpty())

        val allImages = mutableListOf<String>()
        bannerUrl?.let { allImages.add(it) }

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
            val innerHtml = textOnly.html().trim()
            if (innerHtml.isNotEmpty()) {
                blocks.addAll(smartParseTextToBlocks(innerHtml))
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
                    if (text.isNotEmpty() && !text.equals(title, ignoreCase = true) && !text.equals("Новости", ignoreCase = true)) {
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

        // Combine trailing series of photos into a carousel, ignoring trailing links
        var endIdx = blocks.size - 1
        val trailingLinks = mutableListOf<NewContentBlockDto>()
        while (endIdx >= 0) {
            val b = blocks[endIdx]
            if (b is NewContentBlockDto.Text && (b.html.contains("<a", ignoreCase = true) || b.html.contains("http", ignoreCase = true))) {
                trailingLinks.add(0, b)
                endIdx--
            } else {
                break
            }
        }
        val trailingImages = mutableListOf<String>()
        var imgIdx = endIdx
        while (imgIdx >= 0 && blocks[imgIdx] is NewContentBlockDto.Image) {
            trailingImages.add(0, (blocks[imgIdx] as NewContentBlockDto.Image).url)
            imgIdx--
        }
        if (trailingImages.size >= 2) {
            while (blocks.size > imgIdx + 1) {
                blocks.removeAt(blocks.size - 1)
            }
            blocks.add(NewContentBlockDto.ImageCarousel(trailingImages))
            blocks.addAll(trailingLinks)
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
            bannerUrl = bannerUrl ?: allImages.firstOrNull(),
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
            val clean = str.replace("Опубликовано:", "").replace("Дата:", "").trim()
            val parts = clean.split(" ").filter { it.isNotBlank() }
            if (parts.size >= 3) {
                val day = parts[0].toIntOrNull() ?: today.dayOfMonth
                val month = parseMonth(parts[1])
                val year = parts[2].toIntOrNull() ?: today.year
                LocalDate(year, month, day)
            } else {
                today
            }
        } catch (_: Exception) {
            today
        }
    }

    private fun parseMonth(name: String): Int = when (name.lowercase().take(3)) {
        "янв" -> 1
        "фев" -> 2
        "мар" -> 3
        "апр" -> 4
        "май", "мая" -> 5
        "июн" -> 6
        "июл" -> 7
        "авг" -> 8
        "сен" -> 9
        "окт" -> 10
        "ноя" -> 11
        "дек" -> 12
        else -> 1
    }
}

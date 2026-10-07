package app.what.schedule.rksi

import app.what.schedule.core.clients.NewsClient
import app.what.schedule.core.models.NewContentBlockDto
import app.what.schedule.core.models.NewDetailDto
import app.what.schedule.core.models.*
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime


class RKSINewsClient(
    private val client: HttpClient? = null,
    private val baseUrl: String = "https://rksi.ru"
) : NewsClient {

    fun formatImageUrl(url: String): String = when {
        url.isEmpty() -> ""
        url.startsWith("http") -> url
        url.startsWith("//") -> "https:$url"
        url.startsWith("/") -> "$baseUrl$url"
        else -> "$baseUrl/$url"
    }

    override suspend fun getNews(page: Int): List<NewListItemDto> = try {
        val httpClient = client ?: emptyList<NewListItemDto>().let { return it }
        val url = if (page <= 1) "$baseUrl/news" else "$baseUrl/news/$page"
        val response = httpClient.get(url).bodyAsText()
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
        val httpClient = client ?: throw IllegalStateException("HttpClient is required for fetching network news")
        val url = "$baseUrl/news/n_$id"
        val response = httpClient.get(url).bodyAsText()
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

open class RksiSessionExpiredException(message: String = "Сессия РКСИ истекла") : RuntimeException(message)

class RKSIAccountClient(
    private val client: HttpClient,
    private val baseUrl: String = "https://rksi.ru"
) {
    var sessionCookies: List<String> = emptyList()

    private fun cookieHeader(): String {
        return sessionCookies.joinToString("; ")
    }

    private fun formatImageUrl(url: String): String = when {
        url.isEmpty() -> ""
        url.startsWith("http") -> url
        url.startsWith("//") -> "https:$url"
        url.startsWith("/") -> "$baseUrl$url"
        else -> "$baseUrl/$url"
    }

    fun isSessionExpired(html: String): Boolean {
        return html.contains("name=\"log\"") ||
               html.contains("name=\"pwrd\"") ||
               html.contains("Авторизация студента") ||
               html.contains("name=\"babah\"")
    }

    suspend fun login(login: String, pass: String): Boolean {
        val response = client.submitForm(
            url = "$baseUrl/account",
            formParameters = io.ktor.http.parameters {
                append("log", login)
                append("pwrd", pass)
                append("babah", "Вход в аккаунт")
            }
        ) {
            headers.append("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            headers.append("Referer", "$baseUrl/account")
        }
        val setCookies = response.headers.getAll("Set-Cookie") ?: emptyList()
        val parsedCookies = setCookies.map { it.split(";").first().trim() }.filter { it.isNotBlank() }
        if (parsedCookies.isNotEmpty()) {
            sessionCookies = parsedCookies
        }
        val html = response.bodyAsText()
        return !isSessionExpired(html) && (html.contains("exitacc") || html.contains("Мой профиль") || (html.contains("Кабинет студента") && !html.contains("Авторизация студента")))
    }

    suspend fun getProfile(): AccountProfileDto {
        val html = client.get("$baseUrl/account") {
            if (sessionCookies.isNotEmpty()) {
                headers.append("Cookie", cookieHeader())
            }
        }.bodyAsText()

        val doc = Ksoup.parse(html)
        val h1 = doc.getElementsByTag("h1").firstOrNull()?.text()?.trim() ?: ""
        val studentFullName = h1.split("::").firstOrNull()?.trim() ?: ""

        if (isSessionExpired(html)) {
            throw RksiSessionExpiredException()
        }

        if (studentFullName.isBlank() || studentFullName.equals("Кабинет студента", ignoreCase = true)) {
            throw IllegalStateException("Не удалось получить данные профиля")
        }

        val nameParts = studentFullName.split(" ").filter { it.isNotBlank() }
        val surname = nameParts.getOrNull(0) ?: ""
        val name = nameParts.getOrNull(1) ?: ""
        val middleName = nameParts.getOrNull(2)

        var specialty: String? = null
        var group = ""
        var curator: String? = null
        var curatorPhone: String? = null
        var educationForm: String? = null
        var healthGroup: String? = null
        var socialStatus: String? = null
        val extras = mutableMapOf<String, String>()

        doc.select("table tr").forEach { tr ->
            val text = tr.text().trim()
            when {
                text.startsWith("Специальность:") -> specialty = text.removePrefix("Специальность:").trim()
                text.startsWith("Группа:") -> group = text.removePrefix("Группа:").trim()
                text.startsWith("Классный руководитель:") -> {
                    val raw = text.removePrefix("Классный руководитель:").trim()
                    val phoneMatch = Regex("""(\+?\d[\d\s-]{9,}\d)""").find(raw)
                    if (phoneMatch != null) {
                        curatorPhone = phoneMatch.value.trim()
                        curator = raw.replace(phoneMatch.value, "").trim()
                    } else {
                        curator = raw
                    }
                }
                text.startsWith("Форма финансирования обучения:") -> educationForm = text.removePrefix("Форма финансирования обучения:").trim()
                text.startsWith("Группа здоровья:") -> healthGroup = text.removePrefix("Группа здоровья:").trim()
                text.startsWith("Социальный статус:") -> {
                    socialStatus = text.removePrefix("Социальный статус:").replace("(редактировать)", "").trim()
                }
            }
        }

        val photoImg = doc.getElementById("myfoto") ?: doc.select("img[src*='/afoto/']").firstOrNull()
        val photoUrl = photoImg?.attr("src")?.takeIf { it.isNotBlank() }?.let { formatImageUrl(it) }

        return AccountProfileDto(
            fullName = studentFullName.ifBlank { "Студент РКСИ" },
            name = name,
            surname = surname,
            middleName = middleName,
            group = group,
            specialty = specialty,
            curator = curator,
            curatorPhone = curatorPhone,
            photoUrl = photoUrl,
            educationForm = educationForm,
            healthGroup = healthGroup,
            socialStatus = socialStatus,
            extraDetails = extras
        )
    }

    suspend fun getEnquiries(): Pair<List<EnquiryItemDto>, List<EnquiryTypeDto>> {
        val html = client.get("$baseUrl/account/enquiry") {
            if (sessionCookies.isNotEmpty()) {
                headers.append("Cookie", cookieHeader())
            }
        }.bodyAsText()

        if (isSessionExpired(html)) {
            throw RksiSessionExpiredException()
        }

        val doc = Ksoup.parse(html)
        val orderedList = mutableListOf<EnquiryItemDto>()
        val typesList = mutableListOf<EnquiryTypeDto>()

        doc.select("ol li a").forEach { a ->
            val href = a.attr("href")
            val id = href.split("/").lastOrNull()?.trim() ?: ""
            val title = a.text().trim()
            if (id.isNotBlank() && title.isNotBlank()) {
                typesList.add(EnquiryTypeDto(id = id, title = title))
            }
        }

        val tables = doc.getElementsByTag("table")
        tables.forEach { table ->
            val rows = table.getElementsByTag("tr")
            for (i in 1 until rows.size) {
                val cols = rows[i].getElementsByTag("td").map { it.text().trim() }
                if (cols.size >= 5) {
                    orderedList.add(
                        EnquiryItemDto(
                            title = cols[0],
                            status = cols[1],
                            orderDate = cols[2],
                            pickupLocation = cols[3],
                            count = cols[4].toIntOrNull() ?: 1
                        )
                    )
                }
            }
        }

        return Pair(orderedList, typesList)
    }

    suspend fun orderEnquiry(typeId: String, params: Map<String, String>): Boolean {
        return try {
            val response = client.submitForm(
                url = "$baseUrl/account/enquiry/$typeId",
                formParameters = io.ktor.http.parameters {
                    params.forEach { (k, v) -> append(k, v) }
                    if (typeId == "5" || typeId == "6") {
                        if (!params.containsKey("sendDatastudents")) {
                            append("sendDatastudents", "Сохранить")
                        }
                    } else {
                        if (!params.containsKey("add")) {
                            append("add", "Заказать")
                        }
                    }
                }
            ) {
                if (sessionCookies.isNotEmpty()) {
                    headers.append("Cookie", cookieHeader())
                }
                headers.append("Referer", "$baseUrl/account/enquiry/$typeId")
            }
            val responseText = response.bodyAsText()
            if (isSessionExpired(responseText)) {
                throw RksiSessionExpiredException()
            }
            response.status.value in 200..399
        } catch (e: RksiSessionExpiredException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getReAttestations(): List<ReAttestationItemDto> {
        val html = client.get("$baseUrl/account/reat") {
            if (sessionCookies.isNotEmpty()) {
                headers.append("Cookie", cookieHeader())
            }
        }.bodyAsText()

        if (isSessionExpired(html)) {
            throw RksiSessionExpiredException()
        }

        val doc = Ksoup.parse(html)
        val list = mutableListOf<ReAttestationItemDto>()
        val table = doc.selectFirst("table.zebra") ?: doc.selectFirst("table") ?: return list
        val rows = table.getElementsByTag("tr")
        for (i in 1 until rows.size) {
            val cols = rows[i].getElementsByTag("td").map { it.text().trim() }
            if (cols.size >= 4) {
                list.add(
                    ReAttestationItemDto(
                        discipline = cols[0],
                        teacher = cols[1],
                        issueDate = cols[2],
                        deadlineDate = cols[3]
                    )
                )
            }
        }
        return list
    }

    suspend fun getPaymentQrConfig(): PaymentQrConfigDto {
        val html = client.get("$baseUrl/account/qr") {
            if (sessionCookies.isNotEmpty()) {
                headers.append("Cookie", cookieHeader())
            }
        }.bodyAsText()

        if (isSessionExpired(html)) {
            throw RksiSessionExpiredException()
        }

        val doc = Ksoup.parse(html)
        val targets = doc.select("select[name=pay_target] option").map {
            PaymentTargetOptionDto(it.attr("value"), it.text().trim())
        }
        val years = doc.select("select[name=pay_year] option").map {
            PaymentYearOptionDto(it.attr("value"), it.text().trim())
        }
        val periods = doc.select("select[name=period] option").map {
            PaymentPeriodOptionDto(it.attr("value"), it.text().trim())
        }

        return PaymentQrConfigDto(targets = targets, years = years, periods = periods)
    }

    fun buildPaymentQrImageUrl(targetId: String, yearId: String, periodId: String): String {
        return "$baseUrl/backend/qrcode.php?pay_target=$targetId&pay_year=$yearId&period=$periodId"
    }

    suspend fun getPaymentQrImageBytes(targetId: String, yearId: String, periodId: String): ByteArray {
        val response = client.get("$baseUrl/backend/qrcode.php") {
            parameter("pay_target", targetId)
            parameter("pay_year", yearId)
            parameter("period", periodId)
            if (sessionCookies.isNotEmpty()) {
                headers.append("Cookie", cookieHeader())
            }
        }
        val bytes = response.body<ByteArray>()
        val textPreview = bytes.take(200).toByteArray().decodeToString()
        if (isSessionExpired(textPreview) || textPreview.contains("Undefined index: student") || textPreview.contains("<br />")) {
            throw RksiSessionExpiredException()
        }
        return bytes
    }

    suspend fun getProfileSections(): List<ProfileSectionDto> {
        val html = client.get("$baseUrl/profile_editor") {
            if (sessionCookies.isNotEmpty()) {
                headers.append("Cookie", cookieHeader())
            }
        }.bodyAsText()

        if (isSessionExpired(html)) {
            throw RksiSessionExpiredException()
        }

        val doc = Ksoup.parse(html)
        val sections = mutableListOf<ProfileSectionDto>()
        var currentSectionTitle = "Основные данные"
        var currentFields = mutableListOf<ProfileInputFieldDto>()

        doc.select(".main .fildbox, .main .htmlbox").forEach { box ->
            if (box.hasClass("htmlbox")) {
                val h2 = box.selectFirst("h2")?.text()?.trim()
                if (!h2.isNullOrBlank()) {
                    if (currentFields.isNotEmpty()) {
                        sections.add(ProfileSectionDto(currentSectionTitle, currentFields))
                        currentFields = mutableListOf()
                    }
                    currentSectionTitle = h2
                }
            } else {
                val label = box.selectFirst(".fildtitle")?.text()?.trim() ?: ""
                val input = box.selectFirst("input")
                val select = box.selectFirst("select")

                if (select != null) {
                    val name = select.attr("name")
                    val id = select.attr("id")
                    val options = select.select("option").map { it.attr("value") to it.text().trim() }
                    val selectedValue = select.selectFirst("option[selected]")?.attr("value")
                        ?: options.firstOrNull()?.first ?: ""
                    currentFields.add(
                        ProfileInputFieldDto(
                            id = id,
                            name = name,
                            label = label,
                            value = selectedValue,
                            type = "select",
                            options = options
                        )
                    )
                } else if (input != null) {
                    val name = input.attr("name")
                    val id = input.attr("id")
                    val value = input.attr("value")
                    currentFields.add(
                        ProfileInputFieldDto(
                            id = id,
                            name = name,
                            label = label,
                            value = value,
                            type = "text"
                        )
                    )
                }
            }
        }

        if (currentFields.isNotEmpty()) {
            sections.add(ProfileSectionDto(currentSectionTitle, currentFields))
        }

        return sections
    }

    suspend fun saveProfile(fields: Map<String, String>): Boolean {
        return try {
            val response = client.submitForm(
                url = "$baseUrl/profile_editor",
                formParameters = io.ktor.http.parameters {
                    fields.forEach { (k, v) -> append(k, v) }
                    append("sendDatastudents", "Сохранить")
                }
            ) {
                if (sessionCookies.isNotEmpty()) {
                    headers.append("Cookie", cookieHeader())
                }
                headers.append("Referer", "$baseUrl/profile_editor")
            }
            val responseText = response.bodyAsText()
            if (isSessionExpired(responseText)) {
                throw RksiSessionExpiredException()
            }
            response.status.value in 200..399
        } catch (e: RksiSessionExpiredException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getSocialStatus(): List<SocialStatusOptionDto> {
        val html = client.get("$baseUrl/profile_editor/social_status") {
            if (sessionCookies.isNotEmpty()) {
                headers.append("Cookie", cookieHeader())
            }
        }.bodyAsText()

        if (isSessionExpired(html)) {
            throw RksiSessionExpiredException()
        }

        val doc = Ksoup.parse(html)
        val list = mutableListOf<SocialStatusOptionDto>()
        doc.select(".chbox_item").forEach { item ->
            val label = item.selectFirst("label")?.text()?.trim() ?: ""
            val chbox = item.selectFirst("input[type=checkbox]")
            val name = chbox?.attr("name") ?: ""
            val id = Regex("""\d+""").find(name)?.value ?: ""
            val isChecked = chbox?.hasAttr("checked") == true
            if (id.isNotBlank() && label.isNotBlank()) {
                list.add(SocialStatusOptionDto(id = id, title = label, isChecked = isChecked))
            }
        }
        return list
    }

    suspend fun saveSocialStatus(checkedIds: Set<String>, allIds: List<String>): Boolean {
        return try {
            val response = client.submitForm(
                url = "$baseUrl/profile_editor/social_status",
                formParameters = io.ktor.http.parameters {
                    allIds.forEach { id ->
                        append("chitem[$id]", "1")
                        if (id in checkedIds) {
                            append("chbox[$id]", "1")
                        }
                    }
                    append("submit", "Сохранить")
                }
            ) {
                if (sessionCookies.isNotEmpty()) {
                    headers.append("Cookie", cookieHeader())
                }
                headers.append("Referer", "$baseUrl/profile_editor/social_status")
            }
            val responseText = response.bodyAsText()
            if (isSessionExpired(responseText)) {
                throw RksiSessionExpiredException()
            }
            response.status.value in 200..399
        } catch (e: RksiSessionExpiredException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }
}


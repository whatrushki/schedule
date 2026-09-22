package app.what.data.remote.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode

private val linkColor = Color(0xFF1976D2)
private val linkStyles = TextLinkStyles(
    style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
)

private val urlRegex = Regex("""https?://[^\s<>"'()]+""")

fun AnnotatedString.trim(): AnnotatedString {
    val raw = this.text
    var start = 0
    while (start < raw.length && (raw[start].isWhitespace() || raw[start] == '\u00A0')) {
        start++
    }
    var end = raw.length
    while (end > start && (raw[end - 1].isWhitespace() || raw[end - 1] == '\u00A0')) {
        end--
    }
    return if (start == 0 && end == raw.length) this
    else if (start >= end) AnnotatedString("")
    else this.subSequence(start, end)
}

fun AnnotatedString.Companion.fromHtml(html: String): AnnotatedString {
    return try {
        val document = Ksoup.parseBodyFragment(html)
        val body = document.body()
        buildAnnotatedString {
            appendNode(body)
        }.trim()
    } catch (_: Exception) {
        buildAnnotatedString {
            appendWithAutoLinks(html)
        }.trim()
    }
}

private fun AnnotatedString.Builder.appendWithAutoLinks(text: String) {
    val matches = urlRegex.findAll(text).toList()
    if (matches.isEmpty()) {
        append(text)
        return
    }

    var lastIndex = 0
    for (match in matches) {
        if (match.range.first > lastIndex) {
            append(text.substring(lastIndex, match.range.first))
        }
        val url = match.value
        val start = length
        append(url)
        val end = length
        addLink(LinkAnnotation.Url(url = url, styles = linkStyles), start, end)
        lastIndex = match.range.last + 1
    }
    if (lastIndex < text.length) {
        append(text.substring(lastIndex))
    }
}

private fun AnnotatedString.Builder.appendNode(node: Node) {
    for (child in node.childNodes()) {
        when (child) {
            is TextNode -> {
                var text = child.text()
                // Strip leading spaces/nbsp if at start of block or right after a newline
                if (length == 0 || toAnnotatedString().text.endsWith("\n")) {
                    text = text.trimStart { it.isWhitespace() || it == '\u00A0' }
                }
                if (text.isEmpty()) continue

                val parentTag = (child.parent() as? Element)?.tagName()?.lowercase()
                if (parentTag == "a") {
                    append(text)
                } else {
                    appendWithAutoLinks(text)
                }
            }
            is Element -> {
                val tag = child.tagName().lowercase()
                val isBold = tag in setOf("b", "strong")
                val isItalic = tag in setOf("i", "em")
                val isLink = tag == "a"
                val href = child.attr("href").trim()

                val style = when {
                    isBold && isItalic -> SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
                    isBold -> SpanStyle(fontWeight = FontWeight.Bold)
                    isItalic -> SpanStyle(fontStyle = FontStyle.Italic)
                    else -> null
                }

                val start = length
                appendNode(child)
                val end = length

                if (style != null && end > start) {
                    addStyle(style, start, end)
                }

                if (isLink && href.isNotBlank() && end > start) {
                    val fullUrl = when {
                        href.startsWith("http://") || href.startsWith("https://") || href.startsWith("mailto:") || href.startsWith("tel:") -> href
                        href.startsWith("//") -> "https:$href"
                        href.startsWith("www.") || href.startsWith("vk.com") || href.startsWith("t.me") -> "https://$href"
                        href.startsWith("/") -> "https://mmcs.sfedu.ru$href"
                        else -> "https://mmcs.sfedu.ru/$href"
                    }

                    addLink(LinkAnnotation.Url(url = fullUrl, styles = linkStyles), start, end)
                }

                if (tag == "br") {
                    if (length > 0 && !toAnnotatedString().text.endsWith("\n")) {
                        append("\n")
                    }
                } else if (tag in setOf("p", "div", "li")) {
                    if (length > 0 && !toAnnotatedString().text.endsWith("\n")) {
                        append("\n")
                    }
                }
            }
        }
    }
}

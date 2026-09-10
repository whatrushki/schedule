package app.what.data.remote.utils

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Element
import com.fleeksoft.ksoup.nodes.Node
import com.fleeksoft.ksoup.nodes.TextNode

fun AnnotatedString.Companion.fromHtml(html: String): AnnotatedString {
    return try {
        val document = Ksoup.parseBodyFragment(html)
        val body = document.body()
        buildAnnotatedString {
            appendNode(body)
        }
    } catch (_: Exception) {
        AnnotatedString(html)
    }
}

private fun AnnotatedString.Builder.appendNode(node: Node) {
    for (child in node.childNodes()) {
        when (child) {
            is TextNode -> {
                append(child.text())
            }
            is Element -> {
                val isBold = child.tagName() in setOf("b", "strong")
                val isItalic = child.tagName() in setOf("i", "em")
                val isParagraph = child.tagName() == "p" || child.tagName() == "br"

                val style = when {
                    isBold && isItalic -> SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
                    isBold -> SpanStyle(fontWeight = FontWeight.Bold)
                    isItalic -> SpanStyle(fontStyle = FontStyle.Italic)
                    else -> null
                }

                if (style != null) {
                    val start = length
                    appendNode(child)
                    val end = length
                    addStyle(style, start, end)
                } else {
                    appendNode(child)
                }
                if (isParagraph && child.tagName() == "p") {
                    append("\n")
                }
            }
        }
    }
}

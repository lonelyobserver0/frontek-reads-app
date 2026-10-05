package dev.frontek.reads.feed

import dev.frontek.reads.data.Article
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ParsedFeed(val title: String, val items: List<Article>)

/** RSS 2.0 / RSS 1.0 / Atom parser, a port of the web app's parseFeed(). */
object FeedParser {
    private const val CONTENT_CAP = 12000

    fun parse(xml: String): ParsedFeed {
        val start = xml.indexOf('<')
        val doc = Jsoup.parse(if (start > 0) xml.substring(start) else xml, "", Parser.xmlParser())

        val channelTitle = doc.selectFirst("channel > title, feed > title")?.let { textOf(it) } ?: ""

        val rssItems = doc.getElementsByTag("item")
        if (rssItems.isNotEmpty()) {
            val items = rssItems.map {
                val html = richHtml(it, atom = false)
                val link = child(it, "link")?.let { l -> textOf(l) } ?: ""
                Article(
                    title = child(it, "title")?.let(::textOf).orEmpty(),
                    link = link,
                    date = parseDate(child(it, "pubDate")?.let(::textOf) ?: child(it, "dc:date")?.let(::textOf)),
                    summary = Html.stripHtml(html),
                    content = html,
                    image = extractImage(it, html),
                    id = child(it, "guid")?.let(::textOf).orEmpty().ifEmpty { link },
                )
            }
            return ParsedFeed(channelTitle, items)
        }

        val items = doc.getElementsByTag("entry").map { en ->
            val html = richHtml(en, atom = true)
            val link = atomLink(en)
            Article(
                title = child(en, "title")?.let(::textOf).orEmpty(),
                link = link,
                date = parseDate(child(en, "updated")?.let(::textOf) ?: child(en, "published")?.let(::textOf)),
                summary = Html.stripHtml(html),
                content = html,
                image = extractImage(en, html),
                id = child(en, "id")?.let(::textOf).orEmpty().ifEmpty { link },
            )
        }
        return ParsedFeed(channelTitle, items)
    }

    // ---------- helpers ----------

    /** Direct child with that (case-insensitive, prefixed) name, else first descendant. */
    private fun child(node: Element, name: String): Element? {
        val n = name.lowercase()
        node.children().firstOrNull { it.normalName() == n }?.let { return it }
        return node.getElementsByTag(n).firstOrNull { it !== node }
    }

    private fun textOf(el: Element): String = el.wholeText().trim()

    /** Raw markup of a content element: CDATA / escaped HTML as text, inline XHTML as markup. */
    private fun rawOf(el: Element?): String {
        if (el == null) return ""
        return if (el.children().isNotEmpty() && el.attr("type") == "xhtml") el.html() else el.wholeText()
    }

    private fun richHtml(node: Element, atom: Boolean): String {
        val html = if (atom) rawOf(child(node, "content")).ifBlank { rawOf(child(node, "summary")) }
        else rawOf(child(node, "content:encoded")).ifBlank { rawOf(child(node, "description")) }
        val t = html.trim()
        return if (t.length > CONTENT_CAP) t.substring(0, CONTENT_CAP) else t
    }

    private fun atomLink(entry: Element): String {
        var href = ""
        for (l in entry.getElementsByTag("link")) {
            val rel = l.attr("rel")
            if (rel.isEmpty() || rel == "alternate") return l.attr("href")
            if (href.isEmpty()) href = l.attr("href")
        }
        return href
    }

    // ---------- images ----------

    private fun absImg(u: String?): String {
        var s = u?.trim().orEmpty()
        if (s.startsWith("//")) s = "https:$s"
        return if (s.startsWith("http://", true) || s.startsWith("https://", true)) s else ""
    }

    private val IMG_EXT = Regex("""\.(jpe?g|png|webp|gif)(\?|$)""", RegexOption.IGNORE_CASE)
    private val FIRST_IMG = Regex("""<img[^>]+src\s*=\s*["']?(https?://[^"'\s>]+)""", RegexOption.IGNORE_CASE)

    /** media:thumbnail → media:content(image) → enclosure(image) → itunes:image → first <img>. */
    private fun extractImage(node: Element, html: String): String {
        node.getElementsByTag("media:thumbnail").firstOrNull { it.hasAttr("url") }?.let { return absImg(it.attr("url")) }
        for (mc in node.getElementsByTag("media:content")) {
            val url = mc.attr("url")
            val medium = mc.attr("medium").lowercase()
            val type = mc.attr("type").lowercase()
            if (url.isNotEmpty() && (medium == "image" || type.startsWith("image") || IMG_EXT.containsMatchIn(url))) return absImg(url)
        }
        for (enc in node.getElementsByTag("enclosure")) {
            val url = enc.attr("url")
            if (url.isNotEmpty() && enc.attr("type").lowercase().startsWith("image")) return absImg(url)
        }
        node.getElementsByTag("itunes:image").firstOrNull { it.hasAttr("href") }?.let { return absImg(it.attr("href")) }
        return absImg(FIRST_IMG.find(html)?.groupValues?.get(1))
    }

    // ---------- dates ----------

    private val RFC822 = listOf(
        "EEE, d MMM yyyy HH:mm:ss zzz", "EEE, d MMM yyyy HH:mm:ss Z", "d MMM yyyy HH:mm:ss zzz",
        "d MMM yyyy HH:mm:ss Z", "EEE, d MMM yyyy HH:mm zzz", "EEE, d MMM yyyy HH:mm:ss",
    )

    /** RFC 822 (RSS) and ISO 8601 (Atom) dates to epoch millis; 0 if unparseable. */
    fun parseDate(raw: String?): Long {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return 0
        runCatching { return ZonedDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }
        runCatching { return OffsetDateTime.parse(s).toInstant().toEpochMilli() }
        runCatching { return Instant.parse(s).toEpochMilli() }
        runCatching { return ZonedDateTime.parse(s).toInstant().toEpochMilli() }
        for (p in RFC822) {
            runCatching {
                val f = SimpleDateFormat(p, Locale.US).apply { isLenient = true }
                return f.parse(s)!!.time
            }
        }
        runCatching { return LocalDate.parse(s.take(10)).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() }
        return 0
    }
}

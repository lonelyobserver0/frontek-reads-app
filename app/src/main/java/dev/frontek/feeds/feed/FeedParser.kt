package dev.frontek.feeds.feed

import dev.frontek.feeds.model.FeedItem
import dev.frontek.feeds.model.ParsedFeed
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser

/** RSS 2.0 / Atom parser, ported from the web app's parseFeed(). */
object FeedParser {

    private const val CONTENT_CAP = 12000

    fun parse(xmlText: String): ParsedFeed {
        // Some feeds/proxies prepend junk before the XML declaration.
        val start = xmlText.indexOf('<')
        val cleaned = if (start > 0) xmlText.substring(start) else xmlText
        val doc = Jsoup.parse(cleaned, "", Parser.xmlParser())

        val channelTitle = doc.selectFirst("channel > title")
            ?: doc.selectFirst("feed > title")
        val feedTitle = channelTitle?.text()?.trim().orEmpty()

        val rssItems = doc.getElementsByTag("item")
        if (rssItems.isNotEmpty()) {
            val items = rssItems.map { item ->
                val html = richHtml(item, isAtom = false)
                val date = tagText(item, "pubdate").ifEmpty { tagText(item, "date") }
                FeedItem(
                    title = tagText(item, "title").ifEmpty { "(untitled)" },
                    link = tagText(item, "link"),
                    date = DateParser.parse(date),
                    summary = HtmlUtils.stripHtml(html),
                    content = html,
                    id = tagText(item, "guid").ifEmpty { tagText(item, "link") },
                    image = extractImage(item, html),
                )
            }
            return ParsedFeed(feedTitle, items)
        }

        val entries = doc.getElementsByTag("entry")
        val items = entries.map { entry ->
            val html = richHtml(entry, isAtom = true)
            val date = tagText(entry, "updated").ifEmpty { tagText(entry, "published") }
            FeedItem(
                title = tagText(entry, "title").ifEmpty { "(untitled)" },
                link = atomLink(entry),
                date = DateParser.parse(date),
                summary = HtmlUtils.stripHtml(html),
                content = html,
                id = tagText(entry, "id").ifEmpty { atomLink(entry) },
                image = extractImage(entry, html),
            )
        }
        return ParsedFeed(feedTitle, items)
    }

    /** Text of the first descendant with the given (lower-cased) tag name. */
    private fun tagText(node: Element, tag: String): String =
        node.getElementsByTag(tag).firstOrNull()?.text()?.trim().orEmpty()

    /** Raw (unescaped) inner text of the first descendant with the given tag. */
    private fun rawOf(node: Element, tag: String): String =
        node.getElementsByTag(tag).firstOrNull()?.wholeText()?.trim().orEmpty()

    private fun richHtml(node: Element, isAtom: Boolean): String {
        val html = if (isAtom) {
            rawOf(node, "content").ifEmpty { rawOf(node, "summary") }
        } else {
            rawOf(node, "content:encoded").ifEmpty { rawOf(node, "description") }
        }
        return if (html.length > CONTENT_CAP) html.substring(0, CONTENT_CAP) else html
    }

    /** Best-effort lead image: media/enclosure tags, then the first content <img>. */
    private fun extractImage(node: Element, html: String): String? {
        node.getElementsByTag("media:thumbnail").firstOrNull()
            ?.attr("url")?.takeIf { it.isNotBlank() }?.let { return it }

        node.getElementsByTag("media:content").firstOrNull {
            val medium = it.attr("medium")
            val type = it.attr("type")
            (medium == "image" || type.startsWith("image")) && it.attr("url").isNotBlank()
        }?.attr("url")?.takeIf { it.isNotBlank() }?.let { return it }

        node.getElementsByTag("enclosure").firstOrNull {
            it.attr("type").startsWith("image") && it.attr("url").isNotBlank()
        }?.attr("url")?.takeIf { it.isNotBlank() }?.let { return it }

        node.getElementsByTag("itunes:image").firstOrNull()
            ?.attr("href")?.takeIf { it.isNotBlank() }?.let { return it }

        if (html.isNotBlank()) {
            val src = Jsoup.parse(html).selectFirst("img[src]")?.attr("src")
            if (!src.isNullOrBlank() && src.startsWith("http")) return src
        }
        return null
    }

    private fun atomLink(entry: Element): String {
        val links = entry.getElementsByTag("link")
        var href = ""
        for (link in links) {
            val rel = link.attr("rel")
            if (rel.isEmpty() || rel == "alternate") {
                href = link.attr("href")
                break
            }
            if (href.isEmpty()) href = link.attr("href")
        }
        return href.trim()
    }
}

package dev.frontek.feeds.data

import dev.frontek.feeds.feed.UrlUtils
import dev.frontek.feeds.model.Subscription
import org.json.JSONArray
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

/** OPML / JSON import-export of subscriptions, mirroring the web app. */
object Opml {

    /** Build an OPML 2.0 document from subscriptions. */
    fun export(subs: List<Subscription>): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<opml version=\"2.0\">\n")
        sb.append("  <head><title>frontek reads subscriptions</title></head>\n")
        sb.append("  <body>\n")
        subs.forEach { s ->
            sb.append(
                "    <outline type=\"rss\" text=\"${esc(s.title)}\" title=\"${esc(s.title)}\" " +
                    "xmlUrl=\"${esc(s.feed)}\" htmlUrl=\"${esc(s.site ?: "")}\"/>\n",
            )
        }
        sb.append("  </body>\n")
        sb.append("</opml>\n")
        return sb.toString()
    }

    /** Parse OPML or JSON text into subscriptions (deduplication is the caller's job). */
    fun import(text: String): List<Subscription> {
        val trimmed = text.trimStart()
        return if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            importJson(text)
        } else {
            importOpml(text)
        }
    }

    private fun importJson(text: String): List<Subscription> = try {
        val arr = JSONArray(text)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val feed = o.optString("feed").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Subscription(
                title = o.optString("title").ifBlank { UrlUtils.host(feed) },
                feed = feed,
                site = o.optString("site").takeIf { it.isNotBlank() } ?: UrlUtils.origin(feed),
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun importOpml(text: String): List<Subscription> = try {
        val doc = Jsoup.parse(text, "", Parser.xmlParser())
        doc.select("outline").mapNotNull { o ->
            val feed = o.attr("xmlUrl").ifBlank { o.attr("xmlurl") }
            if (feed.isBlank()) return@mapNotNull null
            Subscription(
                title = o.attr("text").ifBlank { o.attr("title").ifBlank { UrlUtils.host(feed) } },
                feed = feed,
                site = o.attr("htmlUrl").ifBlank { o.attr("htmlurl") }.ifBlank { UrlUtils.origin(feed) },
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun esc(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}

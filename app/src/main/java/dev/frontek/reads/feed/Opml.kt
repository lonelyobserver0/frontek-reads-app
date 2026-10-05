package dev.frontek.reads.feed

import dev.frontek.reads.data.Sub
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.parser.Parser

/** Subscription backup in OPML (export) and OPML or JSON (import), compatible with the web app. */
object Opml {

    fun export(subs: List<Sub>): String = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine("""<opml version="2.0">""")
        appendLine("""  <head><title>Frontek Reads subscriptions</title></head>""")
        appendLine("""  <body>""")
        for (s in subs) {
            appendLine("""    <outline type="rss" text="${esc(s.title)}" title="${esc(s.title)}" xmlUrl="${esc(s.feed)}" htmlUrl="${esc(s.site)}"/>""")
        }
        appendLine("""  </body>""")
        append("</opml>")
    }

    /** Parses either format; throws if the file is neither. */
    fun import(text: String): List<Sub> {
        if (Regex("^\\s*[\\[{]").containsMatchIn(text)) {
            return Json.parseToJsonElement(text).jsonArray.mapNotNull { e ->
                val o = e.jsonObject
                fun s(k: String) = o[k]?.jsonPrimitive?.contentOrNull.orEmpty()
                val feed = s("feed")
                if (feed.isEmpty()) null
                else Sub(s("title").ifEmpty { Discovery.hostOf(feed) }, feed, s("site").ifEmpty { Discovery.originOf(feed) })
            }
        }
        val doc = Jsoup.parse(text, "", Parser.xmlParser())
        return doc.getElementsByTag("outline").mapNotNull { o ->
            val feed = o.attr("xmlUrl").ifEmpty { o.attr("xmlurl") }
            if (feed.isEmpty()) null
            else Sub(
                title = o.attr("text").ifEmpty { o.attr("title") }.ifEmpty { Discovery.hostOf(feed) },
                feed = feed,
                site = o.attr("htmlUrl").ifEmpty { Discovery.originOf(feed) },
            )
        }
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}

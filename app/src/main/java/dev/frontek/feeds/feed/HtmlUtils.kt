package dev.frontek.feeds.feed

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.safety.Cleaner
import org.jsoup.safety.Safelist

/** HTML sanitizing, boilerplate cleaning and readability-lite extraction. */
object HtmlUtils {

    // "continue reading" / related markers, IT + EN.
    private val CONTINUE_RE = Regex(
        "(continua a leggere|clicca qui per continuare|leggi (tutto|l['’]articolo|anche|di più)|" +
            "continua »|read more|continue reading|\\[…\\]|\\[\\.\\.\\.\\])",
        RegexOption.IGNORE_CASE,
    )

    // class/id fragments that mark non-article boilerplate.
    private val JUNK_RE = Regex(
        "(share|social|related|correlat|leggi[-_]?anche|newsletter|subscribe|comment|commenti|" +
            "advert|(^|[-_ ])adv?([-_ ]|$)|banner|promo|sponsor|widget|sidebar|author[-_]?box|" +
            "post[-_]?tags|tag[-_]?list|breadcrumb|clickgo|outbrain|taboola|jp-relatedposts|wp-block-buttons)",
        RegexOption.IGNORE_CASE,
    )

    // affiliate / ad / tracking link targets
    private val JUNK_HREF_RE = Regex(
        "(/clickgo/|outbrain|taboola|doubleclick|googlesyndication|adservice|amzn\\.to|/aff[/_-]|utm_medium=affiliate)",
        RegexOption.IGNORE_CASE,
    )

    /** Plain-text preview of an HTML snippet, collapsed and truncated to 260 chars. */
    fun stripHtml(html: String?): String {
        val text = Jsoup.parse(html ?: "").text().replace(Regex("\\s+"), " ").trim()
        return if (text.length > 260) text.substring(0, 257) + "…" else text
    }

    /** Sanitize untrusted HTML with an allowlist; make links/images absolute. */
    fun sanitize(html: String, baseUri: String): String {
        val safelist = Safelist.relaxed()
            .addTags("figure", "figcaption", "picture", "source", "mark", "abbr", "time", "section", "article")
            .addAttributes("a", "href")
            .addAttributes("img", "src", "alt", "srcset")
            .addAttributes("source", "srcset", "src")
            .addProtocols("a", "href", "http", "https", "mailto")
            .addProtocols("img", "src", "http", "https")

        val dirty = Jsoup.parse(html, baseUri)
        val clean = Cleaner(safelist).clean(dirty)
        clean.setBaseUri(baseUri)
        clean.select("a[href]").forEach {
            it.attr("href", it.absUrl("href"))
            it.attr("target", "_blank")
            it.attr("rel", "noopener nofollow")
        }
        clean.select("img[src]").forEach { it.attr("src", it.absUrl("src")) }
        return clean.body().html()
    }

    /** Drop boilerplate elements from a subtree in place. */
    private fun stripJunk(root: Element) {
        // 1) elements whose class/id looks like boilerplate
        root.select("[class],[id]").forEach { n ->
            if (n.parentNode() == null) return@forEach
            val key = n.attr("class") + " " + n.attr("id")
            if (JUNK_RE.containsMatchIn(key)) n.remove()
        }
        // 1b) affiliate / ad / tracking links
        root.select("a[href]").forEach { n ->
            if (n.parentNode() == null) return@forEach
            if (JUNK_HREF_RE.containsMatchIn(n.attr("href"))) n.remove()
        }
        // 2) short "continue reading" / "leggi anche" blocks
        root.select("a, h1, h2, h3, h4, strong, p").forEach { n ->
            if (n.parentNode() == null) return@forEach
            val t = n.text().trim()
            if (t.isNotEmpty() && t.length < 70 && CONTINUE_RE.containsMatchIn(t)) {
                val wrap = n.closest("h1,h2,h3,h4,p,li,div") ?: n
                wrap.remove()
            }
        }
    }

    /** Clean a feed HTML snippet for display (returns an HTML string). */
    fun cleanFeedHtml(html: String, base: String): String {
        val doc = Jsoup.parse(html, base)
        stripJunk(doc.body())
        return doc.body().html()
    }

    /** Does the feed body look like a truncated excerpt rather than the full article? */
    fun isTruncated(html: String): Boolean {
        val text = Jsoup.parse(html).text().trim()
        if (CONTINUE_RE.containsMatchIn(text)) return true
        return text.length < 900
    }

    private val SELECTORS = listOf(
        "[itemprop=articleBody]", "article .entry-content", ".entry-content",
        ".post-content", ".article-content", ".article-body", ".articleBody",
        ".post-body", ".td-post-content", ".single-post-content", ".post__content",
        ".article__content", ".content__article-body", "main article", "article",
    )

    /** Readability-lite extraction: returns extracted HTML and its text length. */
    fun extractArticle(html: String, base: String): Pair<String, Int> {
        val doc: Document = Jsoup.parse(html, base)
        listOf("script", "style", "nav", "header", "footer", "aside", "form", "noscript", "iframe", "svg")
            .forEach { doc.select(it).remove() }

        // 1) well-known content containers
        var container: Element? = null
        for (selector in SELECTORS) {
            val c = doc.selectFirst(selector)
            if (c != null && c.text().trim().length > 400) {
                container = c
                break
            }
        }

        // 2) fallback: score blocks by paragraph text, penalised by link density
        if (container == null) {
            var best: Element? = null
            var bestScore = 0.0
            for (el in doc.select("div, section, article, main")) {
                val text = el.text().trim()
                if (text.length < 200) continue
                var pLen = 0
                for (p in el.select("p")) {
                    val l = p.text().trim().length
                    if (l > 40) pLen += l
                }
                if (pLen == 0) continue
                var linkLen = 0
                for (a in el.select("a")) linkLen += a.text().length
                val density = if (text.isNotEmpty()) linkLen.toDouble() / text.length else 1.0
                var score = pLen * (1 - minOf(density, 0.9))
                if (el.tagName().equals("article", ignoreCase = true)) score *= 1.2
                if (score > bestScore) {
                    bestScore = score
                    best = el
                }
            }
            container = best
        }

        if (container == null) container = doc.body()
        stripJunk(container)
        return container.html() to container.text().trim().length
    }
}

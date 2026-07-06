package dev.frontek.feeds.feed

import org.json.JSONArray
import org.json.JSONObject
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

    // class/id weighting for the scoring fallback (Readability-style).
    private val POSITIVE_RE = Regex(
        "(article|artikel|body|content|entry|main|post|text|blog|story|column|read|prose)",
        RegexOption.IGNORE_CASE,
    )
    private val NEGATIVE_RE = Regex(
        "(comment|meta|footer|foot|masthead|nav|sidebar|sponsor|share|social|related|promo|" +
            "banner|advert|(^|[-_ ])ad([-_ ]|$)|widget|popup|newsletter|subscribe|cookie|hidden|" +
            "menu|header|breadcrumb|pagination)",
        RegexOption.IGNORE_CASE,
    )

    private val ARTICLE_LD_TYPES = setOf(
        "article", "newsarticle", "blogposting", "report", "reportagenewsarticle",
        "liveblogposting", "techarticle", "scholarlyarticle", "medicalscholarlyarticle",
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
        promoteLazyImages(dirty)
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
        promoteLazyImages(doc)
        stripJunk(doc.body())
        return doc.body().html()
    }

    /** Does the feed body look like a truncated excerpt rather than the full article? */
    fun isTruncated(html: String): Boolean {
        val text = Jsoup.parse(html).text().trim()
        if (CONTINUE_RE.containsMatchIn(text)) return true
        return text.length < 900
    }

    /** The site's AMP version, if it advertises one — often cleaner to extract. */
    fun findAmpUrl(html: String, base: String): String? {
        val doc = Jsoup.parse(html, base)
        val link = doc.selectFirst("link[rel=amphtml]") ?: return null
        return link.absUrl("href").ifBlank { null }
    }

    private val SELECTORS = listOf(
        "[itemprop=articleBody]", "[role=main] article", "article .entry-content",
        ".entry-content", ".post-content", ".article-content", ".article-body",
        ".article__body", ".article__content", ".articleBody", ".post-body",
        ".td-post-content", ".single-post-content", ".post__content", ".post-entry",
        ".content__article-body", ".story-body", ".story-content", ".c-article-body",
        ".rich-text", ".body-text", ".node__content", ".sqs-block-content",
        ".wp-block-post-content", ".elementor-widget-theme-post-content",
        "main article", "[role=main]", "article", "main",
    )

    /**
     * Readability-lite extraction: returns extracted HTML and its text length.
     * Tries, in order: schema.org JSON-LD `articleBody`, well-known content
     * containers, then a scored block search — whichever yields the most text.
     */
    fun extractArticle(html: String, base: String): Pair<String, Int> {
        val doc: Document = Jsoup.parse(html, base)
        promoteLazyImages(doc)

        // JSON-LD is read before scripts are stripped for the DOM pass.
        val jsonBody = jsonLdArticleBody(doc)

        listOf("script", "style", "nav", "header", "footer", "aside", "form", "noscript", "iframe", "svg")
            .forEach { doc.select(it).remove() }
        doc.select("[hidden], [aria-hidden=true], [style*=display:none], [style*=display: none]").remove()

        // 1) well-known content containers
        var container: Element? = null
        for (selector in SELECTORS) {
            val c = doc.selectFirst(selector)
            if (c != null && c.text().trim().length > 300) {
                container = c
                break
            }
        }

        // 2) fallback: score blocks by paragraph text, penalised by link density
        if (container == null) {
            container = scoreBestBlock(doc)
        }

        if (container == null) container = doc.body()
        stripJunk(container)
        val domHtml = container.html()
        val domLen = container.text().trim().length

        // 3) prefer JSON-LD only when it is substantially more complete than the DOM
        // (the DOM keeps images and formatting, so we don't override it lightly).
        if (jsonBody != null && jsonBody.length > (domLen * 1.4).toInt() && jsonBody.length > 400) {
            return textToParagraphs(jsonBody) to jsonBody.length
        }
        return domHtml to domLen
    }

    private fun scoreBestBlock(doc: Document): Element? {
        var best: Element? = null
        var bestScore = 0.0
        for (el in doc.select("div, section, article, main")) {
            val text = el.text().trim()
            if (text.length < 160) continue
            var pLen = 0
            var pCount = 0
            for (p in el.select("p")) {
                val l = p.text().trim().length
                if (l > 30) {
                    pLen += l
                    pCount++
                }
            }
            if (pLen == 0) continue
            var linkLen = 0
            for (a in el.select("a")) linkLen += a.text().length
            val density = if (text.isNotEmpty()) linkLen.toDouble() / text.length else 1.0
            var score = pLen * (1 - minOf(density, 0.9))
            score += pCount * 12                       // reward many paragraphs
            score += text.count { it == ',' } * 3.0    // reward prose (comma count)
            val classId = el.className() + " " + el.id()
            if (POSITIVE_RE.containsMatchIn(classId)) score *= 1.35
            if (NEGATIVE_RE.containsMatchIn(classId)) score *= 0.35
            if (el.tagName().equals("article", ignoreCase = true)) score *= 1.2
            if (score > bestScore) {
                bestScore = score
                best = el
            }
        }
        return best
    }

    /** Promote lazy-loaded image URLs (data-src / srcset) into a usable `src`. */
    private fun promoteLazyImages(doc: Document) {
        for (img in doc.select("img")) {
            val src = img.attr("src")
            if (src.isNotBlank() && !src.startsWith("data:")) continue
            val candidate = sequenceOf(
                img.attr("data-src"),
                img.attr("data-lazy-src"),
                img.attr("data-original"),
                img.attr("data-lazy"),
                img.attr("data-url"),
                largestFromSrcset(img.attr("data-srcset")),
                largestFromSrcset(img.attr("srcset")),
            ).firstOrNull { !it.isNullOrBlank() }
            if (!candidate.isNullOrBlank()) img.attr("src", candidate)
        }
    }

    /** Pick the highest-resolution URL from a `srcset` value. */
    private fun largestFromSrcset(srcset: String?): String? {
        if (srcset.isNullOrBlank()) return null
        var best: String? = null
        var bestW = -1
        for (part in srcset.split(",")) {
            val seg = part.trim().split(Regex("\\s+"))
            val url = seg.firstOrNull()?.trim().orEmpty()
            if (url.isBlank()) continue
            val w = seg.getOrNull(1)?.trimEnd('w', 'x', ' ')?.toIntOrNull() ?: 0
            if (w >= bestW) {
                bestW = w
                best = url
            }
        }
        return best
    }

    // ---- JSON-LD (schema.org) ----

    private fun jsonLdArticleBody(doc: Document): String? {
        for (script in doc.select("script[type=\"application/ld+json\"]")) {
            val raw = script.data().ifBlank { script.html() }.trim()
            if (raw.isBlank()) continue
            val body = try {
                articleBodyFromLd(raw)
            } catch (e: Exception) {
                null
            }
            if (!body.isNullOrBlank()) return body
        }
        return null
    }

    private fun articleBodyFromLd(raw: String): String? {
        val nodes = mutableListOf<JSONObject>()
        fun collect(any: Any?) {
            when (any) {
                is JSONObject -> {
                    nodes.add(any)
                    any.optJSONArray("@graph")?.let { g ->
                        for (i in 0 until g.length()) collect(g.opt(i))
                    }
                }
                is JSONArray -> for (i in 0 until any.length()) collect(any.opt(i))
            }
        }
        val root: Any = if (raw.startsWith("[")) JSONArray(raw) else JSONObject(raw)
        collect(root)

        // prefer a node explicitly typed as an article
        for (node in nodes) {
            if (isArticleLdType(node)) {
                val body = node.optString("articleBody").trim()
                if (body.length > 200) return body
            }
        }
        // otherwise any node that carries an articleBody
        for (node in nodes) {
            val body = node.optString("articleBody").trim()
            if (body.length > 200) return body
        }
        return null
    }

    private fun isArticleLdType(node: JSONObject): Boolean {
        val type = node.opt("@type") ?: return false
        return when (type) {
            is String -> type.lowercase() in ARTICLE_LD_TYPES
            is JSONArray -> (0 until type.length()).any { type.optString(it).lowercase() in ARTICLE_LD_TYPES }
            else -> false
        }
    }

    private fun textToParagraphs(text: String): String =
        text.split(Regex("\\r\\n|\\n|\\u2029|\\u2028"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("") { "<p>${escapeHtml(it)}</p>" }

    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}

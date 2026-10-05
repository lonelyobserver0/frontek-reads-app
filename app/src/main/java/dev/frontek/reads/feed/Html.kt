package dev.frontek.reads.feed

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * HTML handling for feed content and full articles: allow-list sanitizer,
 * boilerplate stripping and readability-style extraction. Ports of the web
 * app's sanitizeHtml / stripJunk / extractArticle.
 */
object Html {

    fun stripHtml(html: String): String {
        val tx = Jsoup.parse(html).text().replace(Regex("\\s+"), " ").trim()
        return if (tx.length > 260) tx.substring(0, 257) + "…" else tx
    }

    // ---------- sanitizing ----------

    private val ALLOWED = setOf(
        "a", "p", "br", "hr", "b", "strong", "i", "em", "u", "s", "small", "mark",
        "h1", "h2", "h3", "h4", "h5", "h6", "ul", "ol", "li", "blockquote", "q", "cite",
        "pre", "code", "kbd", "samp", "figure", "figcaption", "img", "picture", "source",
        "table", "thead", "tbody", "tfoot", "tr", "td", "th", "caption", "colgroup", "col",
        "span", "div", "section", "article", "time", "abbr", "sub", "sup", "dl", "dt", "dd",
    )
    private val STRIP_TAGS = setOf(
        "script", "style", "iframe", "object", "embed", "form", "input", "button", "select",
        "textarea", "link", "meta", "noscript", "svg", "canvas", "video", "audio",
        "header", "footer", "nav", "aside",
    )

    private fun safeUrl(u: String?): String {
        val s = u?.trim().orEmpty()
        if (s.isEmpty() || Regex("^(javascript|data|vbscript|file):", RegexOption.IGNORE_CASE).containsMatchIn(s)) return ""
        return s
    }

    fun resolveUrl(href: String, base: String): String =
        runCatching { java.net.URI(base).resolve(href.replace(" ", "%20")).toString() }.getOrDefault(href)

    /** Rebuilds the markup from allowed tags only; no attributes survive except safe href/src. */
    fun sanitize(html: String, base: String): String {
        val src = Jsoup.parseBodyFragment(html, base).body()
        val out = Document.createShell(base).body()
        fun walk(from: Element, dest: Element) {
            for (n in from.childNodes()) {
                if (n is TextNode) { dest.appendChild(TextNode(n.wholeText)); continue }
                if (n !is Element) continue
                val tag = n.normalName()
                if (tag in STRIP_TAGS) continue
                if (tag !in ALLOWED) { walk(n, dest); continue }
                val el = Element(tag)
                when (tag) {
                    "a" -> safeUrl(n.attr("href")).takeIf { it.isNotEmpty() }?.let { el.attr("href", resolveUrl(it, base)) }
                    "img" -> {
                        val s = safeUrl(n.attr("src").ifEmpty { n.attr("data-src") })
                        if (s.isEmpty()) continue
                        el.attr("src", resolveUrl(s, base)).attr("alt", n.attr("alt")).attr("loading", "lazy")
                    }
                    "source" -> {
                        val ss = safeUrl(n.attr("srcset").ifEmpty { n.attr("src") })
                        if (ss.startsWith("http", ignoreCase = true)) el.attr("srcset", ss)
                    }
                }
                walk(n, el)
                dest.appendChild(el)
            }
        }
        walk(src, out)
        return out.html()
    }

    // ---------- content cleaning ----------

    private val CONTINUE_RE = Regex(
        "(continua a leggere|clicca qui per continuare|leggi (tutto|l['’]articolo|anche|di più)|continua »|read more|continue reading|\\[…]|\\[\\.\\.\\.])",
        RegexOption.IGNORE_CASE,
    )
    private val JUNK_RE = Regex(
        "(share|social|related|correlat|leggi[-_]?anche|newsletter|subscribe|comment|commenti|advert|(^|[-_ ])adv?([-_ ]|$)|banner|promo|sponsor|widget|sidebar|author[-_]?box|post[-_]?tags|tag[-_]?list|breadcrumb|clickgo|outbrain|taboola|jp-relatedposts|wp-block-buttons)",
        RegexOption.IGNORE_CASE,
    )
    private val JUNK_HREF_RE = Regex(
        "(/clickgo/|outbrain|taboola|doubleclick|googlesyndication|adservice|amzn\\.to|/aff[/_-]|utm_medium=affiliate|google\\.[a-z.]+/preferences/source)",
        RegexOption.IGNORE_CASE,
    )
    // Short trailing lines that aren't article text ("Fonte dell'articolo: www.engadget.com").
    // Kept out of CONTINUE_RE, which also decides whether a feed item is truncated.
    private val BOILERPLATE_RE = Regex("^(fonte dell['’]articolo|article source)\\s*:", RegexOption.IGNORE_CASE)

    private fun stripJunk(root: Element) {
        // A node holding most of the text is the article itself, whatever its class
        // says (e.g. tomshw.it wraps the body in "adv__parsed__content"): keep it and
        // let the loop strip the real junk nested inside.
        val rootLen = root.text().length
        for (n in root.select("[class],[id]")) {
            if (n === root || n.parent() == null) continue
            if (!JUNK_RE.containsMatchIn(n.className() + " " + n.id())) continue
            if (rootLen > 400 && n.text().length > rootLen * 0.5) continue
            n.remove()
        }
        for (n in root.select("a[href]")) {
            if (n.parent() != null && JUNK_HREF_RE.containsMatchIn(n.attr("href"))) n.remove()
        }
        for (n in root.select("a, h1, h2, h3, h4, strong, p")) {
            if (n.parent() == null) continue
            val tx = n.text()
            if (tx.isNotEmpty() && tx.length < 70 && (CONTINUE_RE.containsMatchIn(tx) || BOILERPLATE_RE.containsMatchIn(tx))) {
                (n.closest("h1,h2,h3,h4,p,li,div")?.takeIf { it !== root } ?: n).remove()
            }
        }
    }

    fun cleanFeedHtml(html: String): String {
        val body = Jsoup.parseBodyFragment(html).body()
        stripJunk(body)
        return body.html()
    }

    /** Feed content that is a teaser rather than the whole article. */
    fun isTruncated(html: String): Boolean {
        val text = Jsoup.parse(html).text()
        return CONTINUE_RE.containsMatchIn(text) || text.length < 900
    }

    // ---------- full-article extraction ----------

    data class Extracted(val html: String, val chars: Int)

    private val SELECTORS = listOf(
        "[itemprop=articleBody]", "article .entry-content", ".entry-content",
        ".post-content", ".article-content", ".article-body", ".articleBody", ".post-body",
        ".td-post-content", ".single-post-content", ".post__content", ".article__content",
        ".content__article-body", "main article", "article",
    )

    fun extractArticle(html: String, base: String): Extracted {
        val doc = Jsoup.parse(html, base)
        doc.select("script, style, nav, header, footer, aside, form, noscript, iframe, svg").remove()

        var container: Element? = SELECTORS.firstNotNullOfOrNull { sel ->
            doc.selectFirst(sel)?.takeIf { it.text().length > 400 }
        }
        if (container == null) {
            var best: Element? = null
            var bestScore = 0.0
            for (el in doc.select("div, section, article, main")) {
                val textLen = el.text().length
                if (textLen < 200) continue
                val pLen = el.select("p").sumOf { p -> p.text().length.let { if (it > 40) it else 0 } }
                if (pLen == 0) continue
                val linkLen = el.select("a").sumOf { it.text().length }
                val density = linkLen.toDouble() / textLen
                var score = pLen * (1 - minOf(density, 0.9))
                if (el.normalName() == "article") score *= 1.2
                if (score > bestScore) { bestScore = score; best = el }
            }
            container = best
        }
        val root = container ?: doc.body()
        stripJunk(root)
        // Headings left orphaned at the end once their widget is gone ("Altre offerte consigliate").
        for (h in root.select("h1,h2,h3,h4,h5,h6").reversed()) {
            if (textAfter(h, root).isNotBlank()) break
            h.remove()
        }
        return Extracted(root.html(), root.text().length)
    }

    /** Text that follows [node] inside [root] in document order. */
    private fun textAfter(node: Node, root: Element): String {
        val sb = StringBuilder()
        var cur: Node? = node
        while (cur != null && cur !== root) {
            var sib = cur.nextSibling()
            while (sib != null) {
                sb.append(if (sib is Element) sib.text() else if (sib is TextNode) sib.text() else "")
                sib = sib.nextSibling()
            }
            cur = cur.parent()
        }
        return sb.toString()
    }
}

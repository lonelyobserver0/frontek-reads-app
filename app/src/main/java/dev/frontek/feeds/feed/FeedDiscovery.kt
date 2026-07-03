package dev.frontek.feeds.feed

import dev.frontek.feeds.net.Http
import org.jsoup.Jsoup

/** Result of resolving a site/feed URL to an actual feed. */
data class Discovered(val feed: String, val title: String, val site: String)

/**
 * Given a site or feed URL, resolve it to a real RSS/Atom feed.
 * Strategy: is it already a feed? → declared <link rel="alternate">/feed links →
 * feed-looking <a> links on the page → common feed paths. Every candidate is
 * verified by actually parsing it, so we never subscribe to a dead URL.
 */
object FeedDiscovery {

    private const val MAX_CANDIDATES = 8

    private val COMMON_PATHS = listOf(
        "/feed/", "/feed", "/rss", "/rss/", "/rss.xml", "/feed.xml", "/atom.xml", "/atom",
        "/index.xml", "/index.rss", "/feed/rss", "/feed/atom", "/rss/all.xml", "/blog/feed/",
        "/blog/rss", "/?feed=rss2", "/feeds/posts/default", "/feeds/posts/default?alt=rss",
    )

    suspend fun discover(input: String): Discovered {
        val url = UrlUtils.normalize(input)
        if (url.isEmpty()) throw IllegalArgumentException("Empty URL")

        val text = Http.fetchText(url)

        // 1) Already a feed?
        val parsed = FeedParser.parse(text)
        if (parsed.items.isNotEmpty()) {
            return Discovered(
                feed = url,
                title = parsed.title.ifEmpty { UrlUtils.host(url) },
                site = UrlUtils.origin(url),
            )
        }

        // 2) Treat as HTML: collect declared feeds and feed-looking links, verify each.
        val doc = Jsoup.parse(text, url)
        val pageTitle = doc.selectFirst("title")?.text()?.trim().orEmpty()
        val candidates = LinkedHashSet<String>()

        doc.select("link[rel=alternate], link[rel=feed]").forEach { link ->
            val type = link.attr("type").lowercase()
            if (type.contains("rss") || type.contains("atom") || type.contains("xml") || type.contains("json")) {
                link.absUrl("href").takeIf { it.isNotBlank() }?.let { candidates.add(it) }
            }
        }
        doc.select("a[href]").forEach { a ->
            val href = a.absUrl("href")
            if (href.isNotBlank() && looksLikeFeedLink(href)) candidates.add(href)
        }

        for (candidate in candidates.take(MAX_CANDIDATES)) {
            verify(candidate, pageTitle)?.let { return it }
        }

        // 3) Last resort: probe common feed paths.
        return probeCommonPaths(url, pageTitle)
    }

    private fun looksLikeFeedLink(href: String): Boolean {
        val h = href.lowercase()
        if (h.contains("comment")) return false // skip WordPress comment feeds
        return h.contains("/feed") || h.contains("/rss") || h.contains("atom") ||
            h.contains("feed=rss") || h.endsWith(".rss") || h.endsWith(".xml")
    }

    private suspend fun verify(candidate: String, pageTitle: String): Discovered? = try {
        val text = Http.fetchText(candidate)
        val parsed = FeedParser.parse(text)
        if (parsed.items.isNotEmpty()) {
            Discovered(
                feed = candidate,
                title = parsed.title.ifEmpty { pageTitle.ifEmpty { UrlUtils.host(candidate) } },
                site = UrlUtils.origin(candidate),
            )
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }

    private suspend fun probeCommonPaths(siteUrl: String, pageTitle: String): Discovered {
        val origin = UrlUtils.origin(siteUrl)
        for (path in COMMON_PATHS) {
            verify(origin + path, pageTitle)?.let { return it }
        }
        throw NoSuchElementException("No feed found on that site")
    }
}

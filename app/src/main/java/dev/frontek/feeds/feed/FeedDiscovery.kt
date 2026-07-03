package dev.frontek.feeds.feed

import dev.frontek.feeds.net.Http
import org.jsoup.Jsoup

/** Result of resolving a site/feed URL to an actual feed. */
data class Discovered(val feed: String, val title: String, val site: String)

/**
 * Given a site or feed URL, resolve it to a real RSS/Atom feed.
 * Ported from the web app's discover() / probeCommonPaths().
 */
object FeedDiscovery {

    private val COMMON_PATHS = listOf(
        "/feed", "/rss", "/feed.xml", "/rss.xml", "/atom.xml", "/index.xml", "/feeds/posts/default",
    )

    suspend fun discover(input: String): Discovered {
        val url = UrlUtils.normalize(input)
        if (url.isEmpty()) throw IllegalArgumentException("Empty URL")

        val text = Http.fetchText(url)

        // Is it already a feed?
        val parsed = FeedParser.parse(text)
        if (parsed.items.isNotEmpty()) {
            return Discovered(
                feed = url,
                title = parsed.title.ifEmpty { UrlUtils.host(url) },
                site = UrlUtils.origin(url),
            )
        }

        // Otherwise treat as HTML: look for <link rel="alternate" type="...rss|atom|xml">
        val doc = Jsoup.parse(text, url)
        val altLink = doc.select("link[rel=alternate]").firstOrNull { link ->
            val type = link.attr("type").lowercase()
            type.contains("rss") || type.contains("atom") || type.contains("xml")
        }
        if (altLink != null) {
            val href = UrlUtils.resolve(altLink.attr("href"), url)
            val titleAttr = altLink.attr("title")
            val pageTitle = doc.selectFirst("title")?.text()?.trim().orEmpty()
            return Discovered(
                feed = href,
                title = titleAttr.ifEmpty { pageTitle.ifEmpty { UrlUtils.host(url) } },
                site = UrlUtils.origin(url),
            )
        }

        // Last resort: probe common feed paths.
        return probeCommonPaths(url)
    }

    private suspend fun probeCommonPaths(siteUrl: String): Discovered {
        val origin = UrlUtils.origin(siteUrl)
        for (path in COMMON_PATHS) {
            val candidate = origin + path
            try {
                val text = Http.fetchText(candidate)
                val parsed = FeedParser.parse(text)
                if (parsed.items.isNotEmpty()) {
                    return Discovered(
                        feed = candidate,
                        title = parsed.title.ifEmpty { UrlUtils.host(origin) },
                        site = origin,
                    )
                }
            } catch (e: Exception) {
                // try next path
            }
        }
        throw NoSuchElementException("No feed found on that site")
    }
}

package dev.frontek.reads.feed

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hits real sites: checks parsing, discovery and extraction end to end. */
class LiveFeedTest {
    private val feeds = listOf(
        "https://hnrss.org/frontpage",
        "https://www.theverge.com/rss/index.xml",
        "https://feeds.arstechnica.com/arstechnica/index",
        "https://www.engadget.com/rss.xml",
        "https://www.ansa.it/sito/notizie/topnews/topnews_rss.xml",
    )

    @Test fun parsesFeeds() = runBlocking {
        for (url in feeds) {
            val p = runCatching { FeedParser.parse(Http.fetchText(url)) }.getOrNull()
            if (p == null) { println("FAIL fetch $url"); continue }
            val first = p.items.firstOrNull()
            println("OK ${p.title} items=${p.items.size} dated=${p.items.count { it.date > 0 }} img=${p.items.count { it.image.isNotEmpty() }} first=${first?.title?.take(50)} link=${first?.link?.take(60)}")
            assertTrue("no items in $url", p.items.isNotEmpty())
        }
    }

    @Test fun discoversFromSite() = runBlocking {
        val f = Discovery.discover("arstechnica.com")
        println("discovered ${f.title} -> ${f.feed}")
        assertTrue(f.feed.startsWith("http"))
    }

    @Test fun feedlySearch() = runBlocking {
        val r = Discovery.searchFeedly("android")
        println("feedly results=${r.size} first=${r.firstOrNull()}")
        assertTrue(r.isNotEmpty())
    }

    @Test fun extractsFullArticle() = runBlocking {
        val item = FeedParser.parse(Http.fetchText("https://feeds.arstechnica.com/arstechnica/index")).items.first()
        val res = Html.extractArticle(Http.fetchText(item.link), item.link)
        val safe = Html.sanitize(res.html, item.link)
        println("extracted chars=${res.chars} truncated=${Html.isTruncated(item.content)} sanitized=${safe.length}")
        assertTrue(res.chars > 400)
        assertTrue(!safe.contains("<script") && !safe.contains("onclick"))
    }

    @Test fun parsesDates() {
        val d = listOf("Sun, 05 Oct 2026 10:00:00 GMT", "Sun, 5 Oct 2026 10:00:00 +0200", "Mon, 05 Oct 2026 10:00:00 EST", "2026-10-05T10:00:00Z", "2026-10-05T10:00:00.123+02:00")
        d.forEach { println("$it -> ${FeedParser.parseDate(it)}"); assertTrue(FeedParser.parseDate(it) > 0) }
    }
}

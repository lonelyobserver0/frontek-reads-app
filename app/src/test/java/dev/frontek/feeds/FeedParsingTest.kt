package dev.frontek.feeds

import dev.frontek.feeds.feed.DateParser
import dev.frontek.feeds.feed.FeedParser
import dev.frontek.feeds.feed.HtmlUtils
import dev.frontek.feeds.feed.UrlUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** JVM tests for the core feed logic (Jsoup-based, no Android framework needed). */
class FeedParsingTest {

    @Test
    fun parsesRss2() {
        val xml = """
            <?xml version="1.0"?>
            <rss version="2.0"><channel>
              <title>My Blog</title>
              <item>
                <title>Hello World</title>
                <link>https://example.com/hello</link>
                <description><![CDATA[<p>Body <b>text</b> goes here for the reader.</p>]]></description>
                <pubDate>Wed, 02 Oct 2024 13:00:00 GMT</pubDate>
                <guid>abc-123</guid>
              </item>
            </channel></rss>
        """.trimIndent()

        val feed = FeedParser.parse(xml)
        assertEquals("My Blog", feed.title)
        assertEquals(1, feed.items.size)
        val item = feed.items[0]
        assertEquals("Hello World", item.title)
        assertEquals("https://example.com/hello", item.link)
        assertEquals("abc-123", item.id)
        assertTrue(item.content.contains("<b>text</b>"))
        assertTrue(item.summary.contains("Body text goes here"))
        assertTrue(item.date > 0L)
    }

    @Test
    fun parsesAtom() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>Atom Feed</title>
              <entry>
                <title>An Atom Post</title>
                <link rel="alternate" href="https://example.org/post"/>
                <content type="html">&lt;p&gt;Rich atom content here.&lt;/p&gt;</content>
                <updated>2024-10-02T13:00:00Z</updated>
                <id>urn:id:1</id>
              </entry>
            </feed>
        """.trimIndent()

        val feed = FeedParser.parse(xml)
        assertEquals("Atom Feed", feed.title)
        assertEquals(1, feed.items.size)
        val item = feed.items[0]
        assertEquals("An Atom Post", item.title)
        assertEquals("https://example.org/post", item.link)
        assertEquals("urn:id:1", item.id)
        assertTrue(item.content.contains("<p>Rich atom content here.</p>"))
        assertTrue(item.date > 0L)
    }

    @Test
    fun extractsImageFromMediaThumbnail() {
        val xml = """
            <?xml version="1.0"?>
            <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/"><channel>
              <title>Pics</title>
              <item>
                <title>With thumb</title>
                <link>https://example.com/a</link>
                <media:thumbnail url="https://img.example.com/pic.jpg"/>
                <description><![CDATA[<p>Body</p>]]></description>
              </item>
            </channel></rss>
        """.trimIndent()
        assertEquals("https://img.example.com/pic.jpg", FeedParser.parse(xml).items[0].image)
    }

    @Test
    fun extractsImageFromContentImg() {
        val xml = """
            <?xml version="1.0"?>
            <rss version="2.0"><channel>
              <title>Pics</title>
              <item>
                <title>With inline img</title>
                <link>https://example.com/b</link>
                <description><![CDATA[<p><img src="https://cdn.example.com/inline.jpg"/> some text</p>]]></description>
              </item>
            </channel></rss>
        """.trimIndent()
        assertEquals("https://cdn.example.com/inline.jpg", FeedParser.parse(xml).items[0].image)
    }

    @Test
    fun noImageWhenNone() {
        val xml = """
            <?xml version="1.0"?>
            <rss version="2.0"><channel><title>Plain</title>
              <item><title>No pic</title><link>https://example.com/c</link>
              <description>Just text</description></item>
            </channel></rss>
        """.trimIndent()
        assertEquals(null, FeedParser.parse(xml).items[0].image)
    }

    @Test
    fun parsesDates() {
        assertTrue(DateParser.parse("Wed, 02 Oct 2024 13:00:00 GMT") > 0)
        assertTrue(DateParser.parse("2024-10-02T13:00:00Z") > 0)
        assertTrue(DateParser.parse("2024-10-02T13:00:00+02:00") > 0)
        assertEquals(0L, DateParser.parse(""))
        assertEquals(0L, DateParser.parse("not a date"))
    }

    @Test
    fun sanitizeStripsScripts() {
        val dirty = "<p>Hi</p><script>alert(1)</script><a href=\"/rel\">link</a>"
        val clean = HtmlUtils.sanitize(dirty, "https://site.com/base/")
        assertTrue(!clean.contains("<script"))
        assertTrue(clean.contains("https://site.com/rel"))
    }

    @Test
    fun urlHelpers() {
        assertEquals("https://foo.com", UrlUtils.normalize("foo.com"))
        assertEquals("example.com", UrlUtils.host("https://www.example.com/path"))
        assertEquals("https://example.com", UrlUtils.origin("https://example.com/path?q=1"))
        assertTrue(UrlUtils.looksLikeUrl("theverge.com"))
        assertTrue(!UrlUtils.looksLikeUrl("just some text"))
    }
}

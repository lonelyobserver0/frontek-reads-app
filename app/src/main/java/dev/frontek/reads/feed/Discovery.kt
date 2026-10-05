package dev.frontek.reads.feed

import dev.frontek.reads.data.CatalogEntry
import dev.frontek.reads.data.FoundFeed
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.text.Normalizer

/** Finding feeds: URL auto-discovery, Feedly search and the built-in catalog. */
object Discovery {

    fun normalizeUrl(input: String): String {
        val s = input.trim()
        if (s.isEmpty()) return ""
        return if (Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(s)) s else "https://$s"
    }

    fun looksLikeUrl(s: String): Boolean {
        val t = s.trim()
        return Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(t) ||
            (!t.contains(' ') && Regex("\\.[a-z]{2,}(/|$)", RegexOption.IGNORE_CASE).containsMatchIn(t))
    }

    fun hostOf(u: String): String = runCatching { URI(u).host!!.removePrefix("www.") }.getOrDefault(u)

    fun originOf(u: String): String = runCatching {
        val uri = URI(u)
        "${uri.scheme}://${uri.authority}"
    }.getOrDefault(u)

    // ---------- auto-discovery ----------

    private val COMMON_PATHS = listOf(
        "/feed/", "/feed", "/rss", "/rss.xml", "/feed.xml", "/atom.xml", "/atom",
        "/index.xml", "/index.rss", "/feed/rss", "/?feed=rss2", "/feeds/posts/default?alt=rss",
    )

    /**
     * Turns any site or feed URL into a feed: the URL itself if it is a feed, else
     * the page's <link rel=alternate> / feed-looking links, else common feed paths.
     */
    suspend fun discover(input: String): FoundFeed {
        val url = normalizeUrl(input)
        if (url.isEmpty()) throw IOException("Empty URL")
        val txt = Http.fetchText(url)
        val parsed = runCatching { FeedParser.parse(txt) }.getOrNull()
        if (parsed != null && parsed.items.isNotEmpty()) {
            return FoundFeed(parsed.title.ifEmpty { hostOf(url) }, url, originOf(url))
        }
        val doc = Jsoup.parse(txt, url)
        val pageTitle = doc.title().trim().ifEmpty { hostOf(url) }
        collectFeedCandidates(doc).forEach { tryFeed(it, pageTitle)?.let { f -> return f } }
        val origin = originOf(url)
        COMMON_PATHS.forEach { p -> tryFeed(origin + p, pageTitle, origin)?.let { f -> return f } }
        throw IOException("No feed found on that site")
    }

    private suspend fun tryFeed(url: String, pageTitle: String, site: String = originOf(url)): FoundFeed? = try {
        val p = FeedParser.parse(Http.fetchText(url))
        if (p.items.isNotEmpty()) FoundFeed(p.title.ifEmpty { pageTitle }, url, site) else null
    } catch (e: Exception) {
        null
    }

    private fun collectFeedCandidates(doc: Document): List<String> {
        val out = LinkedHashSet<String>()
        fun add(href: String) {
            if (href.isNotEmpty() && href.startsWith("http", ignoreCase = true)) out.add(href)
        }
        for (l in doc.select("link[rel=alternate], link[rel=feed]")) {
            val ty = l.attr("type").lowercase()
            if (listOf("rss", "atom", "xml", "json").any { it in ty }) add(l.absUrl("href"))
        }
        val feedish = Regex("(/feed(/|$)|/rss|atom|\\.xml|feed=rss)", RegexOption.IGNORE_CASE)
        for (a in doc.select("a[href]")) {
            val h = a.attr("href")
            if (h.contains("comment", ignoreCase = true)) continue
            if (feedish.containsMatchIn(h)) add(a.absUrl("href"))
        }
        return out.toList()
    }

    // ---------- Feedly search ----------

    private const val FEEDLY_SEARCH = "https://cloud.feedly.com/v3/search/feeds?count=20&query="
    private val json = Json { ignoreUnknownKeys = true }

    /** Web-wide feed search. The only place a user-typed query leaves the device. */
    suspend fun searchFeedly(query: String): List<FoundFeed> {
        val txt = Http.fetchText(FEEDLY_SEARCH + URLEncoder.encode(query, "UTF-8"))
        val results = json.parseToJsonElement(txt).jsonObject["results"]?.jsonArray ?: return emptyList()
        return results.mapNotNull { r ->
            val o = r.jsonObject
            fun s(k: String) = o[k]?.jsonPrimitive?.contentOrNull.orEmpty()
            val feed = s("feedId").removePrefix("feed/")
            if (feed.isEmpty()) return@mapNotNull null
            FoundFeed(
                title = s("title").ifEmpty { hostOf(feed) },
                feed = feed,
                site = s("website").ifEmpty { originOf(feed) },
                description = s("description"),
                icon = s("iconUrl").ifEmpty { s("visualUrl") },
            )
        }
    }

    // ---------- catalog matching ----------

    fun deburr(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

    private val CAT_SYNONYMS = mapOf(
        "Tech" to listOf("tech", "technology", "tecnologia", "tecnologie", "informatica", "tecnología", "technologie", "gadget"),
        "Tech IT" to listOf("tech", "tecnologia", "informatica", "italia", "italiano", "technology"),
        "News" to listOf("news", "notizie", "attualita", "actualite", "noticias", "actualites", "world", "mondo"),
        "News IT" to listOf("news", "notizie", "attualita", "italia", "italiano", "cronaca"),
        "Dev" to listOf("dev", "developer", "sviluppo", "programmazione", "programming", "desarrollo", "developpement", "code", "coding", "web"),
        "Science" to listOf("science", "scienza", "ciencia", "sciences", "scienze", "research", "ricerca"),
        "Gaming" to listOf("gaming", "games", "giochi", "videogiochi", "videojuegos", "jeux", "gioco", "game", "videogame"),
        "Fun" to listOf("fun", "divertimento", "svago", "humor", "humour", "diversion", "ocio", "comics"),
    ).mapValues { (_, v) -> v.map(::deburr) }

    fun matchCatalog(catalog: List<CatalogEntry>, query: String): List<CatalogEntry> {
        val dq = deburr(query).trim()
        if (dq.isEmpty()) return catalog
        val tokens = dq.split(Regex("\\s+"))
        fun rank(e: CatalogEntry): Int {
            val t = deburr(e.title)
            return if (t.startsWith(dq)) 0 else if (dq in t) 1 else 2
        }
        return catalog.filter { f ->
            val hay = deburr("${f.title} ${f.site} ${f.category}")
            val group = CAT_SYNONYMS[f.category].orEmpty()
            tokens.all { tok -> tok in hay || group.any { tok in it } }
        }.sortedBy(::rank)
    }
}

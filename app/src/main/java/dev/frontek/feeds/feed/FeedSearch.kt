package dev.frontek.feeds.feed

import dev.frontek.feeds.model.FeedResult
import dev.frontek.feeds.net.Http
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Dynamic feed search over the web via the Feedly Cloud search API
 * (no API key required). Given a free-text query it returns matching feeds
 * from across the web — not just a hand-curated list. The query leaves the
 * device: it is sent to Feedly.
 */
object FeedSearch {

    private const val ENDPOINT = "https://cloud.feedly.com/v3/search/feeds"

    /** Returns matching feeds, or null on network/service failure (caller falls back). */
    suspend fun search(query: String, count: Int = 20): List<FeedResult>? {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return try {
            val url = "$ENDPOINT?count=$count&query=" + URLEncoder.encode(q, "UTF-8")
            val json = Http.fetchText(url)
            val results = JSONObject(json).optJSONArray("results") ?: return emptyList()
            (0 until results.length()).mapNotNull { i ->
                val o = results.optJSONObject(i) ?: return@mapNotNull null
                // feedId looks like "feed/https://example.com/rss"
                val feed = o.optString("feedId").removePrefix("feed/").trim()
                if (feed.isBlank() || !feed.startsWith("http")) return@mapNotNull null
                val icon = o.optString("iconUrl").ifBlank { o.optString("visualUrl") }
                FeedResult(
                    title = o.optString("title").ifBlank { UrlUtils.host(feed) },
                    feed = feed,
                    site = o.optString("website").takeIf { it.isNotBlank() } ?: UrlUtils.origin(feed),
                    category = null,
                    description = o.optString("description").takeIf { it.isNotBlank() },
                    iconUrl = icon.takeIf { it.isNotBlank() },
                )
            }.distinctBy { it.feed }
        } catch (e: Exception) {
            null
        }
    }
}

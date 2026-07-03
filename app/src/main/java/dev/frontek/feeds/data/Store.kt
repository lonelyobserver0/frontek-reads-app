package dev.frontek.feeds.data

import android.content.Context
import dev.frontek.feeds.model.Article
import dev.frontek.feeds.model.CachedFeed
import dev.frontek.feeds.model.FeedItem
import dev.frontek.feeds.model.SavedArticle
import dev.frontek.feeds.model.Subscription
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * File-backed persistence for subscriptions and the article cache.
 * Replaces the web app's localStorage; JSON via org.json (no extra deps).
 */
class Store(context: Context) {

    private val subsFile = File(context.filesDir, "subs.json")
    private val cacheFile = File(context.filesDir, "cache.json")
    private val savedFile = File(context.filesDir, "saved.json")
    private val settingsFile = File(context.filesDir, "settings.json")

    // ---- subscriptions ----

    fun loadSubs(): MutableList<Subscription> {
        val text = readOrNull(subsFile) ?: return mutableListOf()
        return try {
            val arr = JSONArray(text)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val feed = o.optString("feed").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                Subscription(
                    title = o.optString("title").ifBlank { feed },
                    feed = feed,
                    site = o.optString("site").takeIf { it.isNotBlank() },
                )
            }.toMutableList()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun saveSubs(subs: List<Subscription>) {
        val arr = JSONArray()
        subs.forEach { s ->
            arr.put(
                JSONObject()
                    .put("title", s.title)
                    .put("feed", s.feed)
                    .put("site", s.site ?: ""),
            )
        }
        write(subsFile, arr.toString())
    }

    // ---- article cache ----

    fun loadCache(): MutableMap<String, CachedFeed> {
        val text = readOrNull(cacheFile) ?: return mutableMapOf()
        return try {
            val root = JSONObject(text)
            val map = mutableMapOf<String, CachedFeed>()
            for (key in root.keys()) {
                val o = root.optJSONObject(key) ?: continue
                val items = o.optJSONArray("items") ?: JSONArray()
                map[key] = CachedFeed(
                    t = o.optLong("t"),
                    items = (0 until items.length()).mapNotNull { i ->
                        val it = items.optJSONObject(i) ?: return@mapNotNull null
                        FeedItem(
                            title = it.optString("title"),
                            link = it.optString("link"),
                            date = it.optLong("date"),
                            summary = it.optString("summary"),
                            content = it.optString("content"),
                            id = it.optString("id"),
                            image = it.optString("image").takeIf { s -> s.isNotBlank() },
                        )
                    },
                )
            }
            map
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    fun saveCache(cache: Map<String, CachedFeed>) {
        val root = JSONObject()
        cache.forEach { (feed, cached) ->
            val items = JSONArray()
            cached.items.forEach { it ->
                items.put(
                    JSONObject()
                        .put("title", it.title)
                        .put("link", it.link)
                        .put("date", it.date)
                        .put("summary", it.summary)
                        .put("content", it.content)
                        .put("id", it.id)
                        .put("image", it.image ?: ""),
                )
            }
            root.put(feed, JSONObject().put("t", cached.t).put("items", items))
        }
        write(cacheFile, root.toString())
    }

    // ---- saved articles (favorites / read later) ----

    fun loadSaved(): MutableList<SavedArticle> {
        val text = readOrNull(savedFile) ?: return mutableListOf()
        return try {
            val arr = JSONArray(text)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                SavedArticle(
                    article = Article(
                        title = o.optString("title"),
                        link = o.optString("link"),
                        date = o.optLong("date"),
                        summary = o.optString("summary"),
                        content = o.optString("content"),
                        id = o.optString("id"),
                        source = o.optString("source"),
                        site = o.optString("site").takeIf { it.isNotBlank() },
                        image = o.optString("image").takeIf { it.isNotBlank() },
                    ),
                    favorite = o.optBoolean("favorite"),
                    readLater = o.optBoolean("readLater"),
                    savedAt = o.optLong("savedAt"),
                )
            }.toMutableList()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun saveSaved(saved: List<SavedArticle>) {
        val arr = JSONArray()
        saved.forEach { s ->
            val a = s.article
            arr.put(
                JSONObject()
                    .put("title", a.title)
                    .put("link", a.link)
                    .put("date", a.date)
                    .put("summary", a.summary)
                    .put("content", a.content)
                    .put("id", a.id)
                    .put("source", a.source)
                    .put("site", a.site ?: "")
                    .put("image", a.image ?: "")
                    .put("favorite", s.favorite)
                    .put("readLater", s.readLater)
                    .put("savedAt", s.savedAt),
            )
        }
        write(savedFile, arr.toString())
    }

    // ---- UI preferences ----

    fun loadFontScale(): Float = try {
        val text = readOrNull(settingsFile) ?: return 1.0f
        JSONObject(text).optDouble("fontScale", 1.0).toFloat()
    } catch (e: Exception) {
        1.0f
    }

    fun saveFontScale(scale: Float) {
        write(settingsFile, JSONObject().put("fontScale", scale.toDouble()).toString())
    }

    fun clearCache() {
        cacheFile.delete()
    }

    fun clearAll() {
        subsFile.delete()
        cacheFile.delete()
        savedFile.delete()
    }

    private fun readOrNull(file: File): String? =
        try {
            if (file.exists()) file.readText() else null
        } catch (e: Exception) {
            null
        }

    private fun write(file: File, text: String) {
        try {
            file.writeText(text)
        } catch (e: Exception) {
            // best effort; in-memory state still works this session
        }
    }
}

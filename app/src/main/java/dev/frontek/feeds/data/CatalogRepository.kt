package dev.frontek.feeds.data

import android.content.Context
import dev.frontek.feeds.model.CatalogEntry
import org.json.JSONObject

/** Loads the curated feed catalog bundled in assets/catalog.json. */
object CatalogRepository {

    fun load(context: Context): List<CatalogEntry> = try {
        val text = context.assets.open("catalog.json").bufferedReader().use { it.readText() }
        val feeds = JSONObject(text).optJSONArray("feeds") ?: return emptyList()
        (0 until feeds.length()).mapNotNull { i ->
            val o = feeds.optJSONObject(i) ?: return@mapNotNull null
            val feed = o.optString("feed").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            CatalogEntry(
                title = o.optString("title").ifBlank { feed },
                site = o.optString("site").takeIf { it.isNotBlank() },
                feed = feed,
                category = o.optString("category").takeIf { it.isNotBlank() },
            )
        }
    } catch (e: Exception) {
        emptyList()
    }
}

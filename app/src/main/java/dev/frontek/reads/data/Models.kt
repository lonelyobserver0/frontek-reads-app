package dev.frontek.reads.data

import kotlinx.serialization.Serializable

/** A subscribed feed. Same shape as the web app's `frss.subs` entries. */
@Serializable
data class Sub(
    val title: String,
    val feed: String,
    val site: String = "",
)

/** One feed item, optionally tagged with the subscription it came from. */
@Serializable
data class Article(
    val title: String = "",
    val link: String = "",
    val date: Long = 0,
    val summary: String = "",
    val content: String = "",
    val image: String = "",
    val id: String = "",
    val source: String = "",
    val site: String = "",
) {
    /** Identity used for favorites / read state: the guid, else the link. */
    val key: String get() = id.ifEmpty { link }
}

/** A favorite / read-later article, stored in full so it outlives the feed cache. */
@Serializable
data class SavedArticle(
    val article: Article,
    val favorite: Boolean = false,
    val readLater: Boolean = false,
    val savedAt: Long = 0,
)

@Serializable
data class CachedFeed(val t: Long, val items: List<Article>)

@Serializable
data class CatalogEntry(
    val title: String,
    val site: String = "",
    val feed: String,
    val category: String = "",
)

@Serializable
data class Catalog(val updated: String = "", val feeds: List<CatalogEntry> = emptyList())

/** A feed found by discovery (URL auto-discovery or Feedly search). */
data class FoundFeed(
    val title: String,
    val feed: String,
    val site: String,
    val description: String = "",
    val icon: String = "",
)

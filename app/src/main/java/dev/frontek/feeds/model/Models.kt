package dev.frontek.feeds.model

/** A curated feed from assets/catalog.json. */
data class CatalogEntry(
    val title: String,
    val site: String?,
    val feed: String,
    val category: String?,
)

/** A user subscription (persisted). */
data class Subscription(
    val title: String,
    val feed: String,
    val site: String?,
)

/** A parsed feed item, before it is attributed to a source. */
data class FeedItem(
    val title: String,
    val link: String,
    val date: Long,
    val summary: String,
    val content: String,
    val id: String,
    val image: String?,
)

/** Result of parsing a feed document. */
data class ParsedFeed(
    val title: String,
    val items: List<FeedItem>,
)

/** A feed item enriched with its source, ready to show on the aggregated home. */
data class Article(
    val title: String,
    val link: String,
    val date: Long,
    val summary: String,
    val content: String,
    val id: String,
    val source: String,
    val site: String?,
    val image: String?,
)

/** Cached feed content with a fetch timestamp. */
data class CachedFeed(
    val t: Long,
    val items: List<FeedItem>,
)

/**
 * An article the user saved to a collection. The full [article] is stored so it
 * stays readable even after the feed cache has evicted it.
 */
data class SavedArticle(
    val article: Article,
    val favorite: Boolean,
    val readLater: Boolean,
    val savedAt: Long,
)

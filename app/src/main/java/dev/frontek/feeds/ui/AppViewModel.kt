package dev.frontek.feeds.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.frontek.feeds.R
import dev.frontek.feeds.data.CatalogRepository
import dev.frontek.feeds.data.Opml
import dev.frontek.feeds.data.Store
import dev.frontek.feeds.feed.CatalogSearch
import dev.frontek.feeds.feed.Discovered
import dev.frontek.feeds.feed.FeedDiscovery
import dev.frontek.feeds.feed.FeedParser
import dev.frontek.feeds.feed.FeedSearch
import dev.frontek.feeds.feed.UrlUtils
import dev.frontek.feeds.model.Article
import dev.frontek.feeds.model.CachedFeed
import dev.frontek.feeds.model.CatalogEntry
import dev.frontek.feeds.model.FeedItem
import dev.frontek.feeds.model.FeedResult
import dev.frontek.feeds.model.SavedArticle
import dev.frontek.feeds.model.Subscription
import dev.frontek.feeds.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private companion object {
        const val CACHE_TTL = 15 * 60 * 1000L
        const val MAX_ITEMS_PER_FEED = 20
        const val FONT_MIN = 0.8f
        const val FONT_MAX = 1.8f
        const val FONT_STEP = 0.1f
    }

    private val store = Store(app)
    private val cache: MutableMap<String, CachedFeed> = store.loadCache()

    var subscriptions by mutableStateOf<List<Subscription>>(store.loadSubs())
        private set
    var catalog by mutableStateOf<List<CatalogEntry>>(emptyList())
        private set
    var homeItems by mutableStateOf<List<Article>>(emptyList())
        private set
    var activeSource by mutableStateOf<String?>(null)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var statusMessage by mutableStateOf<String?>(null)
        private set
    var toast by mutableStateOf<String?>(null)
        private set
    var saved by mutableStateOf<List<SavedArticle>>(store.loadSaved())
        private set
    var readKeys by mutableStateOf<Set<String>>(store.loadRead())
        private set
    var fontScale by mutableStateOf(store.loadFontScale())
        private set
    var searchResults by mutableStateOf<List<FeedResult>>(emptyList())
        private set
    var searching by mutableStateOf(false)
        private set
    var searchUsedFallback by mutableStateOf(false)
        private set
    private var searchJob: Job? = null

    init {
        catalog = CatalogRepository.load(app)
        refreshAll(force = false)
    }

    fun consumeToast() { toast = null }
    private fun notify(msg: String) { toast = msg }
    private fun str(@StringRes id: Int, vararg args: Any): String =
        getApplication<Application>().getString(id, *args)

    fun setSourceFilter(source: String?) { activeSource = source }

    // ---- font size ----

    val canDecreaseFont: Boolean get() = fontScale > FONT_MIN + 1e-3f
    val canIncreaseFont: Boolean get() = fontScale < FONT_MAX - 1e-3f
    val fontScalePercent: Int get() = Math.round(fontScale * 100)

    private fun applyFontScale(scale: Float) {
        val clamped = scale.coerceIn(FONT_MIN, FONT_MAX)
        fontScale = clamped
        store.saveFontScale(clamped)
    }

    fun increaseFont() = applyFontScale(fontScale + FONT_STEP)
    fun decreaseFont() = applyFontScale(fontScale - FONT_STEP)
    fun resetFont() = applyFontScale(1.0f)

    val filteredItems: List<Article>
        get() = activeSource?.let { src -> homeItems.filter { it.source == src } } ?: homeItems

    fun isSubscribed(feedUrl: String): Boolean = subscriptions.any { it.feed == feedUrl }

    // ---- discover: dynamic feed search ----

    private fun CatalogEntry.toResult(): FeedResult =
        FeedResult(title, feed, site, category, description = null, iconUrl = null)

    /** Local catalog as Discover results — shown as suggestions when the query is empty. */
    val suggestions: List<FeedResult> get() = catalog.map { it.toResult() }

    /** Search feeds across the web (Feedly); falls back to the local catalog on failure. */
    fun search(query: String) {
        searchJob?.cancel()
        val q = query.trim()
        if (q.isEmpty()) {
            searchResults = emptyList()
            searching = false
            searchUsedFallback = false
            return
        }
        searchJob = viewModelScope.launch {
            searching = true
            searchUsedFallback = false
            val web = FeedSearch.search(q)
            if (!web.isNullOrEmpty()) {
                searchResults = web
                searchUsedFallback = false
            } else {
                searchResults = CatalogSearch.filter(catalog, q).map { it.toResult() }
                searchUsedFallback = true
            }
            searching = false
        }
    }

    // ---- saved collections (favorites / read later) ----

    private fun keyOf(a: Article): String = a.id.ifBlank { a.link }

    // ---- read state ----

    fun isRead(a: Article): Boolean = keyOf(a) in readKeys

    /** Mark an article as read (when it is opened in the reader). */
    fun markRead(a: Article) {
        val key = keyOf(a)
        if (key.isBlank() || key in readKeys) return
        readKeys = readKeys + key
        store.saveRead(readKeys)
    }

    val favorites: List<Article>
        get() = saved.filter { it.favorite }.sortedByDescending { it.savedAt }.map { it.article }

    val readLaterItems: List<Article>
        get() = saved.filter { it.readLater }.sortedByDescending { it.savedAt }.map { it.article }

    fun isFavorite(a: Article): Boolean {
        val key = keyOf(a)
        return saved.any { keyOf(it.article) == key && it.favorite }
    }

    fun isReadLater(a: Article): Boolean {
        val key = keyOf(a)
        return saved.any { keyOf(it.article) == key && it.readLater }
    }

    fun toggleFavorite(a: Article) {
        updateSaved(a, toggleFavorite = true)
        notify(str(if (isFavorite(a)) R.string.toast_fav_added else R.string.toast_fav_removed))
    }

    fun toggleReadLater(a: Article) {
        updateSaved(a, toggleFavorite = false)
        notify(str(if (isReadLater(a)) R.string.toast_read_later_added else R.string.toast_read_later_removed))
    }

    private fun updateSaved(a: Article, toggleFavorite: Boolean) {
        val key = keyOf(a)
        val existing = saved.find { keyOf(it.article) == key }
        saved = if (existing == null) {
            saved + SavedArticle(
                article = a,
                favorite = toggleFavorite,
                readLater = !toggleFavorite,
                savedAt = System.currentTimeMillis(),
            )
        } else {
            val updated = if (toggleFavorite) {
                existing.copy(favorite = !existing.favorite)
            } else {
                existing.copy(readLater = !existing.readLater)
            }
            if (!updated.favorite && !updated.readLater) {
                saved - existing
            } else {
                saved.map { if (keyOf(it.article) == key) updated else it }
            }
        }
        store.saveSaved(saved)
    }

    // ---- subscriptions ----

    fun subscribe(title: String, feed: String, site: String?) {
        if (isSubscribed(feed)) {
            notify(str(R.string.toast_already_subscribed_named, title))
            return
        }
        val sub = Subscription(
            title = title.ifBlank { UrlUtils.host(feed) },
            feed = feed,
            site = site ?: UrlUtils.origin(feed),
        )
        subscriptions = subscriptions + sub
        store.saveSubs(subscriptions)
        notify(str(R.string.toast_subscribed_named, sub.title))
        refreshAll(force = false)
    }

    fun unsubscribe(feed: String) {
        subscriptions = subscriptions.filterNot { it.feed == feed }
        cache.remove(feed)
        store.saveSubs(subscriptions)
        store.saveCache(cache)
        if (activeSource != null && subscriptions.none { it.title == activeSource }) activeSource = null
        val liveTitles = subscriptions.map { it.title }.toSet()
        homeItems = homeItems.filter { it.source in liveTitles }
        notify(str(R.string.toast_unsubscribed))
    }

    fun addByUrl(input: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val found: Discovered = withContext(Dispatchers.IO) { FeedDiscovery.discover(input) }
                if (isSubscribed(found.feed)) {
                    notify(str(R.string.toast_already_subscribed))
                } else {
                    subscribe(found.title, found.feed, found.site)
                }
                onDone(true)
            } catch (e: Exception) {
                notify(str(R.string.toast_no_feed_found))
                onDone(false)
            }
        }
    }

    // ---- refresh / home ----

    private data class FetchResult(
        val articles: List<Article>,
        val failure: String?,
        val feed: String,
        val newTitle: String?,
    )

    fun refreshAll(force: Boolean) {
        val subs = subscriptions
        if (subs.isEmpty()) {
            homeItems = emptyList()
            return
        }
        viewModelScope.launch {
            refreshing = true
            statusMessage = null
            val results: List<FetchResult> = coroutineScope {
                subs.map { sub ->
                    async(Dispatchers.IO) { fetchOne(sub, force) }
                }.awaitAll()
            }

            // apply feed-reported title improvements
            val titleUpdates = results.mapNotNull { r -> r.newTitle?.let { r.feed to it } }.toMap()
            if (titleUpdates.isNotEmpty()) {
                subscriptions = subscriptions.map { s ->
                    titleUpdates[s.feed]?.let { s.copy(title = it) } ?: s
                }
            }

            val collected = results.flatMap { it.articles }.sortedByDescending { it.date }
            homeItems = collected
            store.saveCache(cache)
            store.saveSubs(subscriptions)

            val failures = results.mapNotNull { it.failure }
            statusMessage = if (failures.isNotEmpty()) {
                str(R.string.toast_load_failed, failures.joinToString(", "))
            } else {
                null
            }
            refreshing = false
        }
    }

    private suspend fun fetchOne(sub: Subscription, force: Boolean): FetchResult {
        val cached = cache[sub.feed]
        val fresh = cached != null && (System.currentTimeMillis() - cached.t) < CACHE_TTL
        if (fresh && !force) {
            return FetchResult(cached!!.items.map { withSource(it, sub) }, null, sub.feed, null)
        }
        return try {
            val text = Http.fetchText(sub.feed)
            val parsed = FeedParser.parse(text)
            val items = parsed.items.take(MAX_ITEMS_PER_FEED)
            cache[sub.feed] = CachedFeed(System.currentTimeMillis(), items)
            val newTitle = if (parsed.title.isNotEmpty() &&
                (sub.title == UrlUtils.host(sub.feed) || sub.title.isBlank())
            ) parsed.title else null
            FetchResult(items.map { withSource(it, sub) }, null, sub.feed, newTitle)
        } catch (e: Exception) {
            val fallback = cache[sub.feed]?.items?.map { withSource(it, sub) } ?: emptyList()
            FetchResult(fallback, sub.title, sub.feed, null)
        }
    }

    private fun withSource(item: FeedItem, sub: Subscription): Article = Article(
        title = item.title,
        link = item.link,
        date = item.date,
        summary = item.summary,
        content = item.content,
        id = item.id,
        source = sub.title,
        site = sub.site,
        image = item.image,
    )

    // ---- OPML / JSON ----

    fun exportOpmlTo(uri: Uri) {
        if (subscriptions.isEmpty()) {
            notify(str(R.string.toast_nothing_export))
            return
        }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    getApplication<Application>().contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(Opml.export(subscriptions).toByteArray())
                    }
                    true
                } catch (e: Exception) {
                    false
                }
            }
            notify(str(if (ok) R.string.toast_export_ok else R.string.toast_export_fail))
        }
    }

    fun importOpmlFrom(uri: Uri) {
        viewModelScope.launch {
            val parsed = withContext(Dispatchers.IO) {
                try {
                    val text = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?.bufferedReader()?.use { it.readText() } ?: return@withContext null
                    Opml.import(text)
                } catch (e: Exception) {
                    null
                }
            }
            if (parsed == null) {
                notify(str(R.string.toast_import_read_fail))
                return@launch
            }
            var added = 0
            var current = subscriptions
            parsed.forEach { s ->
                if (current.none { it.feed == s.feed }) {
                    current = current + s
                    added++
                }
            }
            subscriptions = current
            store.saveSubs(subscriptions)
            notify(
                if (added > 0) {
                    getApplication<Application>().resources
                        .getQuantityString(R.plurals.feeds_imported, added, added)
                } else {
                    str(R.string.toast_no_new_feeds)
                },
            )
            if (added > 0) refreshAll(force = false)
        }
    }

    // ---- settings ----

    fun clearCache() {
        cache.clear()
        store.clearCache()
        notify(str(R.string.toast_cache_cleared))
        refreshAll(force = true)
    }

    fun clearAll() {
        subscriptions = emptyList()
        cache.clear()
        homeItems = emptyList()
        activeSource = null
        saved = emptyList()
        readKeys = emptySet()
        store.clearAll()
        notify(str(R.string.toast_all_deleted))
    }
}

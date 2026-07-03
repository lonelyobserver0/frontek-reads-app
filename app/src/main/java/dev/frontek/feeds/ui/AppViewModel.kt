package dev.frontek.feeds.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.frontek.feeds.data.CatalogRepository
import dev.frontek.feeds.data.Opml
import dev.frontek.feeds.data.Store
import dev.frontek.feeds.feed.Discovered
import dev.frontek.feeds.feed.FeedDiscovery
import dev.frontek.feeds.feed.FeedParser
import dev.frontek.feeds.feed.UrlUtils
import dev.frontek.feeds.model.Article
import dev.frontek.feeds.model.CachedFeed
import dev.frontek.feeds.model.CatalogEntry
import dev.frontek.feeds.model.FeedItem
import dev.frontek.feeds.model.SavedArticle
import dev.frontek.feeds.model.Subscription
import dev.frontek.feeds.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private companion object {
        const val CACHE_TTL = 15 * 60 * 1000L
        const val MAX_ITEMS_PER_FEED = 20
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

    init {
        catalog = CatalogRepository.load(app)
        refreshAll(force = false)
    }

    fun consumeToast() { toast = null }
    private fun notify(msg: String) { toast = msg }

    fun setSourceFilter(source: String?) { activeSource = source }

    val filteredItems: List<Article>
        get() = activeSource?.let { src -> homeItems.filter { it.source == src } } ?: homeItems

    fun isSubscribed(feedUrl: String): Boolean = subscriptions.any { it.feed == feedUrl }

    // ---- saved collections (favorites / read later) ----

    private fun keyOf(a: Article): String = a.id.ifBlank { a.link }

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
        notify(if (isFavorite(a)) "Aggiunto ai preferiti." else "Rimosso dai preferiti.")
    }

    fun toggleReadLater(a: Article) {
        updateSaved(a, toggleFavorite = false)
        notify(if (isReadLater(a)) "Salvato in “Leggi più tardi”." else "Rimosso da “Leggi più tardi”.")
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
            notify("Già iscritto a “$title”.")
            return
        }
        val sub = Subscription(
            title = title.ifBlank { UrlUtils.host(feed) },
            feed = feed,
            site = site ?: UrlUtils.origin(feed),
        )
        subscriptions = subscriptions + sub
        store.saveSubs(subscriptions)
        notify("Iscritto a “${sub.title}”.")
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
        notify("Iscrizione rimossa.")
    }

    fun addByUrl(input: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val found: Discovered = withContext(Dispatchers.IO) { FeedDiscovery.discover(input) }
                if (isSubscribed(found.feed)) {
                    notify("Già iscritto.")
                } else {
                    subscribe(found.title, found.feed, found.site)
                }
                onDone(true)
            } catch (e: Exception) {
                notify("Nessun feed RSS/Atom trovato lì.")
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
                "Impossibile caricare: ${failures.joinToString(", ")}."
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
    )

    // ---- OPML / JSON ----

    fun exportOpmlTo(uri: Uri) {
        if (subscriptions.isEmpty()) {
            notify("Niente da esportare.")
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
            notify(if (ok) "Iscrizioni esportate." else "Esportazione fallita.")
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
                notify("Impossibile leggere quel file.")
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
            notify(if (added > 0) "Importati $added feed." else "Nessun feed nuovo nel file.")
            if (added > 0) refreshAll(force = false)
        }
    }

    // ---- settings ----

    fun clearCache() {
        cache.clear()
        store.clearCache()
        notify("Cache svuotata.")
        refreshAll(force = true)
    }

    fun clearAll() {
        subscriptions = emptyList()
        cache.clear()
        homeItems = emptyList()
        activeSource = null
        saved = emptyList()
        store.clearAll()
        notify("Tutti i dati eliminati.")
    }
}

package dev.frontek.reads.ui

import android.app.Application
import android.net.Uri
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.frontek.reads.R
import dev.frontek.reads.data.Article
import dev.frontek.reads.data.CachedFeed
import dev.frontek.reads.data.Catalog
import dev.frontek.reads.data.CatalogEntry
import dev.frontek.reads.data.FoundFeed
import dev.frontek.reads.data.SavedArticle
import dev.frontek.reads.data.Store
import dev.frontek.reads.data.Sub
import dev.frontek.reads.feed.Discovery
import dev.frontek.reads.feed.FeedParser
import dev.frontek.reads.feed.Html
import dev.frontek.reads.feed.Http
import dev.frontek.reads.feed.Opml
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

enum class Tab { Home, Favorites, ReadLater, Discover }
enum class Overlay { Subs, Settings }

/** A snackbar message, resolved against the (localized) context when shown. */
data class UiMessage(
    @StringRes val res: Int = 0,
    val args: List<Any> = emptyList(),
    @PluralsRes val plural: Int = 0,
    val count: Int = 0,
    val error: Boolean = false,
)

data class DiscoverState(
    val urlCard: String? = null,      // the query looks like a URL: offer "Find & subscribe"
    val findingUrl: Boolean = false,
    val searching: Boolean = false,
    val feedly: List<FoundFeed> = emptyList(),
    val catalog: List<CatalogEntry> = emptyList(),
    val fallbackHint: Boolean = false,
    val noMatch: Boolean = false,
)

enum class FullState { Hidden, Ready, Loading, Retry }

data class ReaderState(
    val article: Article,
    val bodyHtml: String,
    val full: FullState,
    val warn: Boolean = false,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)

    // ---------- state ----------
    var tab by mutableStateOf(Tab.Home)
    var overlay by mutableStateOf<Overlay?>(null)

    var subs by mutableStateOf(emptyList<Sub>()); private set
    var saved by mutableStateOf(emptyList<SavedArticle>()); private set
    var readKeys by mutableStateOf(emptySet<String>()); private set
    private val readList = ArrayList<String>()
    private var cache = HashMap<String, CachedFeed>()

    var homeItems by mutableStateOf(emptyList<Article>()); private set
    var activeSource by mutableStateOf<String?>(null)
    var refreshing by mutableStateOf(false); private set
    var failures by mutableStateOf(emptyList<String>()); private set
    var fontScale by mutableIntStateOf(100); private set

    var catalog by mutableStateOf(emptyList<CatalogEntry>()); private set
    var query by mutableStateOf(""); private set
    var discover by mutableStateOf(DiscoverState()); private set

    var reader by mutableStateOf<ReaderState?>(null); private set

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiMessage> = _messages

    private var refreshJob: Job? = null
    private var searchJob: Job? = null
    private var fullJob: Job? = null

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                subs = store.loadSubs()
                saved = store.loadSaved()
                readList.addAll(store.loadRead())
                cache = HashMap(store.loadCache())
                fontScale = store.fontScale
                catalog = runCatching {
                    getApplication<Application>().assets.open("catalog.json").bufferedReader().use {
                        json.decodeFromString(Catalog.serializer(), it.readText()).feeds
                    }
                }.getOrDefault(emptyList())
            }
            readKeys = readList.toHashSet()
            discover = DiscoverState(catalog = catalog)
            refreshAll(false)
        }
    }

    private fun toast(@StringRes res: Int, vararg args: Any, error: Boolean = false) {
        _messages.tryEmit(UiMessage(res = res, args = args.toList(), error = error))
    }

    private fun io(block: suspend () -> Unit) = viewModelScope.launch(Dispatchers.IO) { block() }

    // ---------- refresh / home ----------

    fun refreshAll(force: Boolean) {
        refreshJob?.cancel()
        val current = subs
        if (current.isEmpty()) { homeItems = emptyList(); failures = emptyList(); refreshing = false; return }
        refreshJob = viewModelScope.launch {
            refreshing = true
            val sem = Semaphore(6)
            val now = System.currentTimeMillis()
            val results = current.map { sub ->
                async(Dispatchers.IO) {
                    val cached = cache[sub.feed]
                    if (!force && cached != null && now - cached.t < CACHE_TTL) return@async FeedResult(sub, cached.items)
                    try {
                        sem.withPermit {
                            val parsed = FeedParser.parse(Http.fetchText(sub.feed))
                            FeedResult(sub, parsed.items.take(MAX_ITEMS_PER_FEED), fetched = true, feedTitle = parsed.title)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        FeedResult(sub, cached?.items.orEmpty(), failed = true)
                    }
                }
            }.awaitAll()

            val fetchedAt = System.currentTimeMillis()
            results.filter { it.fetched }.forEach { cache[it.sub.feed] = CachedFeed(fetchedAt, it.items) }
            // Adopt the feed's own title when the sub was only named after its host.
            val renamed = results.filter { it.feedTitle.isNotEmpty() && (it.sub.title.isEmpty() || it.sub.title == Discovery.hostOf(it.sub.feed)) }
                .associate { it.sub.feed to it.feedTitle }
            if (renamed.isNotEmpty()) subs = subs.map { s -> renamed[s.feed]?.let { s.copy(title = it) } ?: s }
            val titles = subs.associate { it.feed to it.title }

            homeItems = results.flatMap { r ->
                val src = titles[r.sub.feed] ?: r.sub.title
                r.items.map { it.copy(source = src, site = r.sub.site) }
            }.sortedByDescending { it.date }
            failures = results.filter { it.failed }.map { titles[it.sub.feed] ?: it.sub.title }
            refreshing = false
            val snapSubs = subs
            val snapCache = HashMap(cache)
            withContext(Dispatchers.IO) { store.saveCache(snapCache); store.saveSubs(snapSubs) }
        }
    }

    private class FeedResult(
        val sub: Sub,
        val items: List<Article>,
        val fetched: Boolean = false,
        val failed: Boolean = false,
        val feedTitle: String = "",
    )

    fun selectSource(title: String?) { activeSource = title }

    // ---------- subscriptions ----------

    fun isSubscribed(feed: String) = subs.any { it.feed == feed }

    fun subscribe(f: FoundFeed) {
        val title = f.title.ifEmpty { Discovery.hostOf(f.feed) }
        if (isSubscribed(f.feed)) { toast(R.string.toast_already_subscribed_named, title); return }
        subs = subs + Sub(title, f.feed, f.site.ifEmpty { Discovery.originOf(f.feed) })
        persistSubs()
        toast(R.string.toast_subscribed_named, title)
        refreshAll(false)
    }

    fun subscribe(e: CatalogEntry) = subscribe(FoundFeed(e.title, e.feed, e.site))

    fun unsubscribe(feed: String) {
        val removed = subs.firstOrNull { it.feed == feed } ?: return
        subs = subs.filter { it.feed != feed }
        cache.remove(feed)
        if (activeSource == removed.title) activeSource = null
        persistSubs()
        toast(R.string.toast_unsubscribed)
        refreshAll(false)
    }

    private fun persistSubs() { val s = subs; io { store.saveSubs(s) } }

    // ---------- favorites / read later ----------

    private fun savedOf(a: Article) = a.key.takeIf { it.isNotEmpty() }?.let { k -> saved.firstOrNull { it.article.key == k } }
    fun isFav(a: Article) = savedOf(a)?.favorite == true
    fun isReadLater(a: Article) = savedOf(a)?.readLater == true

    fun toggleFavorite(a: Article) = toggle(a, favorite = true)
    fun toggleReadLater(a: Article) = toggle(a, favorite = false)

    private fun toggle(a: Article, favorite: Boolean) {
        if (a.key.isEmpty()) return
        val list = saved.toMutableList()
        val i = list.indexOfFirst { it.article.key == a.key }
        val rec = if (i >= 0) list[i] else SavedArticle(a, savedAt = System.currentTimeMillis())
        val next = if (favorite) rec.copy(favorite = !rec.favorite) else rec.copy(readLater = !rec.readLater)
        val added = if (favorite) next.favorite else next.readLater
        when {
            !next.favorite && !next.readLater -> if (i >= 0) list.removeAt(i)
            i >= 0 -> list[i] = next
            else -> list.add(next)
        }
        saved = list
        val snap = list
        io { store.saveSaved(snap) }
        toast(
            if (favorite) (if (added) R.string.toast_fav_added else R.string.toast_fav_removed)
            else (if (added) R.string.toast_read_later_added else R.string.toast_read_later_removed),
        )
    }

    val favorites get() = saved.filter { it.favorite }.sortedByDescending { it.savedAt }.map { it.article }
    val readLater get() = saved.filter { it.readLater }.sortedByDescending { it.savedAt }.map { it.article }

    // ---------- read / unread ----------

    fun isRead(a: Article) = a.key.isNotEmpty() && a.key in readKeys

    fun markRead(a: Article) {
        val k = a.key
        if (k.isEmpty() || k in readKeys) return
        readList.add(k)
        while (readList.size > READ_CAP) readList.removeAt(0)
        readKeys = readList.toHashSet()
        val snap = ArrayList(readList)
        io { store.saveRead(snap) }
    }

    // ---------- reader ----------

    fun openReader(a: Article) {
        markRead(a)
        fullJob?.cancel()
        reader = ReaderState(a, "", if (a.link.isNotEmpty()) FullState.Ready else FullState.Hidden)
        viewModelScope.launch {
            val (body, auto) = withContext(Dispatchers.Default) {
                val feedHtml = if (a.content.isNotBlank()) Html.cleanFeedHtml(a.content) else ""
                val body = if (feedHtml.isNotBlank()) Html.sanitize(feedHtml, a.link) else ""
                body to (a.link.isNotEmpty() && (feedHtml.isBlank() || Html.isTruncated(a.content)))
            }
            if (reader?.article !== a) return@launch
            reader = reader?.copy(bodyHtml = body)
            if (auto) loadFullArticle()
        }
    }

    fun closeReader() { fullJob?.cancel(); reader = null }

    fun loadFullArticle() {
        val r = reader ?: return
        val a = r.article
        if (a.link.isEmpty()) return
        fullJob?.cancel()
        reader = r.copy(full = FullState.Loading, warn = false)
        fullJob = viewModelScope.launch {
            val html = try {
                val page = Http.fetchText(a.link)
                withContext(Dispatchers.Default) {
                    val res = Html.extractArticle(page, a.link)
                    if (res.chars < 400) null else Html.sanitize(res.html, a.link)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            val cur = reader ?: return@launch
            if (cur.article !== a) return@launch
            reader = if (html != null) cur.copy(bodyHtml = html, full = FullState.Hidden, warn = false)
            else cur.copy(full = FullState.Retry, warn = true)
        }
    }

    /** Deep link ?read=<url>&t=<title>&s=<source> from the frontek sites. */
    fun openFromLink(link: String, title: String?, source: String?) {
        openReader(Article(title = title?.ifEmpty { null } ?: link, link = link, id = link, source = source.orEmpty()))
    }

    // ---------- discover ----------

    fun onQueryChange(q: String) {
        query = q
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(350)
            runDiscover(q)
        }
    }

    fun submitQuery() {
        searchJob?.cancel()
        searchJob = viewModelScope.launch { runDiscover(query) }
    }

    /**
     * Category chips filter the built-in catalog only, by exact category: fuzzy
     * matching would let "News IT" also hit "News" (the token "it" is in "attualita").
     */
    fun filterCategory(cat: String) {
        searchJob?.cancel()
        query = cat
        discover = DiscoverState(catalog = catalog.filter { it.category == cat })
    }

    /** "Share" a URL to the app: open Discover with it ready to subscribe. */
    fun handleSharedText(text: String) {
        val url = Regex("https?://\\S+").find(text)?.value ?: text.trim()
        if (url.isEmpty()) return
        overlay = null
        reader = null
        tab = Tab.Discover
        query = url
        submitQuery()
    }

    private suspend fun runDiscover(raw: String) {
        val q = raw.trim()
        if (q.isEmpty()) { discover = DiscoverState(catalog = catalog); return }
        val urlCard = if (Discovery.looksLikeUrl(q)) q else null
        discover = DiscoverState(urlCard = urlCard, searching = true)
        val results = try {
            Discovery.searchFeedly(q)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
        discover = if (results.isNotEmpty()) {
            DiscoverState(urlCard = urlCard, feedly = results)
        } else {
            val matches = Discovery.matchCatalog(catalog, q)
            DiscoverState(
                urlCard = urlCard,
                catalog = matches,
                fallbackHint = matches.isNotEmpty(),
                noMatch = matches.isEmpty() && urlCard == null,
            )
        }
    }

    fun findAndSubscribe(url: String) {
        discover = discover.copy(findingUrl = true)
        viewModelScope.launch {
            try {
                val found = Discovery.discover(url)
                if (isSubscribed(found.feed)) toast(R.string.toast_already_subscribed) else subscribe(found)
                query = ""
                discover = DiscoverState(catalog = catalog)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                discover = discover.copy(findingUrl = false)
                toast(R.string.toast_no_feed_found, error = true)
            }
        }
    }

    // ---------- import / export ----------

    fun exportTo(uri: Uri) {
        val snap = subs
        io {
            try {
                getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.use {
                    it.write(Opml.export(snap).toByteArray())
                }
                toast(R.string.toast_export_ok)
            } catch (e: Exception) {
                toast(R.string.toast_export_fail, error = true)
            }
        }
    }

    fun canExport(): Boolean {
        if (subs.isEmpty()) toast(R.string.toast_nothing_export)
        return subs.isNotEmpty()
    }

    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            val found = withContext(Dispatchers.IO) {
                runCatching {
                    val text = getApplication<Application>().contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
                    Opml.import(text)
                }.getOrNull()
            }
            if (found == null) { toast(R.string.toast_import_read_fail, error = true); return@launch }
            val known = subs.map { it.feed }.toHashSet()
            val fresh = found.filter { known.add(it.feed) }
            if (fresh.isEmpty()) { toast(R.string.toast_no_new_feeds); return@launch }
            subs = subs + fresh
            persistSubs()
            _messages.tryEmit(UiMessage(plural = R.plurals.feeds_imported, count = fresh.size))
            refreshAll(false)
        }
    }

    // ---------- settings ----------

    fun changeFontScale(v: Int) {
        fontScale = v.coerceIn(80, 180)
        store.fontScale = fontScale
    }

    fun clearCache() {
        cache.clear()
        io { store.clearCache() }
        toast(R.string.toast_cache_cleared)
        refreshAll(true)
    }

    fun deleteAll() {
        refreshJob?.cancel()
        subs = emptyList(); saved = emptyList(); readList.clear(); readKeys = emptySet()
        cache.clear(); homeItems = emptyList(); failures = emptyList(); activeSource = null
        refreshing = false
        fontScale = 100
        io { store.clearAll() }
        overlay = null
        toast(R.string.toast_all_deleted)
    }

    companion object {
        private const val CACHE_TTL = 15 * 60 * 1000L
        private const val MAX_ITEMS_PER_FEED = 20
        private const val READ_CAP = 3000
        private val json = Json { ignoreUnknownKeys = true }
    }
}

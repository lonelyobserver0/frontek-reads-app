package dev.frontek.feeds.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.frontek.feeds.data.Store
import dev.frontek.feeds.feed.FeedParser
import dev.frontek.feeds.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Periodically fetches subscribed feeds while the app is in the background and
 * posts a notification when genuinely new articles appear. It compares the
 * current article keys against a persisted baseline ([Store.loadSeen]); the
 * foreground app keeps that baseline fresh, so only articles the user has not
 * seen yet trigger a notification.
 */
class FeedRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val store = Store(applicationContext)
        val subs = store.loadSubs()
        if (subs.isEmpty()) return@withContext Result.success()

        val currentKeys = coroutineScope {
            subs.map { sub ->
                async {
                    try {
                        val text = Http.fetchText(sub.feed)
                        FeedParser.parse(text).items
                            .take(MAX_ITEMS_PER_FEED)
                            .map { it.id.ifBlank { it.link } }
                            .filter { it.isNotBlank() }
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
            }.awaitAll()
        }.flatten().toSet()

        if (currentKeys.isEmpty()) return@withContext Result.success()

        val firstRun = !store.seenFileExists()
        val seen = store.loadSeen()
        val newKeys = currentKeys - seen
        // Keep the baseline bounded to what is currently live plus what was seen.
        store.saveSeen(seen + currentKeys)

        if (!firstRun) {
            Notifications.notifyNewArticles(applicationContext, newKeys.size)
        }
        Result.success()
    }

    private companion object {
        const val MAX_ITEMS_PER_FEED = 20
    }
}

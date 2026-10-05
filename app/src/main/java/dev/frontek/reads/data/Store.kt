package dev.frontek.reads.data

import android.content.Context
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * On-device persistence: everything lives in the app's private storage, nothing
 * is synced anywhere. Mirrors the web app's localStorage keys (frss.*), one JSON
 * file each; small settings go to SharedPreferences.
 */
class Store(context: Context) {
    private val dir = context.filesDir
    private val prefs = context.getSharedPreferences("frss.settings", Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private val subsSer = ListSerializer(Sub.serializer())
    private val savedSer = ListSerializer(SavedArticle.serializer())
    private val readSer = ListSerializer(String.serializer())
    private val cacheSer = MapSerializer(String.serializer(), CachedFeed.serializer())

    fun loadSubs(): List<Sub> = read(SUBS, subsSer) ?: emptyList()
    fun saveSubs(v: List<Sub>) = write(SUBS, subsSer, v)

    fun loadSaved(): List<SavedArticle> = read(SAVED, savedSer) ?: emptyList()
    fun saveSaved(v: List<SavedArticle>) = write(SAVED, savedSer, v)

    /** Opened-article keys, oldest first. */
    fun loadRead(): List<String> = read(READ, readSer) ?: emptyList()
    fun saveRead(v: List<String>) = write(READ, readSer, v)

    fun loadCache(): Map<String, CachedFeed> = read(CACHE, cacheSer) ?: emptyMap()
    fun saveCache(v: Map<String, CachedFeed>) = write(CACHE, cacheSer, v)

    var fontScale: Int
        get() = prefs.getInt("fontScale", 100)
        set(v) = prefs.edit().putInt("fontScale", v).apply()

    fun clearCache() { File(dir, CACHE).delete() }

    fun clearAll() {
        listOf(SUBS, SAVED, READ, CACHE).forEach { File(dir, it).delete() }
        prefs.edit().clear().apply()
    }

    private fun <T> read(name: String, ser: KSerializer<T>): T? = try {
        val f = File(dir, name)
        if (f.exists()) json.decodeFromString(ser, f.readText()) else null
    } catch (e: Exception) {
        null
    }

    /** Write to a temp file and rename, so a crash mid-write never corrupts data. */
    private fun <T> write(name: String, ser: KSerializer<T>, value: T) {
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(json.encodeToString(ser, value))
        tmp.renameTo(File(dir, name))
    }

    private companion object {
        const val SUBS = "subs.json"
        const val SAVED = "saved.json"
        const val READ = "read.json"
        const val CACHE = "cache.json"
    }
}

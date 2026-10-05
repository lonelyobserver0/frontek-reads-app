package dev.frontek.reads.feed

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit

/**
 * Fetches feeds and pages. A native app has no CORS limits, so requests go
 * straight to the site; only if that fails (403 from anti-bot/TLS fingerprinting,
 * network hiccup) does it retry through the frontek.dev proxy, which fetches with
 * curl. The proxy only ever sees the URL being requested.
 */
object Http {
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36"
    private const val FALLBACK_PROXY = "https://feeds.frontek.dev/proxy?url="
    private const val MAX_BYTES = 5L * 1024 * 1024

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            // Many sites 403 a "bot" UA but serve browsers; applies to images too (Coil).
            val r = chain.request()
            chain.proceed(if (r.header("User-Agent") == null) r.newBuilder().header("User-Agent", USER_AGENT).build() else r)
        }
        .build()

    suspend fun fetchText(url: String): String = try {
        get(url)
    } catch (e: Exception) {
        get(FALLBACK_PROXY + URLEncoder.encode(url, "UTF-8"))
    }

    private suspend fun get(url: String): String {
        val req = Request.Builder()
            .url(url)
            .header("Accept", "text/html,application/xhtml+xml,application/rss+xml,application/atom+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "it-IT,it;q=0.9,en;q=0.8")
            .build()
        return withContext(Dispatchers.IO) {
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) throw IOException("HTTP ${res.code}")
                val body = res.body
                val src = body.source()
                src.request(MAX_BYTES)
                val bytes = src.buffer.readByteArray(minOf(src.buffer.size, MAX_BYTES))
                val text = decode(bytes, body.contentType()?.charset())
                if (text.length < 20) throw IOException("Empty response")
                text
            }
        }
    }

    /**
     * Header charset first, then the XML declaration / HTML meta charset, then UTF-8.
     * Many Italian feeds are ISO-8859-1 and only say so inside the document.
     */
    private fun decode(bytes: ByteArray, headerCharset: Charset?): String {
        if (headerCharset != null) return String(bytes, headerCharset)
        val head = String(bytes, 0, minOf(bytes.size, 1024), Charsets.ISO_8859_1)
        val m = Regex("""encoding\s*=\s*["']([\w.:-]+)["']""").find(head)
            ?: Regex("""<meta[^>]+charset\s*=\s*["']?([\w.:-]+)""", RegexOption.IGNORE_CASE).find(head)
        val cs = m?.groupValues?.get(1)?.let { runCatching { Charset.forName(it) }.getOrNull() }
        return String(bytes, cs ?: Charsets.UTF_8).removePrefix("﻿")
    }
}

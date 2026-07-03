package dev.frontek.feeds.feed

import java.net.URI

/** URL helpers mirroring the web app's normalize / host / origin logic. */
object UrlUtils {

    fun normalize(input: String): String {
        val s = input.trim()
        if (s.isEmpty()) return ""
        return if (Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(s)) s else "https://$s"
    }

    fun looksLikeUrl(s: String): Boolean {
        val t = s.trim()
        return Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(t) ||
            Regex("\\.[a-z]{2,}(/|$)", RegexOption.IGNORE_CASE).containsMatchIn(t)
    }

    fun host(u: String): String = try {
        URI(u).host?.removePrefix("www.") ?: u
    } catch (e: Exception) {
        u
    }

    fun origin(u: String): String = try {
        val uri = URI(u)
        val scheme = uri.scheme ?: return u
        val host = uri.host ?: return u
        val port = if (uri.port > 0) ":${uri.port}" else ""
        "$scheme://$host$port"
    } catch (e: Exception) {
        u
    }

    fun resolve(href: String, base: String): String = try {
        URI(base).resolve(href).toString()
    } catch (e: Exception) {
        href
    }
}

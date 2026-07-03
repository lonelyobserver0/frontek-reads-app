package dev.frontek.feeds.feed

import java.text.SimpleDateFormat
import java.util.Locale

/** Parses RSS (RFC-822) and Atom (ISO-8601) dates to epoch millis, or 0. */
object DateParser {

    private val PATTERNS = listOf(
        "EEE, dd MMM yyyy HH:mm:ss Z",
        "EEE, dd MMM yyyy HH:mm:ss zzz",
        "EEE, dd MMM yyyy HH:mm Z",
        "EEE, dd MMM yyyy HH:mm zzz",
        "dd MMM yyyy HH:mm:ss Z",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd",
    )

    fun parse(raw: String?): Long {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return 0L
        for (pattern in PATTERNS) {
            try {
                val fmt = SimpleDateFormat(pattern, Locale.US)
                fmt.isLenient = true
                return fmt.parse(s)?.time ?: continue
            } catch (e: Exception) {
                // try next pattern
            }
        }
        return 0L
    }
}

package dev.frontek.feeds.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Relative date formatting, mirroring the web app's fmtDate(). */
fun fmtDate(ms: Long): String {
    if (ms <= 0L) return ""
    val diff = System.currentTimeMillis() - ms
    val day = 24 * 3600 * 1000L
    return when {
        diff < 3600 * 1000L -> "${maxOf(1, Math.round(diff / 60000.0).toInt())}m fa"
        diff < day -> "${Math.round(diff / 3600000.0).toInt()}h fa"
        diff < 7 * day -> "${Math.round(diff / day.toDouble()).toInt()}g fa"
        else -> SimpleDateFormat("d MMM yyyy", Locale.ITALIAN).format(Date(ms))
    }
}

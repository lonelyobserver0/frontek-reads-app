package dev.frontek.feeds.ui

import android.content.Context
import androidx.core.os.ConfigurationCompat
import dev.frontek.feeds.R
import java.text.SimpleDateFormat
import java.util.Date

/** Relative date formatting, localized via string resources. */
fun fmtDate(ms: Long, context: Context): String {
    if (ms <= 0L) return ""
    val diff = System.currentTimeMillis() - ms
    val day = 24 * 3600 * 1000L
    return when {
        diff < 3600 * 1000L ->
            context.getString(R.string.time_minutes_ago, maxOf(1, Math.round(diff / 60000.0).toInt()))
        diff < day ->
            context.getString(R.string.time_hours_ago, Math.round(diff / 3600000.0).toInt())
        diff < 7 * day ->
            context.getString(R.string.time_days_ago, Math.round(diff / day.toDouble()).toInt())
        else -> {
            val locale = ConfigurationCompat.getLocales(context.resources.configuration)[0]
            SimpleDateFormat("d MMM yyyy", locale).format(Date(ms))
        }
    }
}

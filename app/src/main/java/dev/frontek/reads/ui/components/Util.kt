package dev.frontek.reads.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri
import dev.frontek.reads.R
import java.text.DateFormat
import java.util.Date

/** Relative time like the web app: 5m / 3h / 2d ago, then a short date. */
fun fmtDate(ctx: Context, ms: Long): String {
    if (ms <= 0) return ""
    val diff = System.currentTimeMillis() - ms
    val day = 24 * 3600 * 1000L
    return when {
        diff < 3600_000L -> ctx.getString(R.string.time_minutes_ago, maxOf(1, Math.round(diff / 60000.0).toInt()))
        diff < day -> ctx.getString(R.string.time_hours_ago, Math.round(diff / 3600000.0).toInt())
        diff < 7 * day -> ctx.getString(R.string.time_days_ago, Math.round(diff.toDouble() / day).toInt())
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM, ctx.resources.configuration.locales[0]).format(Date(ms))
    }
}

/** Opens a page in a Custom Tab (falls back to any browser). */
fun openUrl(ctx: Context, url: String) {
    if (url.isBlank()) return
    val uri = url.toUri()
    try {
        CustomTabsIntent.Builder()
            .setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder().setToolbarColor(0xFF264653.toInt()).build())
            .setShowTitle(true)
            .build()
            .launchUrl(ctx, uri)
    } catch (e: ActivityNotFoundException) {
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    }
}

fun shareArticle(ctx: Context, title: String, link: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, if (title.isNotBlank()) "$title\n$link" else link)
    }
    ctx.startActivity(Intent.createChooser(send, null))
}

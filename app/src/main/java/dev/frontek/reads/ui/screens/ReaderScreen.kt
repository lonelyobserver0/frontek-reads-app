package dev.frontek.reads.ui.screens

import android.annotation.SuppressLint
import android.graphics.Color as AColor
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import dev.frontek.reads.R
import dev.frontek.reads.ui.AppViewModel
import dev.frontek.reads.ui.FullState
import dev.frontek.reads.ui.ReaderState
import dev.frontek.reads.ui.components.FavToggle
import dev.frontek.reads.ui.components.ReadLaterToggle
import dev.frontek.reads.ui.components.fmtDate
import dev.frontek.reads.ui.components.openUrl
import dev.frontek.reads.ui.components.shareArticle
import dev.frontek.reads.ui.theme.LocalFrontekColors
import org.jsoup.nodes.Entities

private const val FULL_ACTION = "frontek-reads://full"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(vm: AppViewModel, state: ReaderState) {
    val ctx = LocalContext.current
    val c = LocalFrontekColors.current
    val a = state.article
    val bg = MaterialTheme.colorScheme.background.toArgb()

    val html = readerHtml(
        state = state,
        title = a.title.ifEmpty { stringResource(R.string.untitled) },
        date = fmtDate(ctx, a.date),
        openOriginal = stringResource(R.string.reader_open_original),
        fullLabel = stringResource(
            when (state.full) {
                FullState.Loading -> R.string.reader_loading_full
                FullState.Retry -> R.string.reader_retry_full
                else -> R.string.reader_read_full
            },
        ),
        note = stringResource(R.string.reader_feed_preview_note),
        fallback = a.summary.ifEmpty { stringResource(R.string.no_preview) },
        dark = c.dark,
    )
    val onLink by rememberUpdatedState { url: String ->
        if (url == FULL_ACTION) vm.loadFullArticle() else openUrl(ctx, url)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(a.source, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = vm::closeReader) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_close)) }
                },
                actions = {
                    FavToggle(vm.isFav(a)) { vm.toggleFavorite(a) }
                    ReadLaterToggle(vm.isReadLater(a)) { vm.toggleReadLater(a) }
                    if (a.link.isNotEmpty()) {
                        IconButton(onClick = { shareArticle(ctx, a.title, a.link) }) { Icon(Icons.Filled.Share, stringResource(R.string.action_share)) }
                        IconButton(onClick = { openUrl(ctx, a.link) }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, stringResource(R.string.reader_open_original)) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    @SuppressLint("SetJavaScriptEnabled")
                    WebView(context).apply {
                        settings.javaScriptEnabled = false
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.loadsImagesAutomatically = true
                        setBackgroundColor(bg)
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                onLink(request.url.toString())
                                return true
                            }
                        }
                    }
                },
                update = { wv ->
                    wv.settings.textZoom = vm.fontScale
                    wv.setBackgroundColor(bg)
                    if (wv.tag != html) {
                        wv.tag = html
                        wv.loadDataWithBaseURL(a.link.ifEmpty { null }, html, "text/html", "utf-8", null)
                    }
                },
                onRelease = { it.destroy() },
            )
            if (state.full == FullState.Loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }
}

private fun esc(s: String) = Entities.escape(s)

private fun readerHtml(
    state: ReaderState,
    title: String,
    date: String,
    openOriginal: String,
    fullLabel: String,
    note: String,
    fallback: String,
    dark: Boolean,
): String {
    val a = state.article
    val link = esc(a.link)
    // Same palette as the site's .reader / .prose rules, with a dark variant.
    val v = if (dark) mapOf(
        "bg" to "#121A1E", "ink" to "#DCE5E8", "title" to "#EAF2F4", "grey" to "#9FB0B7", "teal" to "#45B9AA",
        "line" to "#2A373E", "cream" to "#1B262C", "pre" to "#0B1114", "warnbg" to "#3A2C16", "warnfg" to "#F2D49B", "warnline" to "#5A4520",
    ) else mapOf(
        "bg" to "#FFFFFF", "ink" to "#21333B", "title" to "#264653", "grey" to "#5A6B73", "teal" to "#2A9D8F",
        "line" to "#E4E0D4", "cream" to "#F8F6EF", "pre" to "#1C2B32", "warnbg" to "#FDF3E3", "warnfg" to "#7A4A1E", "warnline" to "#F0DCAE",
    )
    val css = """
        :root { color-scheme: ${if (dark) "dark" else "light"}; }
        * { box-sizing: border-box; }
        html, body { margin: 0; padding: 0; background: ${v["bg"]}; }
        body { font-family: -apple-system, Roboto, "Segoe UI", Helvetica, Arial, sans-serif; color: ${v["ink"]};
               line-height: 1.6; -webkit-text-size-adjust: none; overflow-wrap: break-word; }
        article { max-width: 680px; margin: 0 auto; padding: 22px 20px 64px; }
        h1.t { font-size: 1.6rem; line-height: 1.22; margin: 0; letter-spacing: -.01em; }
        h1.t a { color: ${v["title"]}; text-decoration: none; }
        .meta { color: ${v["grey"]}; font-size: .9rem; margin: 10px 0 0; }
        .meta a { color: ${v["teal"]}; font-weight: 700; text-decoration: none; }
        .sep { margin: 0 6px; }
        .actions { margin: 18px 0 8px; }
        .btn { display: inline-block; background: ${v["teal"]}; color: #fff; font-weight: 700; font-size: .9rem;
               padding: 9px 16px; border-radius: 999px; text-decoration: none; }
        .btn.busy { opacity: .6; pointer-events: none; }
        .note { margin: 6px 0 18px; padding: 12px 16px; border-radius: 10px; font-size: .92rem;
                color: ${v["warnfg"]}; background: ${v["warnbg"]}; border: 1px solid ${v["warnline"]}; }
        .note a { color: inherit; font-weight: 800; }
        .prose { font-size: 1.06rem; line-height: 1.75; margin-top: 18px; }
        .prose p { margin: 0 0 1.1em; }
        .prose h1, .prose h2 { font-size: 1.4rem; color: ${v["title"]}; margin: 1.6em 0 .5em; line-height: 1.3; }
        .prose h3 { font-size: 1.2rem; color: ${v["title"]}; margin: 1.4em 0 .4em; }
        .prose h4, .prose h5, .prose h6 { font-size: 1.05rem; color: ${v["title"]}; margin: 1.2em 0 .4em; }
        .prose a { color: ${v["teal"]}; }
        .prose img { max-width: 100%; height: auto; border-radius: 10px; margin: 1.2em 0; display: block; }
        .prose figure { margin: 1.4em 0; }
        .prose figcaption { color: ${v["grey"]}; font-size: .88rem; text-align: center; margin-top: 8px; }
        .prose ul, .prose ol { margin: 0 0 1.1em; padding-left: 1.3em; }
        .prose li { margin-bottom: .4em; }
        .prose blockquote { margin: 1.4em 0; padding: 6px 18px; border-left: 4px solid ${v["teal"]}; color: ${v["grey"]};
                            font-style: italic; background: ${v["cream"]}; border-radius: 0 8px 8px 0; }
        .prose pre { background: ${v["pre"]}; color: #e7eef0; padding: 14px 16px; border-radius: 10px; overflow-x: auto; font-size: .88rem; }
        .prose code { background: ${v["cream"]}; border: 1px solid ${v["line"]}; padding: 1px 5px; border-radius: 6px; font-size: .9em; }
        .prose pre code { background: none; border: 0; padding: 0; color: inherit; }
        .prose table { width: 100%; border-collapse: collapse; margin: 1.3em 0; font-size: .95rem; display: block; overflow-x: auto; }
        .prose th, .prose td { border: 1px solid ${v["line"]}; padding: 8px 10px; text-align: left; }
        .prose hr { border: 0; border-top: 1px solid ${v["line"]}; margin: 1.8em 0; }
        .fallback { color: ${v["grey"]}; }
    """.trimIndent()

    val body = buildString {
        append("<article>")
        append("<h1 class=\"t\">")
        if (a.link.isNotEmpty()) append("<a href=\"$link\">${esc(title)}</a>") else append(esc(title))
        append("</h1>")
        if (date.isNotEmpty() || a.link.isNotEmpty()) {
            append("<p class=\"meta\">")
            if (date.isNotEmpty()) append(esc(date))
            if (date.isNotEmpty() && a.link.isNotEmpty()) append("<span class=\"sep\">·</span>")
            if (a.link.isNotEmpty()) append("<a href=\"$link\">${esc(openOriginal)}</a>")
            append("</p>")
        }
        if (state.full != FullState.Hidden) {
            val busy = if (state.full == FullState.Loading) " busy" else ""
            append("<p class=\"actions\"><a class=\"btn$busy\" href=\"$FULL_ACTION\">${esc(fullLabel)}</a></p>")
        }
        if (state.warn) append("<div class=\"note\">${esc(note)} <a href=\"$link\">${esc(openOriginal)}</a></div>")
        append("<div class=\"prose\">")
        // bodyHtml has already been through Html.sanitize (allow-list, no scripts/handlers).
        if (state.bodyHtml.isNotBlank()) append(state.bodyHtml) else append("<p class=\"fallback\">${esc(fallback)}</p>")
        append("</div></article>")
    }
    return "<!doctype html><html><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><style>$css</style></head><body>$body</body></html>"
}

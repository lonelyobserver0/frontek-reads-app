package dev.frontek.feeds.ui

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.frontek.feeds.R
import dev.frontek.feeds.feed.HtmlUtils
import dev.frontek.feeds.model.Article
import dev.frontek.feeds.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    article: Article,
    isFavorite: Boolean,
    isReadLater: Boolean,
    onToggleFavorite: () -> Unit,
    onToggleReadLater: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Feed excerpt: cleaned + sanitized, or a plain summary fallback.
    val initialBody = remember(article.id) {
        val feedHtml = if (article.content.isNotBlank()) {
            HtmlUtils.cleanFeedHtml(article.content, article.link)
        } else {
            ""
        }
        if (feedHtml.isNotBlank()) {
            HtmlUtils.sanitize(feedHtml, article.link)
        } else {
            "<p>${article.summary.ifBlank { "Questo feed non fornisce un'anteprima." }}</p>"
        }
    }

    var body by remember(article.id) { mutableStateOf(initialBody) }
    var note by remember(article.id) { mutableStateOf<String?>(null) }
    var loadingFull by remember(article.id) { mutableStateOf(false) }
    var fullLoaded by remember(article.id) { mutableStateOf(false) }

    fun loadFull(auto: Boolean) {
        if (article.link.isBlank() || loadingFull) return
        loadingFull = true
        if (auto) note = null
        scope.launch {
            try {
                val (html, chars) = withContext(Dispatchers.IO) {
                    val page = Http.fetchText(article.link)
                    val res = HtmlUtils.extractArticle(page, article.link)
                    res
                }
                if (chars < 400) throw IllegalStateException("too short")
                body = HtmlUtils.sanitize(html, article.link)
                note = null
                fullLoaded = true
            } catch (e: Exception) {
                note = context.getString(R.string.reader_feed_preview_note)
            } finally {
                loadingFull = false
            }
        }
    }

    // Hybrid: auto-pull the full article if the feed gave only an excerpt.
    LaunchedEffect(article.id) {
        val onlyExcerpt = article.content.isBlank() || HtmlUtils.isTruncated(article.content)
        if (article.link.isNotBlank() && onlyExcerpt) loadFull(auto = true)
    }

    BackHandler(onBack = onClose)

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                article.source,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                article.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.reader_close))
                        }
                    },
                    actions = {
                        IconButton(onClick = onToggleReadLater) {
                            Icon(
                                imageVector = if (isReadLater) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                contentDescription = stringResource(if (isReadLater) R.string.read_later_remove else R.string.read_later_add),
                                tint = if (isReadLater) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                            )
                        }
                        IconButton(onClick = onToggleFavorite) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = stringResource(if (isFavorite) R.string.fav_remove else R.string.fav_add),
                                tint = if (isFavorite) MaterialTheme.colorScheme.secondary else LocalContentColor.current,
                            )
                        }
                        if (article.link.isNotBlank()) {
                            IconButton(onClick = { openExternal(context, article.link) }) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = stringResource(R.string.reader_open_original))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                note?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                ArticleWebView(
                    body = body,
                    baseUrl = article.link.ifBlank { "https://frontek.dev" },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                if (article.link.isNotBlank() && !fullLoaded) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Button(enabled = !loadingFull, onClick = { loadFull(auto = false) }) {
                            Text(stringResource(if (loadingFull) R.string.reader_loading_full else R.string.reader_read_full))
                        }
                    }
                }
                Spacer(Modifier.size(0.dp))
            }
        }
    }
}

@Composable
private fun ArticleWebView(body: String, baseUrl: String, modifier: Modifier) {
    val html = remember(body) { buildPage(body) }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.javaScriptEnabled = false
                settings.loadsImagesAutomatically = true
                settings.domStorageEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?,
                    ): Boolean {
                        val url = request?.url?.toString() ?: return false
                        openExternal(ctx, url)
                        return true
                    }
                }
            }
        },
        update = { web ->
            web.loadDataWithBaseURL(baseUrl, html, "text/html", "utf-8", null)
        },
    )
}

private fun buildPage(body: String): String = """
<!doctype html>
<html><head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
  html{color-scheme: light;}
  body{font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,Helvetica,Arial,sans-serif;
       line-height:1.62;color:#21333b;background:#f8f6ef;margin:0;padding:18px;font-size:17px;
       overflow-wrap:break-word;word-wrap:break-word;}
  img,picture,figure,video{max-width:100%;height:auto;border-radius:10px;margin:8px 0;}
  figure{margin-inline:0;}
  figcaption{font-size:.85em;color:#5a6b73;text-align:center;}
  a{color:#2a9d8f;text-decoration:none;}
  h1,h2,h3,h4{line-height:1.25;color:#264653;}
  pre{background:#eef0ea;border-radius:8px;padding:12px;overflow:auto;}
  code{background:#eef0ea;border-radius:4px;padding:1px 4px;}
  pre code{background:none;padding:0;}
  blockquote{border-left:3px solid #2a9d8f;margin:12px 0;padding:2px 0 2px 14px;color:#41545c;}
  table{border-collapse:collapse;max-width:100%;display:block;overflow:auto;}
  td,th{border:1px solid #e4e0d4;padding:6px 10px;}
</style></head>
<body>$body</body></html>
""".trimIndent()

private fun openExternal(context: android.content.Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
        // no browser available; ignore
    }
}

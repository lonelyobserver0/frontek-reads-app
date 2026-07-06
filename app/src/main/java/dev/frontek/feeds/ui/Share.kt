package dev.frontek.feeds.ui

import android.content.Context
import android.content.Intent
import dev.frontek.feeds.model.Article

/** Share an article via the system share sheet (title + link). */
fun shareArticle(context: Context, article: Article) {
    val body = if (article.link.isNotBlank()) {
        "${article.title}\n${article.link}"
    } else {
        article.title
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, article.title)
        putExtra(Intent.EXTRA_TEXT, body)
    }
    try {
        context.startActivity(Intent.createChooser(send, null))
    } catch (e: Exception) {
        // no app able to handle the share; ignore
    }
}

package dev.frontek.reads.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.frontek.reads.data.Article
import dev.frontek.reads.ui.AppViewModel
import dev.frontek.reads.ui.components.ArticleCard
import dev.frontek.reads.ui.components.openUrl

/** Header + article cards, shared by Home, Favorites and Read later. */
@Composable
fun ArticleList(
    vm: AppViewModel,
    articles: List<Article>,
    header: LazyListScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = rememberLazyListState(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        header()
        // Index in the key: the same article may come from two feeds.
        itemsIndexed(articles, key = { i, a -> "$i|${a.key}" }, contentType = { _, _ -> "article" }) { _, a ->
            ArticleCard(
                article = a,
                read = vm.isRead(a),
                favorite = vm.isFav(a),
                readLater = vm.isReadLater(a),
                onOpen = { vm.openReader(a) },
                onOpenOriginal = { vm.markRead(a); openUrl(ctx, a.link) },
                onToggleFav = { vm.toggleFavorite(a) },
                onToggleReadLater = { vm.toggleReadLater(a) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

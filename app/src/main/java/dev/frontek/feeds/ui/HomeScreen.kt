package dev.frontek.feeds.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.frontek.feeds.R
import dev.frontek.feeds.model.Article

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: AppViewModel,
    contentPadding: PaddingValues,
    onOpenArticle: (Article) -> Unit,
) {
    val subs = vm.subscriptions

    if (subs.isEmpty()) {
        EmptyHome(contentPadding)
        return
    }

    var query by remember { mutableStateOf("") }
    val base = vm.filteredItems
    val items = if (query.isBlank()) {
        base
    } else {
        val q = query.trim().lowercase()
        base.filter {
            it.title.lowercase().contains(q) ||
                it.summary.lowercase().contains(q) ||
                it.source.lowercase().contains(q)
        }
    }

    PullToRefreshBox(
        isRefreshing = vm.refreshing,
        onRefresh = { vm.refreshAll(force = true) },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    text = stringResource(
                        R.string.home_summary,
                        pluralStringResource(R.plurals.subscriptions_count, subs.size, subs.size),
                        pluralStringResource(R.plurals.articles_count, vm.homeItems.size, vm.homeItems.size),
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    placeholder = { Text(stringResource(R.string.home_search_placeholder)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotBlank()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.home_search_clear))
                            }
                        }
                    },
                    singleLine = true,
                )
            }
            item {
                ReadControls(vm)
            }
            if (subs.size >= 2) {
                item { SourceFilters(vm) }
            }
            items(items, key = { it.id + it.source }) { article ->
                ArticleCard(
                    article = article,
                    isFavorite = vm.isFavorite(article),
                    isReadLater = vm.isReadLater(article),
                    isRead = vm.isRead(article),
                    onOpen = onOpenArticle,
                    onToggleRead = { vm.toggleRead(it) },
                    onToggleFavorite = { vm.toggleFavorite(it) },
                    onToggleReadLater = { vm.toggleReadLater(it) },
                )
            }
            item { Spacer(Modifier.size(12.dp)) }
        }
    }
}

@Composable
private fun ReadControls(vm: AppViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = vm.unreadOnly,
            onClick = { vm.updateUnreadOnly(!vm.unreadOnly) },
            label = { Text(stringResource(R.string.home_unread_only)) },
            leadingIcon = if (vm.unreadOnly) {
                { Icon(Icons.Filled.MarkEmailRead, contentDescription = null, modifier = Modifier.size(18.dp)) }
            } else {
                null
            },
        )
        Spacer(Modifier.weight(1f))
        val allRead = vm.allHomeRead
        TextButton(onClick = { if (allRead) vm.markAllUnread() else vm.markAllRead() }) {
            Icon(
                imageVector = if (allRead) Icons.Filled.MarkEmailUnread else Icons.Filled.DoneAll,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(6.dp))
            Text(stringResource(if (allRead) R.string.home_mark_all_unread else R.string.home_mark_all_read))
        }
    }
}

@Composable
private fun SourceFilters(vm: AppViewModel) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = vm.activeSource == null,
                onClick = { vm.setSourceFilter(null) },
                label = { Text(stringResource(R.string.filter_all)) },
            )
        }
        items(vm.subscriptions, key = { it.feed }) { sub ->
            FilterChip(
                selected = vm.activeSource == sub.title,
                onClick = { vm.setSourceFilter(sub.title) },
                label = { Text(sub.title, maxLines = 1) },
            )
        }
    }
}

@Composable
private fun EmptyHome(contentPadding: PaddingValues) {
    Box(
        modifier = Modifier.fillMaxSize().padding(contentPadding).padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.home_empty_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.size(8.dp))
            Text(
                stringResource(R.string.home_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

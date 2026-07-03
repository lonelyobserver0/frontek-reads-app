package dev.frontek.feeds.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.frontek.feeds.R
import dev.frontek.feeds.feed.UrlUtils
import dev.frontek.feeds.model.FeedResult
import kotlinx.coroutines.delay

@Composable
fun DiscoverScreen(
    vm: AppViewModel,
    contentPadding: PaddingValues,
) {
    var query by remember { mutableStateOf("") }
    var debouncing by remember { mutableStateOf(false) }

    // Debounced dynamic search: each keystroke waits, then queries the web.
    LaunchedEffect(query) {
        if (query.isBlank()) {
            debouncing = false
            vm.search("")
        } else {
            debouncing = true
            delay(350)
            vm.search(query)
            debouncing = false
        }
    }

    val categories = remember(vm.catalog) { vm.catalog.mapNotNull { it.category }.distinct() }
    val showUrlCard = query.isNotBlank() && UrlUtils.looksLikeUrl(query)
    val loading = debouncing || vm.searching
    val results = if (query.isBlank()) vm.suggestions else vm.searchResults

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text(stringResource(R.string.discover_search_placeholder)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                },
                singleLine = true,
            )
        }

        if (query.isBlank() && categories.isNotEmpty()) {
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(categories, key = { it }) { cat ->
                        AssistChip(onClick = { query = cat }, label = { Text(cat) })
                    }
                }
            }
        }

        if (showUrlCard) {
            item { AddByUrlCard(vm, query) { query = "" } }
        }

        if (query.isNotBlank() && vm.searchUsedFallback && results.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.discover_search_fallback),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        items(results, key = { it.feed }) { result ->
            ResultCard(vm, result)
        }

        if (!loading && results.isEmpty() && query.isNotBlank() && !showUrlCard) {
            item {
                Text(
                    stringResource(R.string.discover_no_match, query),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        item { Spacer(Modifier.size(12.dp)) }
    }
}

@Composable
private fun ResultCard(vm: AppViewModel, result: FeedResult) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FeedIcon(result)
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    result.category?.let {
                        Text(
                            it.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        result.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        result.description ?: UrlUtils.host(result.site ?: result.feed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.size(10.dp))
            if (vm.isSubscribed(result.feed)) {
                OutlinedButton(onClick = {}, enabled = false) { Text(stringResource(R.string.discover_subscribed)) }
            } else {
                Button(onClick = { vm.subscribe(result.title, result.feed, result.site) }) {
                    Text(stringResource(R.string.discover_subscribe))
                }
            }
        }
    }
}

@Composable
private fun FeedIcon(result: FeedResult) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (!result.iconUrl.isNullOrBlank()) {
            AsyncImage(
                model = result.iconUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                result.title.trim().take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AddByUrlCard(vm: AppViewModel, url: String, onDone: () -> Unit) {
    var busy by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.discover_custom_url_label),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.size(2.dp))
            Text(UrlUtils.host(UrlUtils.normalize(url)), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                UrlUtils.normalize(url),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.size(8.dp))
            Button(
                enabled = !busy,
                onClick = {
                    busy = true
                    vm.addByUrl(url) { ok ->
                        busy = false
                        if (ok) onDone()
                    }
                },
            ) {
                Text(stringResource(if (busy) R.string.discover_searching else R.string.discover_find_subscribe))
            }
        }
    }
}


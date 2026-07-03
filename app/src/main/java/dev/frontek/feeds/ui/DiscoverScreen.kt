package dev.frontek.feeds.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.frontek.feeds.R
import dev.frontek.feeds.feed.UrlUtils
import dev.frontek.feeds.model.CatalogEntry

@Composable
fun DiscoverScreen(
    vm: AppViewModel,
    contentPadding: PaddingValues,
) {
    var query by remember { mutableStateOf("") }
    val q = query.trim().lowercase()

    val matches = vm.catalog.filter { f ->
        q.isEmpty() ||
            f.title.lowercase().contains(q) ||
            (f.category ?: "").lowercase().contains(q) ||
            (f.site ?: "").lowercase().contains(q)
    }
    val categories = remember(vm.catalog) {
        vm.catalog.mapNotNull { it.category }.distinct()
    }
    val showUrlCard = query.isNotBlank() && UrlUtils.looksLikeUrl(query)

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { vm.importOpmlFrom(it) } }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/xml"),
    ) { uri -> uri?.let { vm.exportOpmlTo(it) } }

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
                singleLine = true,
            )
        }
        if (categories.isNotEmpty()) {
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

        items(matches, key = { it.feed }) { entry ->
            CatalogCard(vm, entry)
        }

        if (matches.isEmpty() && !showUrlCard) {
            item {
                Text(
                    if (query.isNotBlank()) {
                        stringResource(R.string.discover_no_match, query)
                    } else {
                        stringResource(R.string.discover_loading_catalog)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        item {
            SubscriptionsSection(
                vm = vm,
                onImport = { importLauncher.launch(arrayOf("*/*")) },
                onExport = { exportLauncher.launch("frontek-reads.opml") },
            )
        }
        item { Spacer(Modifier.size(12.dp)) }
    }
}

@Composable
private fun CatalogCard(vm: AppViewModel, entry: CatalogEntry) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                (entry.category ?: "Feed").uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.size(2.dp))
            Text(
                entry.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                entry.feed,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.size(8.dp))
            if (vm.isSubscribed(entry.feed)) {
                OutlinedButton(onClick = {}, enabled = false) { Text(stringResource(R.string.discover_subscribed)) }
            } else {
                Button(onClick = { vm.subscribe(entry.title, entry.feed, entry.site) }) {
                    Text(stringResource(R.string.discover_subscribe))
                }
            }
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

@Composable
private fun SubscriptionsSection(
    vm: AppViewModel,
    onImport: () -> Unit,
    onExport: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Spacer(Modifier.size(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.discover_your_subs),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onImport) { Text(stringResource(R.string.action_import)) }
            TextButton(onClick = onExport) { Text(stringResource(R.string.action_export)) }
        }
        if (vm.subscriptions.isEmpty()) {
            Text(
                stringResource(R.string.discover_subs_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            vm.subscriptions.forEach { sub ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(sub.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            sub.feed,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    TextButton(onClick = { vm.unsubscribe(sub.feed) }) { Text(stringResource(R.string.action_remove)) }
                }
            }
        }
    }
}

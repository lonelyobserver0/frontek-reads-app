package dev.frontek.reads.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.frontek.reads.R
import dev.frontek.reads.data.CatalogEntry
import dev.frontek.reads.feed.Discovery
import dev.frontek.reads.ui.AppViewModel
import dev.frontek.reads.ui.components.FeedCard
import dev.frontek.reads.ui.components.HeaderChip
import dev.frontek.reads.ui.components.SectionHeader
import dev.frontek.reads.ui.theme.Brand
import dev.frontek.reads.ui.theme.LocalFrontekColors

@Composable
fun DiscoverScreen(vm: AppViewModel) {
    val c = LocalFrontekColors.current
    val focus = LocalFocusManager.current
    val d = vm.discover
    val categories = vm.catalog.map { it.category }.filter { it.isNotEmpty() }.distinct()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "header") {
            SectionHeader(stringResource(R.string.nav_discover), stringResource(R.string.discover_title)) {
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.discover_lead), color = c.onHeaderMuted, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = vm.query,
                    onValueChange = vm::onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.discover_search_placeholder)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = {
                        if (vm.query.isNotEmpty()) IconButton(onClick = { vm.onQueryChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close))
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); vm.submitQuery() }),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedBorderColor = Brand.Gold,
                        unfocusedBorderColor = Color.Transparent,
                    ),
                )
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.discover_privacy_note), color = c.onHeaderMuted, fontSize = 12.sp)
                if (categories.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(categories) { cat -> HeaderChip(cat, vm.query == cat) { focus.clearFocus(); vm.filterCategory(cat) } }
                    }
                }
            }
        }

        if (d.searching) item(key = "searching") {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        }

        d.urlCard?.let { url ->
            item(key = "url") {
                val norm = Discovery.normalizeUrl(url)
                Padded {
                    FeedCard(
                        label = stringResource(R.string.discover_custom_url_label),
                        title = Discovery.hostOf(norm), detail = norm, detailIsUrl = true,
                        subscribed = false, busy = d.findingUrl,
                        subscribeText = stringResource(R.string.discover_find_subscribe),
                        onSubscribe = { focus.clearFocus(); vm.findAndSubscribe(url) },
                    )
                }
            }
        }

        items(d.feedly, key = { "f:" + it.feed }) { r ->
            Padded {
                FeedCard(
                    label = Discovery.hostOf(r.site.ifEmpty { r.feed }),
                    title = r.title,
                    detail = r.description.ifEmpty { r.feed }, detailIsUrl = r.description.isEmpty(),
                    icon = r.icon,
                    subscribed = vm.isSubscribed(r.feed),
                    onSubscribe = { vm.subscribe(r) },
                    site = r.site,
                )
            }
        }

        if (d.fallbackHint) item(key = "fallback") { Hint(stringResource(R.string.discover_search_fallback)) }
        if (d.noMatch) item(key = "nomatch") { Hint(stringResource(R.string.discover_no_match, vm.query.trim())) }

        items(d.catalog, key = { "c:" + it.feed }) { e -> Padded { CatalogCard(vm, e) } }
    }
}

@Composable
private fun CatalogCard(vm: AppViewModel, e: CatalogEntry) {
    FeedCard(
        label = e.category.ifEmpty { "Feed" },
        title = e.title, detail = e.feed, detailIsUrl = true,
        subscribed = vm.isSubscribed(e.feed),
        onSubscribe = { vm.subscribe(e) },
        site = e.site,
    )
}

@Composable
private fun Padded(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(Modifier.padding(horizontal = 16.dp)) { content() }
}

@Composable
private fun Hint(text: String) {
    Text(text, color = LocalFrontekColors.current.muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp))
}

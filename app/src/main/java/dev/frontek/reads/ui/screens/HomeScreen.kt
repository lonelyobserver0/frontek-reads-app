package dev.frontek.reads.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.frontek.reads.R
import dev.frontek.reads.ui.AppViewModel
import dev.frontek.reads.ui.Tab
import dev.frontek.reads.ui.components.EmptyState
import dev.frontek.reads.ui.components.HeaderChip
import dev.frontek.reads.ui.components.SectionHeader
import dev.frontek.reads.ui.theme.LocalFrontekColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: AppViewModel) {
    val c = LocalFrontekColors.current
    val subs = vm.subs
    val active = vm.activeSource
    val items = if (active != null) vm.homeItems.filter { it.source == active } else vm.homeItems

    val status = when {
        vm.refreshing -> pluralStringResource(R.plurals.status_refreshing, subs.size, subs.size)
        vm.failures.isNotEmpty() -> stringResource(R.string.status_load_failed, vm.failures.joinToString(", "))
        else -> null
    }

    PullToRefreshBox(isRefreshing = vm.refreshing, onRefresh = { vm.refreshAll(true) }, modifier = Modifier.fillMaxSize()) {
        ArticleList(vm, items, header = {
            item(key = "header", contentType = "header") {
                SectionHeader(stringResource(R.string.home_kicker), stringResource(R.string.home_title)) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (subs.isEmpty()) stringResource(R.string.home_empty_title)
                        else pluralStringResource(R.plurals.subscriptions_count, subs.size, subs.size) + " · " +
                            pluralStringResource(R.plurals.articles_count, vm.homeItems.size, vm.homeItems.size),
                        color = c.onHeaderMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    )
                    if (subs.size >= 2) {
                        Spacer(Modifier.height(12.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item { HeaderChip(stringResource(R.string.filter_all), active == null) { vm.selectSource(null) } }
                            items(subs, key = { it.feed }) { s -> HeaderChip(s.title, active == s.title) { vm.selectSource(s.title) } }
                        }
                    }
                }
            }
            if (subs.isEmpty()) item(key = "empty") {
                EmptyState("📡", stringResource(R.string.home_empty_title), stringResource(R.string.home_empty_body)) {
                    Button(onClick = { vm.tab = Tab.Discover }) { Text(stringResource(R.string.nav_discover)) }
                }
            }
            if (status != null && subs.isNotEmpty()) item(key = "status") {
                Text(
                    status,
                    color = if (vm.refreshing) c.muted else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        })
    }
}

@Composable
fun SavedScreen(vm: AppViewModel, favorites: Boolean) {
    val list = if (favorites) vm.favorites else vm.readLater
    val title = stringResource(if (favorites) R.string.nav_favorites else R.string.nav_read_later)
    ArticleList(vm, list, header = {
        item(key = "header") { SectionHeader(title, title) }
        if (list.isEmpty()) item(key = "empty") {
            EmptyState(
                if (favorites) "♥" else "🔖", null,
                stringResource(if (favorites) R.string.saved_favorites_empty else R.string.saved_read_later_empty),
            )
        }
    })
}

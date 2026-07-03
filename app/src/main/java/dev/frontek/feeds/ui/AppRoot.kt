package dev.frontek.feeds.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight

private enum class Tab { Home, Favorites, ReadLater, Discover }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: AppViewModel) {
    var tab by remember { mutableStateOf(Tab.Home) }
    var reader by remember { mutableStateOf<dev.frontek.feeds.model.Article?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(vm.toast) {
        vm.toast?.let {
            snackbar.showSnackbar(it)
            vm.consumeToast()
        }
    }
    LaunchedEffect(vm.statusMessage) {
        vm.statusMessage?.let { snackbar.showSnackbar(it) }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("frontek reads", fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = { vm.refreshAll(force = true) }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Aggiorna")
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Impostazioni")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == Tab.Home,
                        onClick = { tab = Tab.Home },
                        icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                        label = { Text("Home") },
                    )
                    NavigationBarItem(
                        selected = tab == Tab.Favorites,
                        onClick = { tab = Tab.Favorites },
                        icon = { Icon(Icons.Filled.Favorite, contentDescription = null) },
                        label = { Text("Preferiti") },
                    )
                    NavigationBarItem(
                        selected = tab == Tab.ReadLater,
                        onClick = { tab = Tab.ReadLater },
                        icon = { Icon(Icons.Filled.Bookmark, contentDescription = null) },
                        label = { Text("Leggi dopo") },
                    )
                    NavigationBarItem(
                        selected = tab == Tab.Discover,
                        onClick = { tab = Tab.Discover },
                        icon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        label = { Text("Scopri") },
                    )
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize()) {
                when (tab) {
                    Tab.Home -> HomeScreen(vm, padding) { reader = it }
                    Tab.Favorites -> SavedScreen(
                        vm = vm,
                        items = vm.favorites,
                        title = "Preferiti",
                        emptyText = "Tocca il cuore su un articolo per salvarlo qui.",
                        contentPadding = padding,
                        onOpenArticle = { reader = it },
                    )
                    Tab.ReadLater -> SavedScreen(
                        vm = vm,
                        items = vm.readLaterItems,
                        title = "Leggi più tardi",
                        emptyText = "Tocca il segnalibro su un articolo per leggerlo più tardi.",
                        contentPadding = padding,
                        onOpenArticle = { reader = it },
                    )
                    Tab.Discover -> DiscoverScreen(vm, padding)
                }
                if (vm.refreshing) {
                    LinearProgressIndicator(
                        Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .padding(padding),
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = reader != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        ) {
            reader?.let { article ->
                ReaderScreen(
                    article = article,
                    isFavorite = vm.isFavorite(article),
                    isReadLater = vm.isReadLater(article),
                    onToggleFavorite = { vm.toggleFavorite(article) },
                    onToggleReadLater = { vm.toggleReadLater(article) },
                    onClose = { reader = null },
                )
            }
        }
    }

    if (showSettings) {
        SettingsDialog(vm, onDismiss = { showSettings = false })
    }
}

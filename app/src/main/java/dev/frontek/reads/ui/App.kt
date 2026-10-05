package dev.frontek.reads.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import dev.frontek.reads.R
import dev.frontek.reads.ui.screens.DiscoverScreen
import dev.frontek.reads.ui.screens.HomeScreen
import dev.frontek.reads.ui.screens.ReaderScreen
import dev.frontek.reads.ui.screens.SavedScreen
import dev.frontek.reads.ui.screens.SettingsScreen
import dev.frontek.reads.ui.screens.SubsScreen
import dev.frontek.reads.ui.theme.Brand
import dev.frontek.reads.ui.theme.LocalFrontekColors

private data class Visuals(
    override val message: String,
    val error: Boolean,
    override val actionLabel: String? = null,
    override val withDismissAction: Boolean = false,
    override val duration: SnackbarDuration = SnackbarDuration.Short,
) : SnackbarVisuals

@Composable
fun FrontekApp(vm: AppViewModel) {
    // App-wide text size, like the site's font-scale setting.
    val base = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * vm.fontScale / 100f)) {
        val reader = vm.reader
        val overlay = vm.overlay
        BackHandler(enabled = reader != null || overlay != null || vm.tab != Tab.Home) {
            when {
                reader != null -> vm.closeReader()
                overlay != null -> vm.overlay = null
                else -> vm.tab = Tab.Home
            }
        }
        Box(Modifier.fillMaxSize()) {
            MainScaffold(vm)
            if (overlay != null) Surface(Modifier.fillMaxSize()) {
                when (overlay) {
                    Overlay.Subs -> SubsScreen(vm)
                    Overlay.Settings -> SettingsScreen(vm)
                }
            }
            if (reader != null) Surface(Modifier.fillMaxSize()) { ReaderScreen(vm, reader) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(vm: AppViewModel) {
    val ctx = LocalContext.current
    val c = LocalFrontekColors.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.messages.collect { m ->
            val text = if (m.plural != 0) ctx.resources.getQuantityString(m.plural, m.count, m.count)
            else ctx.getString(m.res, *m.args.toTypedArray())
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(Visuals(text, m.error))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = c.title)) { append("FRONTEK ") }
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("READS") }
                        },
                        fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, letterSpacing = (-0.4).sp,
                    )
                },
                actions = {
                    IconButton(onClick = { vm.overlay = Overlay.Subs }) {
                        Icon(Icons.AutoMirrored.Filled.List, stringResource(R.string.discover_your_subs))
                    }
                    IconButton(onClick = { vm.refreshAll(true) }, enabled = !vm.refreshing) {
                        Icon(Icons.Filled.Refresh, stringResource(R.string.action_refresh))
                    }
                    IconButton(onClick = { vm.overlay = Overlay.Settings }) {
                        Icon(Icons.Filled.Settings, stringResource(R.string.action_settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    actionIconContentColor = c.title,
                ),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavItem(vm, Tab.Home, Icons.Filled.Home, R.string.nav_home)
                NavItem(vm, Tab.Favorites, Icons.Filled.Favorite, R.string.nav_favorites)
                NavItem(vm, Tab.ReadLater, Icons.Filled.Bookmark, R.string.nav_read_later)
                NavItem(vm, Tab.Discover, Icons.Filled.Explore, R.string.nav_discover)
            }
        },
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                val err = (data.visuals as? Visuals)?.error == true
                Snackbar(data, containerColor = if (err) MaterialTheme.colorScheme.error else Brand.Dark, contentColor = Color.White)
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (vm.tab) {
                Tab.Home -> HomeScreen(vm)
                Tab.Favorites -> SavedScreen(vm, favorites = true)
                Tab.ReadLater -> SavedScreen(vm, favorites = false)
                Tab.Discover -> DiscoverScreen(vm)
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.NavItem(vm: AppViewModel, tab: Tab, icon: ImageVector, label: Int) {
    NavigationBarItem(
        selected = vm.tab == tab,
        onClick = { vm.tab = tab },
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(stringResource(label), maxLines = 1) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    )
}

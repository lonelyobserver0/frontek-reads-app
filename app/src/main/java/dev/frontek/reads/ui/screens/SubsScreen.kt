package dev.frontek.reads.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.frontek.reads.R
import dev.frontek.reads.ui.AppViewModel
import dev.frontek.reads.ui.components.EmptyState
import dev.frontek.reads.ui.components.SectionHeader
import dev.frontek.reads.ui.components.cardBorder
import dev.frontek.reads.ui.theme.LocalFrontekColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverlayScaffold(title: String, onBack: () -> Unit, content: @Composable (PaddingValues) -> Unit) {
    val c = LocalFrontekColors.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = c.header, titleContentColor = c.onHeader, navigationIconContentColor = c.onHeader,
                ),
            )
        },
        content = content,
    )
}

@Composable
fun SubsScreen(vm: AppViewModel) {
    val c = LocalFrontekColors.current
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-opml")) { uri ->
        if (uri != null) vm.exportTo(uri)
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFrom(uri)
    }
    OverlayScaffold(stringResource(R.string.discover_your_subs), onBack = { vm.overlay = null }) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "header") {
                SectionHeader(stringResource(R.string.discover_your_subs), stringResource(R.string.subs_manage_title)) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            pluralStringResource(R.plurals.subscriptions_count, vm.subs.size, vm.subs.size),
                            color = c.onHeaderMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        val light = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        OutlinedButton(onClick = { if (vm.canExport()) exporter.launch("frontek-reads.opml") }, colors = light) {
                            Text(stringResource(R.string.action_export))
                        }
                        Spacer(Modifier.padding(4.dp))
                        OutlinedButton(onClick = { importer.launch(arrayOf("text/xml", "application/xml", "text/x-opml", "application/json", "application/octet-stream", "text/plain")) }, colors = light) {
                            Text(stringResource(R.string.action_import))
                        }
                    }
                }
            }
            if (vm.subs.isEmpty()) item(key = "empty") { EmptyState("📡", null, stringResource(R.string.discover_subs_empty)) }
            items(vm.subs, key = { it.feed }) { s ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(12.dp), border = cardBorder(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Row(Modifier.padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.title, fontWeight = FontWeight.ExtraBold, color = c.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(s.feed, fontSize = 12.sp, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(onClick = { vm.unsubscribe(s.feed) }) { Text(stringResource(R.string.action_remove)) }
                    }
                }
            }
            item(key = "backup") {
                Text(
                    stringResource(R.string.footer_backup), color = c.muted, fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }
    }
}

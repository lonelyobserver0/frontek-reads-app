package dev.frontek.reads.ui.screens

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import dev.frontek.reads.R
import dev.frontek.reads.ui.AppViewModel
import dev.frontek.reads.ui.theme.KickerStyle
import dev.frontek.reads.ui.theme.LocalFrontekColors

private val LANGS = listOf(
    "" to R.string.lang_system,
    "it" to R.string.lang_italian,
    "en" to R.string.lang_english,
    "es" to R.string.lang_spanish,
    "fr" to R.string.lang_french,
)

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val c = LocalFrontekColors.current
    var confirmDelete by remember { mutableStateOf(false) }
    val currentLang = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore('-')

    OverlayScaffold(stringResource(R.string.settings_title), onBack = { vm.overlay = null }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Label(stringResource(R.string.settings_language))
            Column(Modifier.selectableGroup()) {
                LANGS.forEach { (tag, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = currentLang == tag, role = Role.RadioButton, onClick = {
                                // Persisted by AppCompat and applied by recreating the activity.
                                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                            })
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = currentLang == tag, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(label))
                    }
                }
            }

            HorizontalDivider(color = c.line)

            Label(stringResource(R.string.settings_text_size))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = { vm.changeFontScale(vm.fontScale - 10) }, enabled = vm.fontScale > 80) {
                    Icon(Icons.Filled.Remove, stringResource(R.string.font_decrease))
                }
                Text("${vm.fontScale}%", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, modifier = Modifier.width(72.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                FilledTonalIconButton(onClick = { vm.changeFontScale(vm.fontScale + 10) }, enabled = vm.fontScale < 180) {
                    Icon(Icons.Filled.Add, stringResource(R.string.font_increase))
                }
                Spacer(Modifier.width(12.dp))
                OutlinedButton(onClick = { vm.changeFontScale(100) }) { Text(stringResource(R.string.font_reset)) }
            }

            HorizontalDivider(color = c.line)

            Label(stringResource(R.string.settings_data))
            OutlinedButton(onClick = vm::clearCache, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_clear_cache)) }
            Button(
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.settings_delete_all)) }

            HorizontalDivider(color = c.line)

            Text(stringResource(R.string.app_name), fontWeight = FontWeight.ExtraBold, color = c.title)
            Text(stringResource(R.string.settings_about), color = c.muted, style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.footer_backup), color = c.muted, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = { Text(stringResource(R.string.confirm_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteAll()
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text.uppercase(), style = KickerStyle, color = MaterialTheme.colorScheme.primary)
}

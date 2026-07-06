package dev.frontek.feeds.ui

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.frontek.feeds.R
import dev.frontek.feeds.ui.theme.ThemeMode
import dev.frontek.feeds.ui.theme.dynamicColorAvailable
import dev.frontek.feeds.work.Notifications

@Composable
fun SettingsDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var confirmClearAll by remember { mutableStateOf(false) }
    var language by remember { mutableStateOf(LocaleManager.current()) }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            vm.updateNotifications(true)
        } else {
            Toast.makeText(context, R.string.notif_permission_denied, Toast.LENGTH_LONG).show()
        }
    }

    fun toggleNotifications(want: Boolean) {
        if (want) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !Notifications.hasPermission(context)
            ) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                vm.updateNotifications(true)
            }
        } else {
            vm.updateNotifications(false)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.settings_body, vm.subscriptions.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.size(16.dp))
                Text(
                    stringResource(R.string.settings_text_size),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = { vm.decreaseFont() },
                        enabled = vm.canDecreaseFont,
                    ) { Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.font_decrease)) }
                    Text(
                        "${vm.fontScalePercent}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(
                        onClick = { vm.increaseFont() },
                        enabled = vm.canIncreaseFont,
                    ) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.font_increase)) }
                }
                TextButton(
                    onClick = { vm.resetFont() },
                    modifier = Modifier.align(Alignment.End),
                ) { Text(stringResource(R.string.font_reset)) }

                Spacer(Modifier.size(16.dp))
                Text(
                    stringResource(R.string.settings_language),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                LanguageOption(R.string.lang_system, language == LocaleManager.SYSTEM) {
                    language = LocaleManager.SYSTEM
                    LocaleManager.set(LocaleManager.SYSTEM)
                }
                LanguageOption(R.string.lang_italian, language == LocaleManager.ITALIAN) {
                    language = LocaleManager.ITALIAN
                    LocaleManager.set(LocaleManager.ITALIAN)
                }
                LanguageOption(R.string.lang_english, language == LocaleManager.ENGLISH) {
                    language = LocaleManager.ENGLISH
                    LocaleManager.set(LocaleManager.ENGLISH)
                }
                LanguageOption(R.string.lang_spanish, language == LocaleManager.SPANISH) {
                    language = LocaleManager.SPANISH
                    LocaleManager.set(LocaleManager.SPANISH)
                }
                LanguageOption(R.string.lang_french, language == LocaleManager.FRENCH) {
                    language = LocaleManager.FRENCH
                    LocaleManager.set(LocaleManager.FRENCH)
                }

                Spacer(Modifier.size(16.dp))
                Text(
                    stringResource(R.string.settings_theme),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                LanguageOption(R.string.theme_system, vm.themeMode == ThemeMode.SYSTEM) {
                    vm.updateThemeMode(ThemeMode.SYSTEM)
                }
                LanguageOption(R.string.theme_light, vm.themeMode == ThemeMode.LIGHT) {
                    vm.updateThemeMode(ThemeMode.LIGHT)
                }
                LanguageOption(R.string.theme_dark, vm.themeMode == ThemeMode.DARK) {
                    vm.updateThemeMode(ThemeMode.DARK)
                }
                if (dynamicColorAvailable) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.settings_dynamic_color),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                stringResource(R.string.settings_dynamic_color_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.size(8.dp))
                        Switch(
                            checked = vm.dynamicColor,
                            onCheckedChange = { vm.updateDynamicColor(it) },
                        )
                    }
                }

                Spacer(Modifier.size(16.dp))
                Text(
                    stringResource(R.string.settings_notifications),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.settings_notifications_label),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            stringResource(R.string.settings_notifications_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.size(8.dp))
                    Switch(
                        checked = vm.notificationsEnabled,
                        onCheckedChange = { toggleNotifications(it) },
                    )
                }

                Spacer(Modifier.size(16.dp))
                OutlinedButton(
                    onClick = { vm.clearCache() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.settings_clear_cache)) }
                Spacer(Modifier.size(8.dp))
                OutlinedButton(
                    onClick = { confirmClearAll = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.settings_delete_all)) }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text(stringResource(R.string.confirm_delete_title)) },
            text = { Text(stringResource(R.string.confirm_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearAll()
                    confirmClearAll = false
                    onDismiss()
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun LanguageOption(labelRes: Int, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(stringResource(labelRes), style = MaterialTheme.typography.bodyLarge)
    }
}

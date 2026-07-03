package dev.frontek.feeds.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsDialog(vm: AppViewModel, onDismiss: () -> Unit) {
    var confirmClearAll by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Impostazioni") },
        text = {
            Column {
                Text(
                    "${vm.subscriptions.size} iscrizioni. I feed vengono scaricati direttamente, " +
                        "senza proxy né account: tutto resta sul dispositivo.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(16.dp))
                OutlinedButton(
                    onClick = { vm.clearCache() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Svuota la cache") }
                Spacer(Modifier.size(8.dp))
                OutlinedButton(
                    onClick = { confirmClearAll = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Elimina tutti i dati") }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Chiudi") }
        },
    )

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text("Eliminare tutto?") },
            text = { Text("Rimuove tutte le iscrizioni e la cache da questo dispositivo.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.clearAll()
                    confirmClearAll = false
                    onDismiss()
                }) { Text("Elimina") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) { Text("Annulla") }
            },
        )
    }
}

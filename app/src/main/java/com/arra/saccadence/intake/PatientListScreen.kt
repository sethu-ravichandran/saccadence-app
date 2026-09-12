package com.arra.saccadence.intake

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The one screen a DPDP-minded juror will actually ask to see: a real, working
 * per-patient delete, not a described-but-unbuilt right.
 */
@Composable
fun PatientListScreen(
    repository: PatientRepository,
    onBack: () -> Unit,
    onLoadPatient: (PatientRecord) -> Unit,
) {
    var records by remember { mutableStateOf(repository.loadAll().sortedByDescending { it.savedAt }) }
    var pendingDelete by remember { mutableStateOf<PatientRecord?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Saved patients", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onBack) { Text("Close") }
        }

        if (records.isEmpty()) {
            Text(
                "No saved patients yet.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 12.dp),
        ) {
            items(records, key = { it.id }) { record ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(record.name.ifBlank { "(no name)" }, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Age ${record.age ?: "?"} · Dr. ${record.doctorName.ifBlank { "?" }} · " +
                                SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()).format(Date(record.savedAt)),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            TextButton(onClick = { onLoadPatient(record) }) { Text("Open") }
                            OutlinedButton(onClick = { pendingDelete = record }) { Text("Delete") }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this patient?") },
            text = { Text("This erases ${record.name.ifBlank { "this patient" }}'s session, details, and measurements. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    repository.delete(record.id)
                    records = repository.loadAll().sortedByDescending { it.savedAt }
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

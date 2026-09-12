package com.arra.saccadence.intake

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun PatientDetailsScreen(
    session: PatientSession,
    repository: PatientRepository,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    var lookupMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StepProgress(step = 1, label = "Patient details")

        OutlinedTextField(
            value = session.name,
            onValueChange = { session.name = it; lookupMessage = null },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = session.age?.toString() ?: "",
            onValueChange = { text ->
                session.age = text.filter { it.isDigit() }.take(3).toIntOrNull()
            },
            label = { Text("Age") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = session.mobile,
            onValueChange = { session.mobile = it },
            label = { Text("Mobile (optional)") },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )

        // Nice-to-have: local lookup for a returning patient, only useful once
        // there's history to find — never required to move forward.
        TextButton(onClick = {
            val found = repository.findByNameAndAge(session.name, session.age)
            lookupMessage = if (found != null) {
                session.resetTo(found)
                "Loaded previous visit for ${found.name}."
            } else {
                "No previous visit found for this name/age."
            }
        }) {
            Text("Have we seen this patient before?")
        }
        lookupMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            TextButton(onClick = onBack) { Text("Back") }
            Button(onClick = onNext, enabled = session.isDetailsValid) { Text("Next") }
        }
    }
}

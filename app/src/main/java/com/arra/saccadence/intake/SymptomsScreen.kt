package com.arra.saccadence.intake

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The answer to "an abnormal number has a dozen possible causes": predefined,
 * identical toggles for every patient, so a reading is recorded under known
 * conditions and stays comparable across that patient's future visits.
 */
@Composable
fun SymptomsScreen(
    session: PatientSession,
    repository: PatientRepository,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        StepProgress(step = 3, total = 7, label = "Symptom / context")

        SymptomFlag.entries.forEach { flag ->
            val checked = session.symptoms[flag] == true
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { session.symptoms[flag] = !checked },
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { session.symptoms[flag] = it },
                )
                Text(flag.question)
            }
        }

        OutlinedTextField(
            value = session.notes,
            onValueChange = { session.notes = it },
            label = { Text("Notes (optional)") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            minLines = 3,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        ) {
            TextButton(onClick = onBack) { Text("Back") }
            Button(onClick = onNext) { Text("Continue to consent") }
        }
    }
}

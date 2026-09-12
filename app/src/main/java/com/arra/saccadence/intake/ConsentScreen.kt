package com.arra.saccadence.intake

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Consent appearing before any patient data is entered — not buried in settings —
 * is the whole DPDP-privacy answer for this app. Keep this screen exactly this
 * blunt: what's captured, what's kept, what's never kept, what's never sent.
 */
@Composable
fun ConsentScreen(
    session: PatientSession,
    onConsented: () -> Unit,
    onDemoPatient: () -> Unit,
    onOpenPatients: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Before we begin", style = MaterialTheme.typography.headlineSmall)

        ConsentPoint("What's captured", "A short video of the eyes during the test, plus the details you enter.")
        ConsentPoint("What's kept", "The measured numbers, and the patient details — on this device only.")
        ConsentPoint("What's never kept", "The video itself. It's processed frame by frame and discarded immediately.")
        ConsentPoint("What's never sent", "Anything. No upload, no cloud, no account.")
        ConsentPoint("Retention + erasure", "Kept until deleted. One tap erases this patient entirely.")

        Button(
            onClick = { session.consentGivenAt = System.currentTimeMillis(); onConsented() },
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text("I consent")
        }

        OutlinedButton(onClick = onDemoPatient) {
            Text("Use demo patient (skip to calibration)")
        }

        TextButton(onClick = onOpenPatients) {
            Text("View / erase saved patients")
        }
    }
}

@Composable
private fun ConsentPoint(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(body, style = MaterialTheme.typography.bodyMedium)
    }
}

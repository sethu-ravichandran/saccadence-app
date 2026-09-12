package com.arra.saccadence.intake

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private const val MINOR_AGE_CUTOFF = 18

/**
 * The consent here belongs to the patient (or, if the patient is a minor, their
 * parent/lawful guardian per DPDPA 2023 s.9) — the clinician is only the one
 * operating the device on their behalf, not the one giving consent. So this
 * screen is written to be read aloud to (or by) the patient/guardian directly,
 * and the affirmative act — the checkbox, then the button — is described as
 * theirs, not the staff's.
 *
 * It's also the whole DPDP-privacy answer for this app: notice before capture
 * (not buried in settings), purpose limitation, data minimisation (the video
 * itself is never kept), storage limitation, on-device-only processing in lieu
 * of a security safeguard disclosure, and the patient's rights to access,
 * correct, erase, and withdraw consent at any time, as easily as it was given.
 */
@Composable
fun ConsentScreen(
    session: PatientSession,
    onBack: () -> Unit = {},
    onConsented: () -> Unit,
    onDemoPatient: () -> Unit,
    onOpenPatients: () -> Unit,
) {
    val isMinor = (session.age ?: 0) < MINOR_AGE_CUTOFF
    val consentGiver = if (isMinor) "the parent/guardian" else "the patient"
    var acknowledged by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StepProgress(step = 4, total = 7, label = "Consent")
        Text("For the patient", style = MaterialTheme.typography.headlineSmall)
        Text(
            if (isMinor) {
                "Staff: please read this notice aloud to the patient's parent or guardian before continuing — the patient is a minor, so consent must come from them."
            } else {
                "Staff: please read this notice aloud to the patient (or hand them the device to read) before continuing."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ConsentPoint("What's captured", "A short video of your eyes during the test, plus the details entered for you on the previous screens.")
        ConsentPoint("Why", "Only to measure how your eyes move — nothing else is done with it.")
        ConsentPoint("What's kept", "The measured results and your details — on this device only, never on a clinic network or the cloud.")
        ConsentPoint("What's never kept", "The video itself. It's processed frame by frame and discarded immediately.")
        ConsentPoint("What's never sent", "Anything. No upload, no cloud, no account.")
        ConsentPoint("Retention + erasure", "Kept until you ask for it to be deleted. Any member of staff can erase it for you on this screen, any time.")
        ConsentPoint(
            "Your rights, under the DPDPA, 2023",
            "You can ask to see, correct, or erase your data at any time, and you can withdraw this consent as easily as you're giving it — doing so stops the test immediately.",
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) {
            Checkbox(checked = acknowledged, onCheckedChange = { acknowledged = it })
            Text(
                "I confirm $consentGiver has read or heard this notice, understands it, and freely agrees to proceed.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            TextButton(onClick = onBack) { Text("Back") }
            Button(
                enabled = acknowledged,
                onClick = { session.consentGivenAt = System.currentTimeMillis(); onConsented() },
            ) {
                Text(if (isMinor) "Guardian consents" else "Patient consents")
            }
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

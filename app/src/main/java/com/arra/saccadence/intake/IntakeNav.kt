package com.arra.saccadence.intake

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** "Step X of Y" header shown above every form step. Back is always allowed and never clears state. */
@Composable
fun StepProgress(step: Int, total: Int = 7, label: String) {
    Text(
        text = "Step $step of $total — $label",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
    LinearProgressIndicator(
        progress = { step / total.toFloat() },
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
    )
}

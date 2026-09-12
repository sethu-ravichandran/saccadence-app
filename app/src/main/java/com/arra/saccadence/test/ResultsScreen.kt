package com.arra.saccadence.test

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arra.saccadence.notes.ClinicNote
import com.arra.saccadence.trial.BlandAltmanResult
import com.arra.saccadence.trial.TrialResult

/**
 * Reports two numbers and how they moved since the last visit — per the
 * deck's scope page, this is an instrument's readout, not a diagnosis, and
 * every number here traces to a value in [result]: nothing is invented for
 * display.
 */
@Composable
fun ResultsScreen(
    result: TrialResult,
    previous: TrialResult?,
    repeatabilityLatency: BlandAltmanResult?,
    repeatabilityGain: BlandAltmanResult?,
    clinicNote: ClinicNote,
    onRunAnother: () -> Unit,
    onDone: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Result", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        QualityBadge(result.qualityStatus, result.qualityReasons)
        Spacer(Modifier.height(20.dp))

        MetricRow(
            label = "Saccade onset latency",
            value = result.medianLatencyMs?.let { "%.0f ms".format(it) } ?: "— (no valid steps)",
            comparison = comparisonText(result.medianLatencyMs, previous?.medianLatencyMs, unit = "ms", lowerIsBetter = true),
        )
        Spacer(Modifier.height(12.dp))
        MetricRow(
            label = "Smooth-pursuit gain",
            value = result.meanPursuitGain?.let { "%.2f".format(it) } ?: "— (no valid sweeps)",
            comparison = comparisonText(result.meanPursuitGain, previous?.meanPursuitGain, unit = "", lowerIsBetter = false),
        )

        Spacer(Modifier.height(24.dp))
        Text("Steps: ${result.validSteps.size} valid / ${result.steps.size} total", style = MaterialTheme.typography.bodyMedium)
        Text("Sweeps: ${result.validSweeps.size} valid / ${result.sweeps.size} total", style = MaterialTheme.typography.bodyMedium)

        result.errorBudget?.let { budget ->
            Spacer(Modifier.height(24.dp))
            Text("Error budget", style = MaterialTheme.typography.titleMedium)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            BudgetLine("Frame period (${"%.1f".format(result.measuredFps ?: 0.0)} fps)", "%.1f ms".format(budget.framePeriodMs))
            BudgetLine("Opening calibration jitter", "%.1f ms".format(budget.preCalibrationJitterMs))
            BudgetLine("Closing calibration jitter", "%.1f ms".format(budget.postCalibrationJitterMs))
            BudgetLine("Measured clock drift across trial", "%.1f ms".format(budget.measuredDriftMs))
            BudgetLine("Rolling-shutter residual", "%.1f ms".format(budget.rollingShutterResidualMs))
            BudgetLine("Landmark jitter", "%.2f°".format(budget.landmarkSigmaDeg))
            BudgetLine("Residual head drift", "%.2f°".format(budget.residualHeadDriftDeg))
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            BudgetLine("Total timing budget (RSS)", "%.1f ms".format(budget.totalTimingBudgetMs), emphasize = true)
        }

        if (repeatabilityLatency != null || repeatabilityGain != null) {
            Spacer(Modifier.height(24.dp))
            Text("Repeatability vs. previous trial (Bland-Altman)", style = MaterialTheme.typography.titleMedium)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            repeatabilityLatency?.let {
                BudgetLine("Latency bias", "%.1f ms".format(it.biasMs))
                BudgetLine("Latency limits of agreement", "%.1f to %.1f ms".format(it.lowerLimitOfAgreement, it.upperLimitOfAgreement))
            }
            repeatabilityGain?.let {
                BudgetLine("Gain bias", "%.3f".format(it.biasMs))
                BudgetLine("Gain limits of agreement", "%.3f to %.3f".format(it.lowerLimitOfAgreement, it.upperLimitOfAgreement))
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("Clinic note (${if (clinicNote.isDraft) "draft — ${clinicNote.source}" else clinicNote.source})", style = MaterialTheme.typography.titleMedium)
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text(clinicNote.text, style = MaterialTheme.typography.bodyMedium)

        Spacer(Modifier.height(24.dp))
        Text(
            "This is a longitudinal measurement instrument, not a diagnostic tool. It does not replace a validated videonystagmography assessment.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(32.dp))
        Button(onClick = onRunAnother, modifier = Modifier.fillMaxWidth()) { Text("Repeat trial") }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
    }
}

@Composable
private fun QualityBadge(status: String, reasons: List<String>) {
    val color = when (status) {
        "ok" -> MaterialTheme.colorScheme.primary
        "flagged" -> androidx.compose.ui.graphics.Color(0xFF8A5000)
        else -> MaterialTheme.colorScheme.error
    }
    Text("Quality: $status", style = MaterialTheme.typography.titleMedium, color = color)
    if (reasons.isNotEmpty()) {
        Text(reasons.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MetricRow(label: String, value: String, comparison: String?) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.displaySmall)
        if (comparison != null) Text(comparison, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BudgetLine(label: String, value: String, emphasize: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Normal)
    }
}

private fun comparisonText(current: Double?, previous: Double?, unit: String, lowerIsBetter: Boolean): String? {
    if (current == null) return null
    if (previous == null) return "No prior measurement."
    val delta = current - previous
    val direction = if ((lowerIsBetter && delta < 0) || (!lowerIsBetter && delta > 0)) "improved" else "changed"
    val sign = if (delta >= 0) "+" else ""
    return "Compared with previous visit: $direction ($sign${"%.2f".format(delta)}$unit)"
}

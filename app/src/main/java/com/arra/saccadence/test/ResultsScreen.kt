package com.arra.saccadence.test

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.arra.saccadence.notes.ClinicNote
import com.arra.saccadence.trial.BlandAltmanResult
import com.arra.saccadence.trial.TrialResult
import com.arra.saccadence.ui.components.Hairline
import com.arra.saccadence.ui.components.MonoEyebrow
import com.arra.saccadence.ui.components.PrimaryButton
import com.arra.saccadence.ui.components.ScreenScaffold
import com.arra.saccadence.ui.components.ScreenTitle
import com.arra.saccadence.ui.components.SecondaryButton
import com.arra.saccadence.ui.components.StatusBlock
import com.arra.saccadence.ui.components.StatusTone
import com.arra.saccadence.ui.theme.SaccadenceColors
import com.arra.saccadence.ui.theme.SaccadenceType
import com.arra.saccadence.ui.theme.Spacing

/**
 * Reports two numbers and how they moved since the last visit — per the
 * deck's scope page, this is an instrument's readout, not a diagnosis, and
 * every number here traces to a value in [result]: nothing is invented for
 * display.
 *
 * The handoff's contribution is the hierarchy: the two headline metrics get a
 * 44 px mono numeral so they read as instrument output, and everything that
 * qualifies them — validity counts, the error budget, repeatability — sits in
 * mono audit rows beneath, legible but never competing. The full seven-line
 * error budget stays; the handoff's shorter three-component version is its own
 * placeholder, and these lines are real computed values.
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
    ScreenScaffold(
        bodyArrangement = Arrangement.spacedBy(Spacing.xlPlus),
        footer = {
            PrimaryButton(
                label = "Repeat trial",
                onClick = onRunAnother,
                modifier = Modifier.fillMaxWidth(),
            )
            SecondaryButton(
                label = "Done",
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        ScreenTitle(title = "Result")
        QualityBadge(result.qualityStatus, result.qualityReasons)

        Hairline()

        MetricRow(
            label = "SACCADE ONSET LATENCY",
            value = result.medianLatencyMs?.let { "%.0f".format(it) } ?: "—",
            unit = result.medianLatencyMs?.let { "ms" },
            fallbackNote = if (result.medianLatencyMs == null) "no valid steps" else null,
            comparison = comparisonText(result.medianLatencyMs, previous?.medianLatencyMs, unit = "ms", lowerIsBetter = true),
            improved = isImproved(result.medianLatencyMs, previous?.medianLatencyMs, lowerIsBetter = true),
        )

        Hairline()

        MetricRow(
            label = "SMOOTH-PURSUIT GAIN",
            value = result.meanPursuitGain?.let { "%.2f".format(it) } ?: "—",
            unit = result.meanPursuitGain?.let { "ratio" },
            fallbackNote = if (result.meanPursuitGain == null) "no valid sweeps" else null,
            comparison = comparisonText(result.meanPursuitGain, previous?.meanPursuitGain, unit = "", lowerIsBetter = false),
            improved = isImproved(result.meanPursuitGain, previous?.meanPursuitGain, lowerIsBetter = false),
        )

        Hairline()

        // Validity. Mono on both sides: these are counts, and the handoff puts
        // anything measured in mono.
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            MonoEyebrow("VALIDITY")
            AuditLine(
                label = "Steps",
                value = "${result.validSteps.size}/${result.steps.size}",
                valueColor = countColor(result.validSteps.size, result.steps.size),
            )
            AuditLine(
                label = "Sweeps",
                value = "${result.validSweeps.size}/${result.sweeps.size}",
                valueColor = countColor(result.validSweeps.size, result.sweeps.size),
            )
        }

        result.errorBudget?.let { budget ->
            Hairline()
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                MonoEyebrow("ERROR BUDGET")
                AuditLine("Frame period (${"%.1f".format(result.measuredFps ?: 0.0)} fps)", "%.1f ms".format(budget.framePeriodMs))
                AuditLine("Opening calibration jitter", "%.1f ms".format(budget.preCalibrationJitterMs))
                AuditLine("Closing calibration jitter", "%.1f ms".format(budget.postCalibrationJitterMs))
                AuditLine("Measured clock drift across trial", "%.1f ms".format(budget.measuredDriftMs))
                AuditLine("Rolling-shutter residual", "%.1f ms".format(budget.rollingShutterResidualMs))
                AuditLine("Landmark jitter", "%.2f°".format(budget.landmarkSigmaDeg))
                AuditLine("Residual head drift", "%.2f°".format(budget.residualHeadDriftDeg))
                // A gap before the total, so the RSS line reads as a sum of
                // the components above rather than as one more of them.
                Spacer(Modifier.height(Spacing.xxs))
                AuditLine(
                    label = "Total timing budget (RSS)",
                    value = "±%.1f ms".format(budget.totalTimingBudgetMs),
                    emphasize = true,
                )
            }
        }

        if (repeatabilityLatency != null || repeatabilityGain != null) {
            Hairline()
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                MonoEyebrow("REPEATABILITY VS. PREVIOUS TRIAL · BLAND–ALTMAN")
                repeatabilityLatency?.let {
                    AuditLine("Latency bias", "%.1f ms".format(it.biasMs))
                    AuditLine("Latency limits of agreement", "%.1f to %.1f ms".format(it.lowerLimitOfAgreement, it.upperLimitOfAgreement))
                }
                repeatabilityGain?.let {
                    AuditLine("Gain bias", "%.3f".format(it.biasMs))
                    AuditLine("Gain limits of agreement", "%.3f to %.3f".format(it.lowerLimitOfAgreement, it.upperLimitOfAgreement))
                }
            }
        }

        Hairline()

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.smPlus)) {
            // The qualifier names how the note was produced — a reader has to be
            // able to tell a generated draft from a reviewed one.
            Text(
                text = "Clinic note (${if (clinicNote.isDraft) "draft — ${clinicNote.source}" else clinicNote.source})",
                style = SaccadenceType.SectionTitle,
                color = SaccadenceColors.Ink,
            )
            Text(
                text = clinicNote.text,
                style = SaccadenceType.BodySecondary,
                color = SaccadenceColors.InkMuted,
            )
        }

        Text(
            text = "This is a longitudinal measurement instrument, not a diagnostic tool. It does not replace a validated videonystagmography assessment.",
            style = SaccadenceType.Caption,
            color = SaccadenceColors.InkFaintStrong,
        )
    }
}

/**
 * The handoff's quality badge: a tinted block with a dot, the level, and the
 * reasons as a second line. "ok" is the good tone, "flagged" the warning tone,
 * and anything else falls through to warning too — an unrecognised quality
 * level must never render as if it were fine.
 */
@Composable
private fun QualityBadge(status: String, reasons: List<String>) {
    val tone = if (status == "ok") StatusTone.Good else StatusTone.Warning
    StatusBlock(
        tone = tone,
        title = "Quality: $status",
        detail = reasons.takeIf { it.isNotEmpty() }?.joinToString(", "),
    )
}

/**
 * A headline metric: a mono eyebrow label, a 44 px mono numeral with its unit
 * trailing at 16 px, and the comparison against the previous visit beneath.
 */
@Composable
private fun MetricRow(
    label: String,
    value: String,
    unit: String?,
    fallbackNote: String?,
    comparison: String?,
    improved: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.smPlus)) {
        Text(text = label, style = SaccadenceType.MonoMetricLabel, color = SaccadenceColors.InkMutedMono)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = SaccadenceType.Metric,
                color = if (unit == null) SaccadenceColors.InkFaint else SaccadenceColors.Ink,
            )
            if (unit != null) {
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    text = unit,
                    style = SaccadenceType.MetricUnit,
                    color = SaccadenceColors.InkMuted,
                )
            }
            if (fallbackNote != null) {
                Spacer(Modifier.width(Spacing.smPlus))
                Text(
                    text = fallbackNote,
                    style = SaccadenceType.BodySmall,
                    color = SaccadenceColors.WarningValue,
                )
            }
        }
        if (comparison != null) {
            Text(
                text = comparison,
                style = if (improved) {
                    SaccadenceType.BodySmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.W600)
                } else {
                    SaccadenceType.BodySmall
                },
                color = if (improved) SaccadenceColors.SuccessValue else SaccadenceColors.InkMuted,
            )
        }
    }
}

/**
 * One audit row: a sans label on the left, a mono value on the right. The
 * mono/sans split is what makes these scannable — the eye can run down the
 * right-hand column of numbers without reading the labels.
 */
@Composable
private fun AuditLine(
    label: String,
    value: String,
    emphasize: Boolean = false,
    valueColor: androidx.compose.ui.graphics.Color = SaccadenceColors.Ink,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = label,
            style = if (emphasize) SaccadenceType.AuditEmphasis else SaccadenceType.BodySecondary,
            color = if (emphasize) SaccadenceColors.Ink else SaccadenceColors.InkMuted,
        )
        Text(
            text = value,
            style = if (emphasize) SaccadenceType.MonoAuditValue else SaccadenceType.MonoAudit,
            color = valueColor,
        )
    }
}

/** A short count takes the warning colour — the handoff's treatment for 15/20. */
private fun countColor(valid: Int, total: Int): androidx.compose.ui.graphics.Color =
    if (total > 0 && valid < total) SaccadenceColors.WarningValue else SaccadenceColors.Ink

private fun isImproved(current: Double?, previous: Double?, lowerIsBetter: Boolean): Boolean {
    if (current == null || previous == null) return false
    val delta = current - previous
    return (lowerIsBetter && delta < 0) || (!lowerIsBetter && delta > 0)
}

private fun comparisonText(current: Double?, previous: Double?, unit: String, lowerIsBetter: Boolean): String? {
    if (current == null) return null
    if (previous == null) return "No prior measurement."
    val delta = current - previous
    val direction = if ((lowerIsBetter && delta < 0) || (!lowerIsBetter && delta > 0)) "improved" else "changed"
    val sign = if (delta >= 0) "+" else ""
    return "Compared with previous visit: $direction ($sign${"%.2f".format(delta)}$unit)"
}

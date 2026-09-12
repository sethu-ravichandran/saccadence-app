package com.arra.saccadence.intake

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.arra.saccadence.ui.components.LabeledTextField
import com.arra.saccadence.ui.components.MonoEyebrow
import com.arra.saccadence.ui.components.PillChip
import com.arra.saccadence.ui.components.PrimaryButton
import com.arra.saccadence.ui.components.ScreenScaffold
import com.arra.saccadence.ui.components.SecondaryButton
import com.arra.saccadence.ui.theme.Sizes
import com.arra.saccadence.ui.theme.Spacing

/**
 * The handoff's RECENT chips are sourced from the doctor names already on this
 * device rather than from a fixed list — a chip offering a clinician who has
 * never used this phone would be worse than no chip at all. Read once per
 * entry into the screen, so tapping a chip can't reshuffle the row under the
 * finger that is tapping it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DoctorScreen(
    session: PatientSession,
    repository: PatientRepository,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
    val recentDoctors = remember {
        repository.loadAll()
            .sortedByDescending { it.savedAt }
            .map { it.doctorName.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(4)
    }

    ScreenScaffold(
        bodyArrangement = Arrangement.spacedBy(Spacing.xl),
        footer = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Sizes.footerGap),
            ) {
                SecondaryButton(
                    label = "Back",
                    onClick = onBack,
                    modifier = Modifier.width(Sizes.backButtonWidth),
                )
                PrimaryButton(
                    label = "Next",
                    onClick = onNext,
                    enabled = session.isDoctorValid,
                    modifier = Modifier.weight(1f),
                )
            }
        },
    ) {
        StepProgress(step = 2, label = "Doctor")

        LabeledTextField(
            label = "Doctor / clinician name",
            value = session.doctorName,
            onValueChange = { session.doctorName = it },
            modifier = Modifier.fillMaxWidth(),
        )

        if (recentDoctors.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.smPlus)) {
                MonoEyebrow("RECENT")
                // Wraps rather than scrolls: a clinician name can be long, and a
                // chip that runs off the edge of the row is a chip nobody taps.
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    recentDoctors.forEach { name ->
                        PillChip(label = name, onClick = { session.doctorName = name })
                    }
                }
            }
        }
    }
}

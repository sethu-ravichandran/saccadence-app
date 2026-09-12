package com.arra.saccadence.intake

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.arra.saccadence.ui.components.CheckRow
import com.arra.saccadence.ui.components.Hairline
import com.arra.saccadence.ui.components.NotesField
import com.arra.saccadence.ui.components.PrimaryButton
import com.arra.saccadence.ui.components.ScreenScaffold
import com.arra.saccadence.ui.components.SecondaryButton
import com.arra.saccadence.ui.theme.SaccadenceType
import com.arra.saccadence.ui.theme.Sizes
import com.arra.saccadence.ui.theme.Spacing

/**
 * The answer to "an abnormal number has a dozen possible causes": predefined,
 * identical toggles for every patient, so a reading is recorded under known
 * conditions and stays comparable across that patient's future visits.
 *
 * The handoff heads this screen with an H1 ("Anything affecting the eyes
 * today?") and a sub-line. That is new copy rather than a re-skin of existing
 * copy, so it is not added here — the screen keeps opening straight into the
 * step bar and the checklist, as it does today.
 */
@Composable
fun SymptomsScreen(
    session: PatientSession,
    repository: PatientRepository,
    onBack: () -> Unit,
    onNext: () -> Unit,
) {
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
                    modifier = Modifier.width(Sizes.backButtonWidthNarrow),
                )
                PrimaryButton(
                    label = "Continue to consent",
                    onClick = onNext,
                    labelStyle = SaccadenceType.ButtonPrimary.copy(
                        fontSize = SaccadenceType.ButtonSecondary.fontSize,
                    ),
                    modifier = Modifier.weight(1f),
                )
            }
        },
    ) {
        StepProgress(step = 3, label = "Symptom / context")

        // A hairline above the first row as well as under each one, so the
        // checklist reads as one bounded block rather than a run of loose rows.
        Column(modifier = Modifier.fillMaxWidth()) {
            Hairline()
            SymptomFlag.entries.forEach { flag ->
                val checked = session.symptoms[flag] == true
                CheckRow(
                    checked = checked,
                    title = flag.question,
                    onCheckedChange = { session.symptoms[flag] = it },
                )
            }
        }

        NotesField(
            label = "Notes",
            labelQualifier = "optional",
            value = session.notes,
            onValueChange = { session.notes = it },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

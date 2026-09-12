package com.arra.saccadence.intake

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.arra.saccadence.ui.components.DashedRule
import com.arra.saccadence.ui.components.Hairline
import com.arra.saccadence.ui.components.InfoRow
import com.arra.saccadence.ui.components.PrimaryButton
import com.arra.saccadence.ui.components.ScreenScaffold
import com.arra.saccadence.ui.components.ScreenTitle
import com.arra.saccadence.ui.components.SecondaryButton
import com.arra.saccadence.ui.components.SquareCheckbox
import com.arra.saccadence.ui.components.Tag
import com.arra.saccadence.ui.theme.SaccadenceColors
import com.arra.saccadence.ui.theme.SaccadenceType
import com.arra.saccadence.ui.theme.Sizes
import com.arra.saccadence.ui.theme.Spacing

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
 *
 * Every string below is the approved notice and is reproduced verbatim; the
 * handoff's own consent copy is explicitly placeholder and is not used. What
 * the handoff contributes here is the *treatment*: hairline-separated rows, a
 * 22 px square checkbox, and the demo entry point pushed below a dashed rule so
 * it can never be mistaken for a button.
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

    // Unlike the other intake steps, this screen's actions live at the end of
    // the scrolling body rather than in a pinned footer. That is the handoff's
    // own structure for the consent screen — "title block → consent list →
    // flexible spacer → action stack", with no footer block — and it is also
    // what fits here: the stack is four deep, and pinning it inside the
    // stepper's half-height pane would leave almost nothing for the notice
    // that the patient is supposed to actually read.
    ScreenScaffold(bodyArrangement = Arrangement.spacedBy(Spacing.xl)) {
        StepProgress(step = 4, label = "Consent")

        ScreenTitle(
            title = "For the patient",
            subtitle = if (isMinor) {
                "Staff: please read this notice aloud to the patient's parent or guardian before continuing — the patient is a minor, so consent must come from them."
            } else {
                "Staff: please read this notice aloud to the patient (or hand them the device to read) before continuing."
            },
        )

        Column(modifier = Modifier.fillMaxWidth()) {
            Hairline()
            InfoRow("What's captured", "A short video of your eyes during the test, plus the details entered for you on the previous screens.")
            InfoRow("Why", "Only to measure how your eyes move — nothing else is done with it.")
            InfoRow("What's kept", "The measured results and your details — on this device only, never on a clinic network or the cloud.")
            InfoRow("What's never kept", "The video itself. It's processed frame by frame and discarded immediately.")
            InfoRow("What's never sent", "Anything. No upload, no cloud, no account.")
            InfoRow("Retention + erasure", "Kept until you ask for it to be deleted. Any member of staff can erase it for you on this screen, any time.")
            InfoRow(
                "Your rights, under the DPDPA, 2023",
                "You can ask to see, correct, or erase your data at any time, and you can withdraw this consent as easily as you're giving it — doing so stops the test immediately.",
            )
        }

        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { acknowledged = !acknowledged },
        ) {
            SquareCheckbox(checked = acknowledged)
            Spacer(Modifier.width(Spacing.md))
            Text(
                text = "I confirm $consentGiver has read or heard this notice, understands it, and freely agrees to proceed.",
                style = SaccadenceType.BodySecondary,
                color = SaccadenceColors.Ink,
            )
        }

        // The action stack, in the handoff's emphasis order: the decision
        // first, then record management, then the demo entry point.
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
                label = if (isMinor) "Guardian consents" else "Patient consents",
                onClick = { session.consentGivenAt = System.currentTimeMillis(); onConsented() },
                enabled = acknowledged,
                modifier = Modifier.weight(1f),
            )
        }

        SecondaryButton(
            label = "View / erase saved patients",
            onClick = onOpenPatients,
            height = Sizes.buttonHeightCompact,
            labelStyle = SaccadenceType.ButtonSecondarySmall,
            modifier = Modifier.fillMaxWidth(),
        )

        // The demo entry point: a dashed rule, then a tag-plus-label row.
        // Deliberately not a button — a synthetic session must never look like
        // the next step in a real one.
        Column(modifier = Modifier.fillMaxWidth()) {
            DashedRule()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onDemoPatient)
                    .padding(vertical = Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Tag(label = "DEMO")
                Spacer(Modifier.width(Spacing.smPlus))
                Text(
                    text = "Use demo patient (skip to calibration)",
                    style = SaccadenceType.BodySmall,
                    color = SaccadenceColors.InkFaintStrong,
                )
            }
        }
    }
}

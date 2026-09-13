package com.arra.saccadence.intake

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arra.saccadence.test.EyeCaptureArea
import com.arra.saccadence.test.RigStimulusMirror
import com.arra.saccadence.ui.components.DarkWarningStrip
import com.arra.saccadence.ui.components.VideoChip
import com.arra.saccadence.ui.theme.SaccadenceColors
import com.arra.saccadence.ui.theme.Spacing
import com.arra.saccadence.trial.StimulusMirror
import com.arra.saccadence.trial.TrialPhase
import com.arra.saccadence.trial.TrialSessionController

private const val TOTAL_STEPS = 7

/** The user-navigated part of the flow. The remaining steps (5-7: pre-calibration,
 * test, post-calibration) are phase-driven, not stored here — see [phaseStepNumberAndLabel]. */
enum class FormStep { DETAILS, DOCTOR, SYMPTOMS, CONSENT }

/** Ordinal used only to pick a slide direction for the step transition — forward vs back. */
private fun formStepIndex(step: FormStep?): Int = when (step) {
    FormStep.DETAILS -> 0
    FormStep.DOCTOR -> 1
    FormStep.SYMPTOMS -> 2
    FormStep.CONSENT -> 3
    null -> 4
}

private fun phaseStepNumberAndLabel(phase: TrialPhase): Pair<Int, String> = when (phase) {
    // AwaitingPostCalibration sits AFTER the blocks, so it must not fall through
    // to the else branch below — that labelled the closing step "Pre-test
    // calibration" on screen.
    is TrialPhase.AwaitingPostCalibration, is TrialPhase.PostCalibration -> 7 to "Post-test calibration"
    is TrialPhase.Fixation, is TrialPhase.Saccade, is TrialPhase.Pursuit -> 6 to "Test"
    // Connecting/WaitingForRig/SetupCalibration/Ready/PreCalibration all live under one
    // continuously-mounted camera session — see EyeCaptureArea's own doc comment for why.
    else -> 5 to "Pre-test calibration"
}

/**
 * One screen for the whole clinician-facing flow: top half always mirrors the
 * rig's live state (idle while pairing, the stimulus once a trial starts), so
 * the operator never has to switch screens to see it. Bottom half steps
 * through patient intake and then, once consent is given, the trial itself.
 *
 * Rig pairing has already started by the time this is shown (see
 * MainActivity) — that's what makes the top half live from step 1 rather
 * than only once the trial reaches calibration.
 */
@Composable
fun TrialStepperScreen(
    session: PatientSession,
    repository: PatientRepository,
    controller: TrialSessionController,
    phase: TrialPhase,
    stimulus: StimulusMirror = StimulusMirror.Idle,
    formStep: FormStep?,
    onFormStepChange: (FormStep?) -> Unit,
    connectionWarning: String? = null,
) {
    var showPatientList by remember { mutableStateOf(false) }

    if (showPatientList) {
        PatientListScreen(
            repository = repository,
            onBack = { showPatientList = false },
            onLoadPatient = { record ->
                session.resetTo(record)
                onFormStepChange(FormStep.DETAILS)
                showPatientList = false
            },
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize().background(SaccadenceColors.DarkScreen)) {
        if (connectionWarning != null) {
            // Sits directly above the dark rig panel, so it takes the dark
            // warning treatment rather than Material's error container — a
            // light strip here would cut the two dark panes in half.
            DarkWarningStrip(text = connectionWarning)
        }
        RigStimulusMirror(phase = phase, stimulus = stimulus, modifier = Modifier.fillMaxWidth().weight(1f))

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            AnimatedContent(
                targetState = formStep,
                transitionSpec = {
                    val forward = formStepIndex(targetState) >= formStepIndex(initialState)
                    val enterShift = if (forward) { w: Int -> w / 6 } else { w: Int -> -w / 6 }
                    val exitShift = if (forward) { w: Int -> -w / 6 } else { w: Int -> w / 6 }
                    (slideInHorizontally(tween(220), enterShift) + fadeIn(tween(220)))
                        .togetherWith(slideOutHorizontally(tween(180), exitShift) + fadeOut(tween(150)))
                },
                label = "form-step",
                modifier = Modifier.fillMaxSize(),
            ) { step ->
                when (step) {
                    FormStep.DETAILS -> PatientDetailsScreen(
                        session = session,
                        repository = repository,
                        onBack = {},
                        onNext = { onFormStepChange(FormStep.DOCTOR) },
                    )
                    FormStep.DOCTOR -> DoctorScreen(
                        session = session,
                        repository = repository,
                        onBack = { onFormStepChange(FormStep.DETAILS) },
                        onNext = { onFormStepChange(FormStep.SYMPTOMS) },
                    )
                    FormStep.SYMPTOMS -> SymptomsScreen(
                        session = session,
                        repository = repository,
                        onBack = { onFormStepChange(FormStep.DOCTOR) },
                        onNext = { onFormStepChange(FormStep.CONSENT) },
                    )
                    FormStep.CONSENT -> ConsentScreen(
                        session = session,
                        onBack = { onFormStepChange(FormStep.SYMPTOMS) },
                        onConsented = {
                            repository.save(session.toRecord())
                            onFormStepChange(null)
                        },
                        onDemoPatient = {
                            session.fillDemoData()
                            repository.save(session.toRecord())
                            onFormStepChange(null)
                        },
                        onOpenPatients = { showPatientList = true },
                    )
                    null -> {
                        EyeCaptureArea(controller = controller, phase = phase, modifier = Modifier.fillMaxSize())
                        val (stepNumber, label) = phaseStepNumberAndLabel(phase)
                        // A scrim chip, not a bare label: it floats over the live
                        // camera feed, where flat text on any single colour would
                        // lose contrast as the scene changes.
                        VideoChip(
                            label = "STEP $stepNumber OF $TOTAL_STEPS — ${label.uppercase()}",
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = Spacing.md),
                        )
                    }
                }
            }
        }
    }
}

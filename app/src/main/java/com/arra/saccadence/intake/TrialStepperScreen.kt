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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.arra.saccadence.test.EyeCaptureArea
import com.arra.saccadence.test.RigStimulusMirror
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
    is TrialPhase.PostCalibration -> 7 to "Post-test calibration"
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

    Column(modifier = Modifier.fillMaxSize()) {
        if (connectionWarning != null) {
            Text(
                text = connectionWarning,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
        RigStimulusMirror(phase = phase, modifier = Modifier.fillMaxWidth().weight(1f))

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
                        Text(
                            text = "Step $stepNumber of $TOTAL_STEPS — $label",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 12.dp)
                                .background(Color(0x99000000), RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

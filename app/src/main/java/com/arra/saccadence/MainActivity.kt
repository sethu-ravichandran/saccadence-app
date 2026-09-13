package com.arra.saccadence

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import com.arra.saccadence.notes.DeterministicClinicNoteGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.arra.saccadence.intake.FormStep
import com.arra.saccadence.intake.PatientRepository
import com.arra.saccadence.intake.PatientSession
import com.arra.saccadence.intake.TrialStepperScreen
import com.arra.saccadence.notes.ClinicNote
import com.arra.saccadence.notes.GemmaClinicNoteGenerator
import com.arra.saccadence.notes.NoteInput
import com.arra.saccadence.pairing.PairingScreen
import com.arra.saccadence.test.ResultsScreen
import com.arra.saccadence.trial.Repeatability
import com.arra.saccadence.trial.StimulusMirror
import com.arra.saccadence.trial.TrialPhase
import com.arra.saccadence.trial.TrialRepository
import com.arra.saccadence.trial.TrialSessionController
import com.arra.saccadence.ui.theme.SaccadenceColors
import com.arra.saccadence.ui.theme.SaccadenceTheme
import com.arra.saccadence.voice.SpokenInstructions

private enum class Screen { SPLASH, PAIRING, STEPPER, RESULTS }

class MainActivity : ComponentActivity() {

    private val cameraGranted = mutableStateOf(false)

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> cameraGranted.value = granted }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        cameraGranted.value = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (!cameraGranted.value) {
            requestPermission.launch(Manifest.permission.CAMERA)
        }

        setContent {
            SaccadenceTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(SaccadenceColors.Surface)
                        .safeDrawingPadding(),
                ) {
                    val context = LocalContext.current
                    val session = remember { PatientSession() }
                    val repository = remember { PatientRepository(context) }
                    val trialRepository = remember { TrialRepository.forContext(context) }
                    val noteGenerator = remember {
                        // Pushed separately (see GemmaClinicNoteGenerator's doc): ~290MB,
                        // far too large to vendor in the APK. It has to live in the app's
                        // OWN files dir — /data/local/tmp is not traversable by the app
                        // UID, so a model staged there reads back as absent. Absent or
                        // unreadable -> the deterministic note is the whole story, which
                        // is a correct result, not a degraded one.
                        val gemmaFile = java.io.File(context.filesDir, "gemma.task")
                        GemmaClinicNoteGenerator(
                            modelPath = gemmaFile.takeIf { it.canRead() }?.absolutePath,
                            createInference = { path ->
                                com.google.mediapipe.tasks.genai.llminference.LlmInference.createFromOptions(
                                    context,
                                    com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions.builder()
                                        .setModelPath(path)
                                        .setMaxTokens(128)
                                        .build()
                                )
                            },
                        )
                    }

                    var screen by remember { mutableStateOf(Screen.SPLASH) }
                    var phase by remember { mutableStateOf<TrialPhase>(TrialPhase.Connecting) }
                    var pairingStatusText by remember { mutableStateOf("") }
                    var connectionWarning by remember { mutableStateOf<String?>(null) }
                    var voice by remember { mutableStateOf<SpokenInstructions?>(null) }
                    // Presentation-only mirror of the rig's target, for the stimulus
                    // panel — see StimulusMirror; nothing measured depends on it.
                    var stimulus by remember { mutableStateOf<StimulusMirror>(StimulusMirror.Idle) }
                    var controller by remember { mutableStateOf<TrialSessionController?>(null) }
                    // Hoisted above the stepper (not local to it) so it survives the
                    // Stepper<->Results round trip on "run another" without forcing the
                    // clinician to redo patient details/doctor/consent for the same patient.
                    var formStep by remember { mutableStateOf<FormStep?>(FormStep.DETAILS) }

                    // Once a trial completes we jump to Results; every other phase is
                    // rendered inside Screen.STEPPER itself (its top half mirrors the rig
                    // live, its bottom half is phase-driven once intake is done — see
                    // TrialStepperScreen), so there's nothing else to react to here.
                    LaunchedEffect(phase) {
                        if (phase is TrialPhase.Complete) screen = Screen.RESULTS
                    }

                    when (screen) {
                        Screen.SPLASH -> SplashScreen(
                            onFinished = { screen = Screen.PAIRING },
                        )

                        Screen.PAIRING -> PairingScreen(
                            statusText = pairingStatusText,
                            onConnect = { settings ->
                                val spoken = SpokenInstructions(context, settings.language)
                                voice = spoken
                                val newController = TrialSessionController(
                                    patientId = session.id,
                                    trialRepository = trialRepository,
                                    voice = spoken,
                                    onPhaseChange = { newPhase ->
                                        phase = newPhase
                                        pairingStatusText = statusTextFor(newPhase)
                                    },
                                    onConnectionWarning = { warning -> connectionWarning = warning },
                                    onStimulus = { mirrored -> stimulus = mirrored },
                                )
                                controller = newController
                                connectionWarning = null
                                newController.connect(settings.host, settings.port, settings.sessionCode)
                                pairingStatusText = "Connecting to ${settings.host}:${settings.port}…"
                                // Move into the stepper immediately rather than waiting for
                                // the rig to finish joining — patient intake (details/doctor/
                                // symptoms/consent) can happen while that connection settles,
                                // with the top-half rig panel showing live connection status.
                                screen = Screen.STEPPER
                            },
                        )

                        Screen.STEPPER -> {
                            val activeController = controller
                            if (activeController != null) {
                                TrialStepperScreen(
                                    session = session,
                                    repository = repository,
                                    controller = activeController,
                                    phase = phase,
                                    stimulus = stimulus,
                                    formStep = formStep,
                                    onFormStepChange = { formStep = it },
                                    connectionWarning = connectionWarning,
                                )
                            }
                        }

                        Screen.RESULTS -> {
                            val result = (phase as? TrialPhase.Complete)?.result
                            val activeController = controller
                            if (result != null && activeController != null) {
                                val previous = trialRepository.previousFor(session.id, result)
                                val noteInput = remember(result.trialId) {
                                    NoteInput(
                                        patientName = session.name.ifBlank { "Patient" },
                                        medianLatencyMs = result.medianLatencyMs,
                                        previousMedianLatencyMs = previous?.medianLatencyMs,
                                        meanPursuitGain = result.meanPursuitGain,
                                        previousMeanPursuitGain = previous?.meanPursuitGain,
                                        qualityStatus = result.qualityStatus,
                                        qualityReasons = result.qualityReasons,
                                        openingJitterMs = result.errorBudget?.preCalibrationJitterMs,
                                        closingJitterMs = result.errorBudget?.postCalibrationJitterMs,
                                        jitterDerivedFromGeometry = result.qualityReasons.contains(
                                            "calibration_jitter_derived_from_capture_geometry"
                                        ),
                                        totalTimingBudgetMs = result.errorBudget?.totalTimingBudgetMs,
                                    )
                                }
                                // The deterministic note renders immediately; Gemma's
                                // rewrite replaces it only once it comes back, off the
                                // main thread. Model load and inference both take
                                // seconds, so doing this inline would ANR the results
                                // screen at the worst possible moment.
                                val deterministicNote = remember(result.trialId) {
                                    DeterministicClinicNoteGenerator().generate(noteInput)
                                }
                                var note by remember(result.trialId) { mutableStateOf(deterministicNote) }
                                LaunchedEffect(result.trialId) {
                                    val rewritten = withContext(Dispatchers.IO) {
                                        runCatching { noteGenerator.generate(noteInput) }.getOrNull()
                                    }
                                    if (rewritten != null) note = rewritten
                                }
                                ResultsScreen(
                                    result = result,
                                    previous = previous,
                                    repeatabilityLatency = previous?.takeIf { it.protocolId == result.protocolId }
                                        ?.let { Repeatability.compute(Repeatability.latencyPairs(result, it)) },
                                    repeatabilityGain = previous?.takeIf { it.protocolId == result.protocolId }
                                        ?.let { Repeatability.compute(Repeatability.gainPairs(result, it)) },
                                    clinicNote = note,
                                    onRunAnother = {
                                        activeController.acknowledgeComplete()
                                        screen = Screen.STEPPER
                                    },
                                    onDone = {
                                        activeController.acknowledgeComplete()
                                        activeController.disconnect()
                                        voice?.shutdown()
                                        session.resetTo()
                                        formStep = FormStep.DETAILS
                                        screen = Screen.PAIRING
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun statusTextFor(phase: TrialPhase): String = when (phase) {
    is TrialPhase.Connecting -> "Connecting…"
    is TrialPhase.WaitingForRig -> "Disconnected — retrying…"
    else -> "Paired ✓"
}

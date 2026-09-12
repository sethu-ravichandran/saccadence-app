package com.arra.saccadence

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.arra.saccadence.calibration.CalibrationScreen
import com.arra.saccadence.intake.IntakeNavHost
import com.arra.saccadence.intake.PatientRepository
import com.arra.saccadence.intake.PatientSession
import com.arra.saccadence.notes.ClinicNote
import com.arra.saccadence.notes.GemmaClinicNoteGenerator
import com.arra.saccadence.notes.NoteInput
import com.arra.saccadence.pairing.PairingScreen
import com.arra.saccadence.test.ResultsScreen
import com.arra.saccadence.test.TestScreen
import com.arra.saccadence.trial.Repeatability
import com.arra.saccadence.trial.TrialPhase
import com.arra.saccadence.trial.TrialRepository
import com.arra.saccadence.trial.TrialSessionController
import com.arra.saccadence.voice.SpokenInstructions

private enum class Screen { INTAKE, PAIRING, CALIBRATION, TEST, RESULTS }

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
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
                    val context = LocalContext.current
                    val session = remember { PatientSession() }
                    val repository = remember { PatientRepository(context) }
                    val trialRepository = remember { TrialRepository.forContext(context) }
                    val noteGenerator = remember { GemmaClinicNoteGenerator(modelPath = null) }

                    var screen by remember { mutableStateOf(Screen.INTAKE) }
                    var phase by remember { mutableStateOf<TrialPhase>(TrialPhase.Connecting) }
                    var pairingStatusText by remember { mutableStateOf("") }
                    var voice by remember { mutableStateOf<SpokenInstructions?>(null) }
                    var controller by remember { mutableStateOf<TrialSessionController?>(null) }

                    // Phase is driven by the rig's own events once paired; screen
                    // navigation reacts to it rather than the other way around —
                    // the clinician's Office Kit input on the rig is what actually
                    // advances a trial, the phone is a follower.
                    LaunchedEffect(phase) {
                        screen = when (phase) {
                            is TrialPhase.Connecting, is TrialPhase.WaitingForRig -> Screen.PAIRING
                            is TrialPhase.SetupCalibration, is TrialPhase.Ready -> Screen.CALIBRATION
                            is TrialPhase.PreCalibration, is TrialPhase.Fixation,
                            is TrialPhase.Saccade, is TrialPhase.Pursuit, is TrialPhase.PostCalibration -> Screen.TEST
                            is TrialPhase.Complete -> Screen.RESULTS
                        }
                    }

                    when (screen) {
                        Screen.INTAKE -> IntakeNavHost(
                            session = session,
                            repository = repository,
                            onReachCalibration = { screen = Screen.PAIRING },
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
                                )
                                controller = newController
                                newController.connect(settings.host, settings.port, settings.sessionCode)
                                pairingStatusText = "Connecting to ${settings.host}:${settings.port}…"
                            },
                        )

                        Screen.CALIBRATION -> {
                            val granted by cameraGranted
                            if (granted) {
                                CalibrationScreen(onStartTest = {})
                            } else {
                                Box(modifier = Modifier.fillMaxSize()) { Text("Camera permission required") }
                            }
                        }

                        Screen.TEST -> {
                            val activeController = controller
                            if (activeController != null) {
                                TestScreen(controller = activeController, phase = phase)
                            }
                        }

                        Screen.RESULTS -> {
                            val result = (phase as? TrialPhase.Complete)?.result
                            val activeController = controller
                            if (result != null && activeController != null) {
                                val previous = trialRepository.previousFor(session.id, result)
                                val note = noteGenerator.generate(
                                    NoteInput(
                                        patientName = session.name.ifBlank { "Patient" },
                                        medianLatencyMs = result.medianLatencyMs,
                                        previousMedianLatencyMs = previous?.medianLatencyMs,
                                        meanPursuitGain = result.meanPursuitGain,
                                        previousMeanPursuitGain = previous?.meanPursuitGain,
                                        qualityStatus = result.qualityStatus,
                                        qualityReasons = result.qualityReasons,
                                    )
                                )
                                ResultsScreen(
                                    result = result,
                                    previous = previous,
                                    repeatabilityLatency = previous?.takeIf { it.protocolId == result.protocolId }
                                        ?.let { Repeatability.compute(Repeatability.latencyPairs(result, it)) },
                                    repeatabilityGain = previous?.takeIf { it.protocolId == result.protocolId }
                                        ?.let { Repeatability.compute(Repeatability.gainPairs(result, it)) },
                                    clinicNote = note,
                                    onRunAnother = { activeController.acknowledgeComplete() },
                                    onDone = {
                                        activeController.acknowledgeComplete()
                                        activeController.disconnect()
                                        voice?.shutdown()
                                        session.resetTo()
                                        screen = Screen.INTAKE
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

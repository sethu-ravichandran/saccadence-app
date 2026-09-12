package com.arra.saccadence.test

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.arra.saccadence.calibration.MarkerSample
import com.arra.saccadence.landmark.EyeLandmarkTracker
import com.arra.saccadence.marker.ImageProxyFrameSampler
import com.arra.saccadence.marker.MarkerDecoder
import com.arra.saccadence.trial.TrialPhase
import com.arra.saccadence.trial.TrialSessionController
import java.util.concurrent.Executors

/**
 * The actual-test layout: the rig's stimulus phase mirrored on top (so the
 * clinician doesn't have to look away from the phone to see where the
 * trial is), the patient's eyes on the bottom via the **back** camera —
 * only the back camera hits 120fps on this hardware, which is also what
 * points the phone's own screen at the clinician rather than the patient.
 *
 * One continuous camera session spans the whole trial. Which decoder a
 * frame goes to is driven by [controller]'s current phase: the marker
 * decoder during the pre/post calibration brackets (guard needs to be
 * in-frame only there), the landmark tracker during
 * fixation/saccade/pursuit (the marker is not required in the patient
 * trial frame — see the deck correction on this exact point).
 */
@Composable
fun TestScreen(
    controller: TrialSessionController,
    phase: TrialPhase,
) {
    // The parent screen switches away once phase becomes TrialPhase.Complete
    // (the controller has already persisted the result by then) — this
    // composable only renders the live in-progress states.
    Column(modifier = Modifier.fillMaxSize()) {
        RigStimulusMirror(phase = phase, modifier = Modifier.fillMaxWidth().weight(1f))
        EyeCaptureArea(controller = controller, phase = phase, modifier = Modifier.fillMaxWidth().weight(1f))
    }
}

@Composable
private fun RigStimulusMirror(phase: TrialPhase, modifier: Modifier = Modifier) {
    val (title, subtitle) = phaseCopy(phase)
    Box(modifier = modifier.background(Color(0xFF141821)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("RIG STIMULUS", style = MaterialTheme.typography.labelMedium, color = Color(0xFF8A93A6))
            androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
            androidx.compose.foundation.layout.Spacer(Modifier.size(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFF8A93A6))
        }
    }
}

private fun phaseCopy(phase: TrialPhase): Pair<String, String> = when (phase) {
    is TrialPhase.PreCalibration -> "Opening calibration" to "Point the camera at the rig's marker corner."
    is TrialPhase.Fixation -> "Fixation" to "Patient is looking at the center dot."
    is TrialPhase.Saccade -> "Saccade block" to "Dot is stepping — camera should be on the patient's eyes."
    is TrialPhase.Pursuit -> "Pursuit block" to "Dot is sweeping — camera should be on the patient's eyes."
    is TrialPhase.PostCalibration -> "Closing calibration" to "Point the camera at the rig's marker corner again."
    is TrialPhase.Complete -> "Trial complete" to "Preparing results…"
    else -> "Waiting" to "Waiting for the rig to start a trial."
}

@Composable
private fun EyeCaptureArea(controller: TrialSessionController, phase: TrialPhase, modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val markerDecoder = remember { MarkerDecoder() }
    val landmarkTracker = remember { EyeLandmarkTracker(context) }
    // The camera analyzer callback below is captured once, when AndroidView's
    // factory runs — it must read the *current* phase on every frame via this
    // holder, not close over the composable parameter, or it would freeze on
    // whatever phase was active the moment the camera session was first bound.
    val currentPhase = rememberUpdatedState(phase)
    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
            landmarkTracker.close()
        }
    }

    Box(modifier = modifier.background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                val mainExecutor = ContextCompat.getMainExecutor(ctx)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(executor) { image ->
                                val phoneTimeMs = System.nanoTime() / 1_000_000.0
                                when (currentPhase.value) {
                                    is TrialPhase.PreCalibration, is TrialPhase.PostCalibration -> {
                                        val result = markerDecoder.decode(ImageProxyFrameSampler(image))
                                        if (result != null) {
                                            controller.onMarkerFrame(MarkerSample(phoneTimeMs, result.active))
                                        }
                                        image.close()
                                    }
                                    is TrialPhase.Fixation, is TrialPhase.Saccade, is TrialPhase.Pursuit -> {
                                        landmarkTracker.analyze(image, phoneTimeMs.toLong())
                                        landmarkTracker.latestFrame(phoneTimeMs)?.let { controller.onLandmarkFrame(it) }
                                        image.close()
                                    }
                                    else -> image.close()
                                }
                            }
                        }

                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis
                    )
                }, mainExecutor)

                previewView
            }
        )

        EyeAlignmentGuides(modifier = Modifier.fillMaxSize())
        RecordingBadge(modifier = Modifier.align(Alignment.TopStart).padding(12.dp))

        Text(
            text = qualityText(phase),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
                .background(Color(0x99000000), RoundedCornerShape(6.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

private fun qualityText(phase: TrialPhase): String = when (phase) {
    is TrialPhase.PreCalibration, is TrialPhase.PostCalibration -> "Decoding rig marker…"
    is TrialPhase.Fixation, is TrialPhase.Saccade, is TrialPhase.Pursuit -> "Tracking eyes…"
    else -> "Idle"
}

@Composable
private fun EyeAlignmentGuides(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val ovalWidth = size.width * 0.16f
        val ovalHeight = ovalWidth * 1.4f
        val centerY = size.height * 0.45f
        val leftCenterX = size.width * 0.35f
        val rightCenterX = size.width * 0.65f

        val stroke = Stroke(width = 3.dp.toPx())
        val guideColor = Color(0xCCFFFFFF)

        for (cx in listOf(leftCenterX, rightCenterX)) {
            drawOval(
                color = guideColor,
                topLeft = Offset(cx - ovalWidth / 2, centerY - ovalHeight / 2),
                size = androidx.compose.ui.geometry.Size(ovalWidth, ovalHeight),
                style = stroke,
            )
        }
    }
}

@Composable
private fun RecordingBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(Color(0x99000000), RoundedCornerShape(20.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(10.dp).background(Color(0xFFE5484D), CircleShape))
            androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
            Text("REC", color = Color.White, style = MaterialTheme.typography.labelMedium)
        }
    }
}

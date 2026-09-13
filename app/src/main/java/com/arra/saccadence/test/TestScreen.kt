package com.arra.saccadence.test

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.arra.saccadence.calibration.CalibrationState
import com.arra.saccadence.calibration.GuardTracker
import com.arra.saccadence.calibration.LOCK_JITTER_THRESHOLD_PX
import com.arra.saccadence.calibration.MarkerSample
import com.arra.saccadence.calibration.PreviewStats
import com.arra.saccadence.calibration.PreviewStatsTracker
import com.arra.saccadence.landmark.EyeLandmarkTracker
import com.arra.saccadence.landmark.FaceFramingQuality
import com.arra.saccadence.landmark.eyeWidthOf
import com.arra.saccadence.marker.ImageProxyFrameSampler
import com.arra.saccadence.marker.MarkerDecoder
import com.arra.saccadence.trial.StimulusMirror
import com.arra.saccadence.trial.TrialPhase
import com.arra.saccadence.trial.TrialSessionController
import androidx.compose.foundation.layout.Row
import com.arra.saccadence.ui.components.CaptureQuality
import androidx.compose.foundation.layout.Row
import com.arra.saccadence.ui.components.CaptureQualityLine
import com.arra.saccadence.ui.components.DarkInnerSurface
import com.arra.saccadence.ui.components.MirrorCaption
import com.arra.saccadence.ui.components.PrimaryButton
import com.arra.saccadence.ui.components.RecordingChip
import com.arra.saccadence.ui.components.StatCard
import com.arra.saccadence.ui.components.StimulusInstruction
import com.arra.saccadence.ui.components.darkStatCardColors
import androidx.camera.core.Camera
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import com.arra.saccadence.ui.theme.Radius
import com.arra.saccadence.ui.theme.SaccadenceColors
import com.arra.saccadence.ui.theme.SaccadenceType
import com.arra.saccadence.ui.theme.Sizes
import com.arra.saccadence.ui.theme.Spacing
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
 *
 * Visually this is the handoff's dark half. The earlier fixed hexes
 * (`#8A93A6`, plain white, `#141821`) are gone: the handoff is explicit that
 * the dark surfaces use the same accent and neutral ramps as the light screens
 * "so it reads as one product in a different mode", which is why everything
 * here draws from the `Dark*` tokens.
 */
@Composable
fun TestScreen(
    controller: TrialSessionController,
    phase: TrialPhase,
    stimulus: StimulusMirror = StimulusMirror.Idle,
) {
    // The parent screen switches away once phase becomes TrialPhase.Complete
    // (the controller has already persisted the result by then) — this
    // composable only renders the live in-progress states.
    Column(modifier = Modifier.fillMaxSize().background(SaccadenceColors.DarkScreen)) {
        RigStimulusMirror(phase = phase, stimulus = stimulus, modifier = Modifier.fillMaxWidth().weight(1f))
        EyeCaptureArea(controller = controller, phase = phase, modifier = Modifier.fillMaxWidth().weight(1f))
    }
}

/**
 * The rig's stimulus, mirrored. During the saccade and pursuit blocks this
 * draws the moving target itself; in every other phase there is no target to
 * draw, so it stays as it was — the instruction block alone, centred.
 */
@Composable
internal fun RigStimulusMirror(
    phase: TrialPhase,
    stimulus: StimulusMirror = StimulusMirror.Idle,
    modifier: Modifier = Modifier,
) {
    val (title, subtitle) = phaseCopy(phase)

    Box(modifier = modifier.background(SaccadenceColors.DarkCard)) {
        if (stimulus is StimulusMirror.Idle) {
            StimulusInstruction(
                eyebrow = "RIG STIMULUS",
                title = title,
                subtitle = subtitle,
                modifier = Modifier.align(Alignment.Center).padding(Spacing.xl),
            )
            return@Box
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.mdPlus),
        ) {
            StimulusInstruction(
                eyebrow = "RIG STIMULUS",
                title = title,
                subtitle = subtitle,
            )
            DarkInnerSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .heightIn(min = MIRROR_MIN_HEIGHT),
            ) {
                when (stimulus) {
                    StimulusMirror.FixationTarget -> FixationTargetCanvas()
                    is StimulusMirror.SaccadeTarget -> SaccadeTargetCanvas(stimulus)
                    is StimulusMirror.PursuitSweep -> PursuitSweepCanvas(stimulus)
                    StimulusMirror.Idle -> Unit
                }
            }
            MirrorCaption(mirrorCaption(stimulus))
        }
    }
}

/** The handoff's minimum for the mirror area, so it survives a short pane. */
private val MIRROR_MIN_HEIGHT = 90.dp

private fun mirrorCaption(stimulus: StimulusMirror): String = when (stimulus) {
    StimulusMirror.FixationTarget -> "mirrored from rig · fixation centre"
    is StimulusMirror.SaccadeTarget ->
        stimulus.stepAmplitudeDeg
            ?.let { "mirrored from rig · step %.0f°".format(kotlin.math.abs(it)) }
            ?: "mirrored from rig · centre target"
    is StimulusMirror.PursuitSweep ->
        "mirrored from rig · sweep %.2f Hz".format(stimulus.frequencyHz)
    StimulusMirror.Idle -> "mirrored from rig"
}

/**
 * The fixation block: one steady dot, dead centre, with no pulse and no glow
 * animation. The stillness is the point — the patient is holding their gaze,
 * and anything moving here would be a competing stimulus.
 */
@Composable
private fun FixationTargetCanvas() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val centre = Offset(size.width / 2, size.height / 2)
        drawCircle(
            color = SaccadenceColors.DarkAccent.copy(alpha = 0.4f),
            radius = TARGET_GLOW_DIAMETER.toPx() / 2,
            center = centre,
        )
        drawCircle(
            color = SaccadenceColors.DarkAccent,
            radius = TARGET_DIAMETER.toPx() / 2,
            center = centre,
        )
    }
}

/**
 * The saccade block: a guide line, a dim dot at the position the target jumped
 * *from*, and the live target pulsing at its new position. The previous dot is
 * what makes the step legible as a step rather than as a dot that teleported.
 */
@Composable
private fun SaccadeTargetCanvas(stimulus: StimulusMirror.SaccadeTarget) {
    val transition = rememberInfiniteTransition(label = "saccade-target")
    val glow by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 450, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "saccade-glow",
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val midY = size.height / 2

        drawLine(
            color = SaccadenceColors.DarkBorder,
            start = Offset(0f, midY),
            end = Offset(size.width, midY),
            strokeWidth = Sizes.borderWidth.toPx(),
        )

        stimulus.previousXFraction?.let { previous ->
            drawCircle(
                color = SaccadenceColors.DarkPrevTarget,
                radius = PREVIOUS_TARGET_DIAMETER.toPx() / 2,
                center = Offset(size.width * previous, midY),
            )
        }

        val centre = Offset(size.width * stimulus.targetXFraction, midY)
        drawCircle(
            color = SaccadenceColors.DarkAccent.copy(alpha = 0.55f * glow),
            radius = TARGET_GLOW_DIAMETER.toPx() / 2,
            center = centre,
        )
        drawCircle(
            color = SaccadenceColors.DarkAccent,
            radius = TARGET_DIAMETER.toPx() / 2,
            center = centre,
        )
    }
}

/**
 * The pursuit block: the target glides along a rail instead of pulsing.
 *
 * Position is computed from the wall clock against the instant the rig started
 * the sweep, **not** from an animation that starts when the event arrives. That
 * distinction is the whole point:
 *
 * - An arrival-started animation is permanently late by the transport latency,
 *   and any mismatch between the rig's real sweep rate and our `tween`
 *   duration accumulates as visible phase drift over a long sweep.
 * - A clock-anchored triangle wave is correct at every frame. It is late only
 *   by render latency, it cannot drift, and it self-corrects after a dropped
 *   frame or a recomposition instead of carrying the error forward.
 *
 * When [StimulusMirror.PursuitSweep.startPhoneTimeMs] is present — i.e. the
 * opening calibration produced a usable clock offset — the anchor is the rig's
 * own start instant translated onto the phone's clock, so the drawn dot sits
 * where the real dot is *now*. Without it, the anchor falls back to arrival,
 * which is the old behaviour minus the drift.
 */
@Composable
private fun PursuitSweepCanvas(stimulus: StimulusMirror.PursuitSweep) {
    val halfPeriodMs = (stimulus.traverseSeconds * 1000).coerceIn(250.0, 20_000.0)
    val anchorMs = remember(stimulus) {
        stimulus.startPhoneTimeMs ?: phoneClockMs()
    }

    // Held as state read *inside* the draw lambda, so each frame invalidates
    // the draw only — no recomposition of the tree on every vsync.
    val nowMs = remember(stimulus) { mutableDoubleStateOf(anchorMs) }
    LaunchedEffect(stimulus) {
        while (true) {
            withFrameMillis { nowMs.doubleValue = phoneClockMs() }
        }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val midY = size.height / 2
        val inset = size.width * RAIL_INSET_FRACTION
        val railStart = inset
        val railEnd = size.width - inset

        drawLine(
            color = SaccadenceColors.DarkRail,
            start = Offset(railStart, midY),
            end = Offset(railEnd, midY),
            strokeWidth = RAIL_THICKNESS.toPx(),
            cap = StrokeCap.Round,
        )

        // Triangle wave: phase runs 0→2 per full there-and-back cycle, folded
        // back on itself so 1→2 retraces 1→0. The double modulo keeps it
        // correct even if the anchor is briefly in the future.
        val cycles = (nowMs.doubleValue - anchorMs) / halfPeriodMs
        val phase = ((cycles % 2.0) + 2.0) % 2.0
        val travel = (if (phase <= 1.0) phase else 2.0 - phase).toFloat()

        // direction < 0 starts the dot at the right-hand end, matching the
        // rig's own sign convention for a sweep's leading edge.
        val progress = if (stimulus.direction < 0) 1f - travel else travel
        drawCircle(
            color = SaccadenceColors.DarkAccent,
            radius = TARGET_DIAMETER.toPx() / 2,
            center = Offset(railStart + (railEnd - railStart) * progress, midY),
        )
    }
}

/**
 * The phone timebase the clock offset was measured against — the same one
 * `MarkerSample.phoneTimeMs` is stamped with in [EyeCaptureArea]. Must not be
 * `System.currentTimeMillis()`, which carries wall-clock skew and jumps when
 * the device syncs time.
 */
private fun phoneClockMs(): Double = System.nanoTime() / 1_000_000.0

private val TARGET_DIAMETER = 18.dp
private val TARGET_GLOW_DIAMETER = 22.dp
private val PREVIOUS_TARGET_DIAMETER = 14.dp
private val RAIL_THICKNESS = 2.dp

/** The handoff insets the pursuit rail 8 % from each edge of the mirror. */
private const val RAIL_INSET_FRACTION = 0.08f

private fun phaseCopy(phase: TrialPhase): Pair<String, String> = when (phase) {
    is TrialPhase.SetupCalibration -> "Setup calibration" to "Point the camera at the rig's marker corner to confirm framing."
    is TrialPhase.Ready -> "Ready" to "Confirm the marker lock below, then start the trial from the rig."
    is TrialPhase.PreCalibration -> "Opening calibration" to "Point the camera at the rig's marker corner."
    is TrialPhase.Fixation -> "Fixation" to "Patient is looking at the center dot."
    is TrialPhase.Saccade -> "Saccade block" to "Dot is stepping — camera should be on the patient's eyes."
    is TrialPhase.Pursuit -> "Pursuit block" to "Dot is sweeping — camera should be on the patient's eyes."
    is TrialPhase.PostCalibration -> "Closing calibration" to "Point the camera at the rig's marker corner again."
    is TrialPhase.Complete -> "Trial complete" to "Preparing results…"
    else -> "Waiting" to "Waiting for the rig to start a trial."
}

@Composable
internal fun EyeCaptureArea(controller: TrialSessionController, phase: TrialPhase, modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    // Full-frame search, not the default bottom-right ROI: the analyzer
    // delivers the UNROTATED sensor buffer (rotationDegrees=90 in portrait),
    // so "bottom-right of the buffer" is not the bottom-right of what the
    // operator sees in the preview — a corner-scoped ROI made real-hardware
    // decode fail on 540/540 frames while the marker was perfectly framed.
    val markerDecoder = remember { MarkerDecoder(roiWidthFraction = 1f, roiHeightFraction = 1f) }
    val landmarkTracker = remember { EyeLandmarkTracker(context) }
    // The camera analyzer callback below is captured once, when AndroidView's
    // factory runs — it must read the *current* phase on every frame via this
    // holder, not close over the composable parameter, or it would freeze on
    // whatever phase was active the moment the camera session was first bound.
    val currentPhase = rememberUpdatedState(phase)
    // Ready-phase marker-lock check — reuses CalibrationScreen's tracker so
    // the phone can confirm lock and send "Start test" without ever handing
    // the camera off to a different AndroidView. See the class doc on
    // GuardTracker for why this matters: it's what keeps this same camera
    // session alive from Ready straight into PreCalibration.
    val guardTracker = remember { GuardTracker() }
    // Live FPS / no-decode accounting for the marker phases, so the operator
    // can see whether the preview is healthy before committing to a trial.
    val statsTracker = remember { PreviewStatsTracker() }
    var readyLock by remember { mutableStateOf(CalibrationState()) }
    var previewStats by remember { mutableStateOf(PreviewStats()) }
    var readySent by remember { mutableStateOf(false) }
    // Live face-framing feedback during the actual eye-tracking blocks — a
    // too-far/too-small face silently produces unreliable angle math
    // downstream (see FaceFramingQuality's doc), so the operator needs to see
    // this during capture, not discover it after the trial completes.
    var eyeWidth by remember { mutableStateOf<Float?>(null) }
    // No wider-than-1x option was found on this hardware's back camera during
    // on-device testing, so this exists for fine control near the default,
    // not to reach an ultra-wide FOV that may not be there.
    var camera by remember { mutableStateOf<Camera?>(null) }
    var zoomRatio by remember { mutableStateOf(1f) }
    var minZoomRatio by remember { mutableStateOf(1f) }
    var maxZoomRatio by remember { mutableStateOf(1f) }
    DisposableEffect(Unit) {
        onDispose {
            executor.shutdown()
            landmarkTracker.close()
        }
    }

    Box(modifier = modifier.background(SaccadenceColors.DarkCardInner)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                // COMPATIBLE (TextureView-backed), not the SurfaceView-backed default — see
                // QrScannerFeed's identical fix for why: on this OEM skin a SurfaceView here
                // renders black and can bleed over neighboring Compose content (the recording
                // badge, status text) until the camera session settles.
                val previewView = PreviewView(ctx).apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                }
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
                                    // SetupCalibration is the rig's one-time, operator-paced
                                    // session-start marker check (see marker-protocol.md) — the
                                    // clinician ends it on the laptop whenever they're satisfied,
                                    // so the phone needs to show live lock feedback throughout
                                    // it, not just once the rig has already moved on to Ready.
                                    is TrialPhase.SetupCalibration, is TrialPhase.Ready -> {
                                        val result = markerDecoder.decode(ImageProxyFrameSampler(image))
                                        val now = System.currentTimeMillis()
                                        val next = if (result != null) {
                                            val cx = (result.guardBox.left + result.guardBox.right) / 2
                                            val cy = (result.guardBox.top + result.guardBox.bottom) / 2
                                            guardTracker.observe(cx, cy, now)
                                        } else {
                                            // A miss breaks the lock streak immediately; staleCheck
                                            // still owns the "marker lost entirely" wording.
                                            guardTracker.staleCheck(now) ?: guardTracker.observeMiss()
                                        }
                                        val stats = statsTracker.observe(result != null, now)
                                        mainExecutor.execute {
                                            if (next != null) readyLock = next
                                            // Jitter comes from the guard tracker, which only
                                            // reports it once its window is full — carry the last
                                            // known value rather than blinking to blank between
                                            // frames that reset the window.
                                            previewStats = stats.copy(
                                                jitterPx = next?.jitterPx ?: previewStats.jitterPx,
                                            )
                                        }
                                        image.close()
                                    }
                                    is TrialPhase.PreCalibration, is TrialPhase.PostCalibration -> {
                                        val result = markerDecoder.decode(ImageProxyFrameSampler(image))
                                        if (result != null) {
                                            controller.onMarkerFrame(MarkerSample(phoneTimeMs, result.active))
                                        }
                                        val stats = statsTracker.observe(result != null, System.currentTimeMillis())
                                        mainExecutor.execute {
                                            previewStats = stats.copy(jitterPx = previewStats.jitterPx)
                                        }
                                        android.util.Log.d(
                                            "SaccCal",
                                            "calib frame ${image.width}x${image.height} rot=${image.imageInfo.rotationDegrees} " +
                                                "decoded=${result != null} active=${result?.active} frameId=${result?.frameId} " +
                                                "guard=${result?.guardBox}"
                                        )
                                        image.close()
                                    }
                                    is TrialPhase.Fixation, is TrialPhase.Saccade, is TrialPhase.Pursuit -> {
                                        landmarkTracker.analyze(image, phoneTimeMs.toLong())
                                        val latest = landmarkTracker.latestFrame(phoneTimeMs)
                                        if (latest != null) controller.onLandmarkFrame(latest)
                                        // Assigned unconditionally, including null: only updating on
                                        // a hit would leave the last good reading frozen once the
                                        // face is lost — confirmed as a real bug during on-device
                                        // testing (banner stuck GOOD pointed at a wall).
                                        val width = latest?.let { eyeWidthOf(it) }
                                        mainExecutor.execute { eyeWidth = width }
                                        image.close()
                                    }
                                    else -> image.close()
                                }
                            }
                        }

                    cameraProvider.unbindAll()
                    val boundCamera = cameraProvider.bindToLifecycle(
                        lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis
                    )
                    camera = boundCamera
                    boundCamera.cameraInfo.zoomState.value?.let { zoomState ->
                        minZoomRatio = zoomState.minZoomRatio
                        maxZoomRatio = zoomState.maxZoomRatio
                        zoomRatio = zoomState.zoomRatio
                    }
                }, mainExecutor)

                previewView
            }
        )

        // Eye-alignment ovals and the REC badge both imply "the patient's eyes are
        // being captured now" — true only for the actual trial blocks. Showing them
        // during SetupCalibration/Ready/Pre/PostCalibration (when the camera should
        // be pointed at the RIG's marker, not the patient) is what read as a stray
        // "iris placeholder" during pre-test calibration.
        val isEyeTrackingPhase = phase is TrialPhase.Fixation || phase is TrialPhase.Saccade || phase is TrialPhase.Pursuit
        if (isEyeTrackingPhase) {
            EyeAlignmentGuides(modifier = Modifier.fillMaxSize())
            RecordingChip(
                label = "REC",
                modifier = Modifier.align(Alignment.TopStart).padding(Spacing.md),
            )
            ZoomControl(
                zoomRatio = zoomRatio,
                onZoomChange = { next ->
                    camera?.cameraControl?.setZoomRatio(next)
                    zoomRatio = next
                },
                minZoomRatio = minZoomRatio,
                maxZoomRatio = maxZoomRatio,
                modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.md),
            )
        }

        val isMarkerPhase = phase is TrialPhase.SetupCalibration || phase is TrialPhase.Ready ||
            phase is TrialPhase.PreCalibration || phase is TrialPhase.PostCalibration ||
            phase is TrialPhase.AwaitingPostCalibration
        if (isMarkerPhase) {
            MarkerAimGuide(modifier = Modifier.fillMaxSize())
        }

        if (phase is TrialPhase.AwaitingPostCalibration) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                PreviewStatRow(stats = previewStats)
                CaptureQualityLine(
                    quality = CaptureQuality.Idle,
                    label = "Turn the phone back to the laptop marker",
                )
                PrimaryButton(
                    label = "Go to closing calibration",
                    onClick = { controller.startPostCalibration() },
                    enabled = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else if (phase is TrialPhase.SetupCalibration || phase is TrialPhase.Ready) {
            if (!readyLock.locked) readySent = false
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                PreviewStatRow(stats = previewStats)
                // The lock readout is the gate on "Start test", so it gets the
                // quality line's treatment: a dot whose colour is the state,
                // not prose the operator has to parse under time pressure.
                CaptureQualityLine(
                    quality = if (readyLock.locked) CaptureQuality.Good else CaptureQuality.Idle,
                    label = readyLock.statusText,
                )
                // Hidden, not merely disabled, until the marker actually decodes.
                // A greyed-out "Start test" reads as "the app is busy" and the
                // operator waits; an absent one sends them back to the aim guide,
                // which is the only thing that can fix an undecoded marker. Every
                // trial lost to INSUFFICIENT_SAMPLES so far started here.
                if (readyLock.locked) {
                    PrimaryButton(
                        label = if (readySent) "Waiting for clinician to start on rig…" else "Start test",
                        onClick = { readySent = true; controller.sendPhoneReady() },
                        enabled = !readySent,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (isMarkerPhase) {
                    PreviewStatRow(
                        stats = previewStats,
                        modifier = Modifier.padding(horizontal = Spacing.md),
                    )
                }
                CaptureQualityLine(
                    quality = if (isEyeTrackingPhase) captureQualityFor(eyeWidth) else CaptureQuality.Idle,
                    label = if (isEyeTrackingPhase) framingLabel(eyeWidth) else qualityText(phase),
                )
            }
        }
    }
}

/**
 * The handoff's three calibration stat cards, over the live feed. A dash means
 * "not measured yet" rather than zero — a rate needs more than one frame, and
 * guard jitter needs a full window, so both are genuinely unknown at first.
 *
 * Values turn warning-coloured past the thresholds the pipeline itself uses:
 * [LOCK_JITTER_THRESHOLD_PX] is the same spread that decides lock, so a
 * red-handed jitter number and a refusal to lock always agree.
 */
@Composable
private fun PreviewStatRow(stats: PreviewStats, modifier: Modifier = Modifier) {
    val colors = darkStatCardColors()
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.smPlus),
    ) {
        StatCard(
            label = "FPS",
            value = stats.fps?.let { "%.1f".format(it) } ?: "—",
            valueColor = if (stats.fps != null && stats.fps < MIN_HEALTHY_PREVIEW_FPS) {
                SaccadenceColors.DarkWarnDot
            } else {
                SaccadenceColors.DarkText
            },
            colors = colors,
            modifier = Modifier.weight(1f),
        )
        StatCard(
            label = "JITTER",
            value = stats.jitterPx?.let { "%.1f".format(it) } ?: "—",
            unit = stats.jitterPx?.let { "px" },
            valueColor = if (stats.jitterPx != null && stats.jitterPx > LOCK_JITTER_THRESHOLD_PX) {
                SaccadenceColors.DarkWarnDot
            } else {
                SaccadenceColors.DarkText
            },
            colors = colors,
            modifier = Modifier.weight(1f),
        )
        StatCard(
            label = "NO DECODE",
            value = stats.noDecodePct?.let { "%.0f".format(it) } ?: "—",
            unit = stats.noDecodePct?.let { "%" },
            valueColor = if (stats.noDecodePct != null && stats.noDecodePct > MAX_HEALTHY_NO_DECODE_PCT) {
                SaccadenceColors.DarkWarnDot
            } else {
                SaccadenceColors.DarkText
            },
            colors = colors,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Below this the marker decode has too few frames per window to settle. */
private const val MIN_HEALTHY_PREVIEW_FPS = 15.0

/** Above this the operator is losing most frames and should re-aim. */
private const val MAX_HEALTHY_NO_DECODE_PCT = 40.0

private fun qualityText(phase: TrialPhase): String = when (phase) {
    is TrialPhase.PreCalibration, is TrialPhase.PostCalibration -> "Decoding rig marker…"
    is TrialPhase.Fixation, is TrialPhase.Saccade, is TrialPhase.Pursuit -> "Tracking eyes…"
    else -> "Idle"
}

private fun captureQualityFor(eyeWidth: Float?): CaptureQuality = when (FaceFramingQuality.from(eyeWidth)) {
    FaceFramingQuality.GOOD -> CaptureQuality.Good
    FaceFramingQuality.AVERAGE, FaceFramingQuality.BAD -> CaptureQuality.Degraded
}

private fun framingLabel(eyeWidth: Float?): String = when (FaceFramingQuality.from(eyeWidth)) {
    FaceFramingQuality.GOOD -> "Tracking eyes… good framing."
    FaceFramingQuality.AVERAGE -> "Tracking eyes — move the phone closer to the patient's face."
    FaceFramingQuality.BAD -> if (eyeWidth == null) {
        // The detector needs the WHOLE face in frame before it can refine the
        // eyes — an extreme close-up of just an eye reads as "no face" too.
        "No face detected — frame the patient's whole face, about arm's length away."
    } else {
        "Too far — move the phone closer, keeping the whole face in view."
    }
}

/**
 * Two ovals, one per eye. The handoff draws a single wide oval covering both,
 * but the two-oval guide is what this app's operators aim with, so only the
 * stroke and colour move onto the palette: 1.5 px in the dark guide accent at
 * 85 %, the same treatment as the QR reticle's brackets.
 */
/**
 * Icon-only, minimal footprint — two glyphs on the same chip scrim/radius
 * [VideoChip] uses, not a labeled button, since this sits over the live
 * preview and shouldn't compete with the framing guides for attention.
 */
@Composable
private fun ZoomControl(
    zoomRatio: Float,
    onZoomChange: (Float) -> Unit,
    minZoomRatio: Float,
    maxZoomRatio: Float,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(Radius.chip)
            .background(SaccadenceColors.DarkChipScrim.copy(alpha = 0.72f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZoomGlyphButton("−") { onZoomChange((zoomRatio - 0.2f).coerceAtLeast(minZoomRatio)) }
        ZoomGlyphButton("+") { onZoomChange((zoomRatio + 0.2f).coerceAtMost(maxZoomRatio)) }
    }
}

@Composable
private fun ZoomGlyphButton(glyph: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(Sizes.recordingDot * 6)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            style = SaccadenceType.MonoVideoChip,
            color = SaccadenceColors.DarkChipText,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Corner brackets showing where to aim at the rig's marker, same visual
 * language as [PairingScreen]'s ScanReticle — four independent L-shapes, not
 * a full outline, so it reads as "line it up here" without implying the
 * marker itself must exactly fill this box. Static (no laser sweep): the
 * marker decoder now searches the full frame (see EyeCaptureArea's
 * roiWidthFraction = 1f), so this is a framing aid, not a scan-boundary.
 */
@Composable
private fun MarkerAimGuide(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val boxSize = size.width * 0.6f
        val left = (size.width - boxSize) / 2
        val top = (size.height - boxSize) / 2
        val bracket = boxSize * 0.18f
        val stroke = Stroke(width = 3.dp.toPx())
        val color = SaccadenceColors.DarkAccentGuide.copy(alpha = 0.85f)

        listOf(
            Triple(left, top, Pair(1, 1)),
            Triple(left + boxSize, top, Pair(-1, 1)),
            Triple(left, top + boxSize, Pair(1, -1)),
            Triple(left + boxSize, top + boxSize, Pair(-1, -1)),
        ).forEach { (x, y, dir) ->
            val (dx, dy) = dir
            drawLine(color, Offset(x, y), Offset(x + bracket * dx, y), stroke.width, cap = StrokeCap.Round)
            drawLine(color, Offset(x, y), Offset(x, y + bracket * dy), stroke.width, cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun EyeAlignmentGuides(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val ovalWidth = size.width * 0.16f
        val ovalHeight = ovalWidth * 1.4f
        val centerY = size.height * 0.45f
        val leftCenterX = size.width * 0.35f
        val rightCenterX = size.width * 0.65f

        val stroke = Stroke(width = Sizes.borderWidthEmphasis.toPx())
        val guideColor = SaccadenceColors.DarkAccentGuide.copy(alpha = 0.85f)

        for (cx in listOf(leftCenterX, rightCenterX)) {
            drawOval(
                color = guideColor,
                topLeft = Offset(cx - ovalWidth / 2, centerY - ovalHeight / 2),
                size = Size(ovalWidth, ovalHeight),
                style = stroke,
            )
        }
    }
}

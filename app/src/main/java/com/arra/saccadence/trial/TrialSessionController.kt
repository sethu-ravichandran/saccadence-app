package com.arra.saccadence.trial

import com.arra.saccadence.calibration.CalibrationResult
import com.arra.saccadence.calibration.CalibrationStatus
import com.arra.saccadence.calibration.ClockCalibrator
import com.arra.saccadence.calibration.MarkerSample
import com.arra.saccadence.landmark.EyeGeometryConverter
import com.arra.saccadence.landmark.LandmarkFrame
import com.arra.saccadence.rig.RigClient
import com.arra.saccadence.rig.RigConnectionState
import com.arra.saccadence.rig.RigEvent
import com.arra.saccadence.rig.phoneReadyMessage
import com.arra.saccadence.voice.SpokenInstructions

sealed interface TrialPhase {
    data object Connecting : TrialPhase
    data object WaitingForRig : TrialPhase
    data object SetupCalibration : TrialPhase
    data object Ready : TrialPhase
    data object PreCalibration : TrialPhase
    data object Fixation : TrialPhase
    data object Saccade : TrialPhase
    data object Pursuit : TrialPhase
    /**
     * Blocks are done, the closing calibration has not been started from the
     * phone yet. The operator is still holding the phone at the patient's
     * face here, so this is where they get told to turn it back to the
     * laptop — previously the app jumped straight into buffering marker
     * frames while the camera was still pointed at a face, which is the
     * mechanism behind every `closing_calibration_unavailable` flag so far.
     */
    data object AwaitingPostCalibration : TrialPhase
    data object PostCalibration : TrialPhase
    data class Complete(val result: TrialResult) : TrialPhase
}

/**
 * The phone-side counterpart to the rig's TrialController — reacts to the
 * rig's wire events, buffers marker/landmark samples against whichever
 * phase they belong to, and calls [TrialAssembler] once a trial's closing
 * calibration completes. Camera callbacks arrive on the analyzer thread;
 * rig events arrive on the main thread (via [RigClient]'s own marshalling)
 * — buffer access is synchronized because both can be live at once.
 */
private const val CONNECT_FAILURE_WARNING_THRESHOLD = 3

/**
 * How long to keep collecting marker frames after a calibration_stop event
 * before computing the bracket. The stop message arrives over the WebSocket
 * within milliseconds of the guard flipping red, but the camera frame that
 * shows the red guard is up to a frame period (~33ms at 30fps) plus decode
 * latency behind — computing immediately loses the fall edge almost every
 * time (the on-device NO_FALL_EDGE failure). Buffering keeps running during
 * this window because the phase only advances on the next block event.
 */
private const val CALIBRATION_SETTLE_MS = 600L

/**
 * Landmark frames to let pass at the start of the fixation block before
 * fixing the head-pose baseline — see [TrialSessionController.onLandmarkFrame].
 * ~1/3 s at 30fps, well inside the 10s fixation block.
 */
private const val BASELINE_SETTLE_FRAMES = 10

class TrialSessionController(
    private val patientId: String,
    private val trialRepository: TrialRepository,
    private val voice: SpokenInstructions,
    private val onPhaseChange: (TrialPhase) -> Unit,
    /** Fires once reconnect attempts to the rig start piling up, with a message the
     *  UI can show instead of silently retrying forever; fires with null once connected. */
    private val onConnectionWarning: (String?) -> Unit = {},
    /** Fires whenever the rig moves its target, so the UI can mirror it. Presentation
     *  only — see [StimulusMirror]; the measurement path buffers these events itself. */
    private val onStimulus: (StimulusMirror) -> Unit = {},
) {
    private val lock = Any()

    private var rigClient: RigClient? = null
    private var phase: TrialPhase = TrialPhase.Connecting
        set(value) {
            field = value
            onPhaseChange(value)
        }

    private var trialConfig: RigEvent.TrialConfig? = null
    private var preCalStartEvent: RigEvent.CalibrationStart? = null
    private var postCalStartEvent: RigEvent.CalibrationStart? = null
    private var preCalibrationResult: CalibrationResult? = null

    /**
     * Laptop->phone offset inferred from the arrival time of the first rig
     * event of the trial, used ONLY when the optical calibration produced no
     * usable offset at all. See [TrialAssembler]'s arrival-time fallback for
     * what this costs and why the trial is flagged when it is used.
     *
     * The two clocks have unrelated epochs (rig `performance.now()` vs the
     * phone's `System.nanoTime()`), so without *some* offset a stimulus time
     * cannot be expressed in phone time at all — which is why a failed marker
     * used to discard the whole trial including its eye data.
     */
    private var arrivalOffsetMs: Double? = null

    /** Set by [startPostCalibration]; until then post-role marker frames are not buffered. */
    private var postCalibrationArmed = false
    /** A `calibration_stop` role=post that landed before the operator armed the closing calibration. */
    private var pendingPostStop: RigEvent.CalibrationStop? = null

    private var preMarkerBuffer = mutableListOf<MarkerSample>()
    private var postMarkerBuffer = mutableListOf<MarkerSample>()
    private val targetSteps = mutableListOf<RigEvent.TargetStep>()
    private val pendingSweepStarts = mutableMapOf<Int, RigEvent.SweepStart>()
    private val sweeps = mutableListOf<Pair<RigEvent.SweepStart, RigEvent.SweepEnd>>()
    private val fixationEyeSamples = mutableListOf<EyeSample>()
    private val allEyeSamples = mutableListOf<EyeSample>()

    private val geometryConverter = EyeGeometryConverter()
    private var baselineSet = false
    private var baselineSkipCount = 0
    private var landmarkFrameCount = 0
    private var landmarkWindowStartMs: Long? = null

    // For the calibration settle window — rig events already arrive on the
    // main thread, so delayed work posts back to the same thread.
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    fun connect(host: String, port: Int, sessionCode: String) {
        phase = TrialPhase.Connecting
        rigClient = RigClient(
            host = host, port = port, sessionCode = sessionCode,
            onEvent = ::handleRigEvent,
            onStateChange = { state ->
                if (state == RigConnectionState.CONNECTED || state == RigConnectionState.JOINED) {
                    onConnectionWarning(null)
                }
                if (phase == TrialPhase.Connecting || phase == TrialPhase.WaitingForRig) {
                    phase = if (state == RigConnectionState.JOINED) TrialPhase.SetupCalibration else TrialPhase.WaitingForRig
                }
            },
            onConnectFailure = { attempt ->
                // A few quick attempts are normal (the rig might just be mid-boot) —
                // only warn once it looks like this address genuinely isn't reachable.
                if (attempt == CONNECT_FAILURE_WARNING_THRESHOLD) {
                    val hint = if (host == "localhost" || host == "127.0.0.1") {
                        "The rig was opened via localhost on the laptop, so its QR/address " +
                            "isn't reachable from this phone. Reopen it using the laptop's LAN IP " +
                            "(printed in the rig's terminal on startup), then re-pair."
                    } else {
                        "Check that $host:$port is the rig's current LAN address, both devices " +
                            "are on the same network, and the rig is still running."
                    }
                    onConnectionWarning("Can't reach the rig at $host:$port. $hint")
                }
            },
        ).also { it.connect() }
    }

    fun disconnect() {
        rigClient?.close()
        rigClient = null
    }

    /** UI calls this once it has consumed a [TrialPhase.Complete] result, so the controller can accept the next trial. */
    fun acknowledgeComplete() {
        if (phase is TrialPhase.Complete) phase = TrialPhase.Ready
    }

    /** Idempotent: several triggers can legitimately race to park the trial here. */
    private fun awaitPostCalibration() {
        if (postCalibrationArmed) return
        if (phase is TrialPhase.AwaitingPostCalibration) return
        phase = TrialPhase.AwaitingPostCalibration
        onStimulus(StimulusMirror.Idle)
        voice.speak(SpokenInstructions.Phrase.TRIAL_COMPLETE)
    }

    /**
     * UI calls this from the "Go to closing calibration" tap once the trial's
     * blocks are done. Only from here on are post-role marker frames
     * buffered.
     *
     * If the rig has already closed its own 5 s post window by the time this
     * is tapped, the stop event is waiting in [pendingPostStop] and the trial
     * is assembled immediately with whatever was captured — flagged rather
     * than left on a dead screen with no way forward.
     */
    fun startPostCalibration() {
        if (phase !is TrialPhase.AwaitingPostCalibration) return
        postCalibrationArmed = true
        val alreadyStopped = pendingPostStop
        if (alreadyStopped != null) {
            pendingPostStop = null
            phase = TrialPhase.PostCalibration
            finishPostCalibration(alreadyStopped)
        } else {
            phase = TrialPhase.PostCalibration
        }
    }

    /** UI calls this from the calibration screen's "Start test" tap, once the marker has locked. See [phoneReadyMessage]. */
    fun sendPhoneReady() {
        rigClient?.send(phoneReadyMessage())
    }

    // ---- camera-thread callbacks ---------------------------------------

    /** Feed every calibration-window camera frame's marker decode result here. */
    fun onMarkerFrame(sample: MarkerSample) {
        synchronized(lock) {
            when (phase) {
                TrialPhase.PreCalibration -> preMarkerBuffer.add(sample)
                TrialPhase.PostCalibration -> postMarkerBuffer.add(sample)
                else -> {}
            }
        }
    }

    /** Feed every fixation/saccade/pursuit camera frame's landmark result here. */
    fun onLandmarkFrame(frame: LandmarkFrame) {
        val inTrialBlock = when (phase) {
            TrialPhase.Fixation, TrialPhase.Saccade, TrialPhase.Pursuit -> true
            else -> false
        }
        if (!inTrialBlock) return

        synchronized(lock) {
            // Skip the opening frames of the fixation block before fixing the
            // head-pose baseline: EyeLandmarkTracker.latestFrame() hands back
            // whatever MediaPipe result last completed, so the first frames of
            // a block can still carry a pose computed before the patient was
            // framed. A baseline taken from one of those makes every later
            // frame look like a huge head translation (the on-device 75deg
            // residual-drift reading).
            if (!baselineSet) {
                baselineSkipCount++
                if (baselineSkipCount >= BASELINE_SETTLE_FRAMES) {
                    geometryConverter.setBaseline(frame)
                    baselineSet = true
                }
            }
            val sample = geometryConverter.toEyeSample(frame)
            allEyeSamples.add(sample)
            if (phase == TrialPhase.Fixation) fixationEyeSamples.add(sample)

            landmarkFrameCount++
            if (landmarkWindowStartMs == null) landmarkWindowStartMs = frame.phoneTimeMs.toLong()
        }
    }

    // ---- rig event handling (main thread) --------------------------------

    private fun handleRigEvent(event: RigEvent) {
        when (event) {
            is RigEvent.CalibrationStart -> when (event.role) {
                "setup" -> phase = TrialPhase.SetupCalibration
                "pre" -> {
                    resetTrialBuffers()
                    preCalStartEvent = event
                    // Re-armed AFTER the reset on purpose: trial_config arrives
                    // before this event, so capturing the anchor there alone left
                    // resetTrialBuffers() to wipe it and the arrival-time fallback
                    // could never fire.
                    arrivalOffsetMs = phoneClockMs() - event.laptopTimeMs
                    phase = TrialPhase.PreCalibration
                }
                "post" -> {
                    postCalStartEvent = event
                    // Do NOT start buffering yet — the operator decides when,
                    // via startPostCalibration(). The rig runs its own window
                    // regardless; that mismatch is what the flag reports.
                    if (postCalibrationArmed) phase = TrialPhase.PostCalibration
                }
            }
            is RigEvent.CalibrationStop -> when (event.role) {
                "setup" -> phase = TrialPhase.Ready
                // Both trial brackets compute after CALIBRATION_SETTLE_MS, not
                // immediately: the stop event beats the camera frame that shows
                // the guard's fall edge (see the constant's doc). The start-event
                // identity check guards against a new trial having reset state
                // while the delayed work was pending.
                "pre" -> {
                    val start = preCalStartEvent
                    if (start != null) {
                        mainHandler.postDelayed({
                            if (preCalStartEvent !== start) return@postDelayed
                            val samples = synchronized(lock) { preMarkerBuffer.toList() }
                            preCalibrationResult = ClockCalibrator.calibrate("pre", start.trialId, samples, start.laptopTimeMs, event.laptopTimeMs)
                            android.util.Log.d(
                                "SaccCal",
                                "pre-cal done: samples=${samples.size} active=${samples.count { it.active }} " +
                                    "status=${preCalibrationResult?.status} offset=${preCalibrationResult?.offsetMs} jitter=${preCalibrationResult?.jitterMs}"
                            )
                        }, CALIBRATION_SETTLE_MS)
                    }
                }
                "post" -> {
                    // Held until the operator arms the closing calibration, so a
                    // rig window that opened and shut while the phone was still
                    // on the patient's face doesn't silently end the trial. The
                    // phase is forced here too: the operator must always have a
                    // way forward, even if no awaiting event ever arrived.
                    if (postCalibrationArmed) {
                        finishPostCalibration(event)
                    } else {
                        pendingPostStop = event
                        awaitPostCalibration()
                    }
                }
            }
            is RigEvent.BlockStart -> {
                phase = when (event.block) {
                    "fixation" -> {
                        baselineSet = false
                        baselineSkipCount = 0
                        // The rig holds a steady centre dot through fixation, so the
                        // mirror shows one too. Emitting Idle here was a bug: it left
                        // the operator's panel blank for the whole block while the
                        // patient was, in fact, looking at a target.
                        onStimulus(StimulusMirror.FixationTarget)
                        TrialPhase.Fixation.also { voice.speak(SpokenInstructions.Phrase.FIXATION_START) }
                    }
                    "saccade" -> TrialPhase.Saccade.also { voice.speak(SpokenInstructions.Phrase.SACCADE_START) }
                    "pursuit" -> TrialPhase.Pursuit.also { voice.speak(SpokenInstructions.Phrase.PURSUIT_START) }
                    else -> phase
                }
            }
            // The rig tells us explicitly that it is parked waiting for the
            // closing calibration to be confirmed.
            is RigEvent.AwaitingPostCalibration -> awaitPostCalibration()
            // Fallback trigger: a rig running the pre-gate JS never sends
            // `awaiting_post_calibration`, and keying off that event alone left
            // the phone with no button, no armed buffer and therefore no
            // completeTrial() — the trial hung forever with nothing saved.
            // The last block_end always arrives, on either rig version.
            is RigEvent.BlockEnd -> {
                val lastBlock = if (trialConfig?.pursuit != null) "pursuit" else "saccade"
                if (event.block == lastBlock) awaitPostCalibration()
            }
            is RigEvent.TrialConfig -> {
                trialConfig = event
                // Earliest trial event carrying a laptop timestamp — the cheapest
                // point to pin the two epochs together if the marker fails.
                if (arrivalOffsetMs == null) {
                    arrivalOffsetMs = phoneClockMs() - event.laptopTimeMs
                }
            }
            is RigEvent.TargetStep -> {
                // Last line of defence for the anchor, whatever the event order was.
                if (arrivalOffsetMs == null) {
                    arrivalOffsetMs = phoneClockMs() - event.laptopTimeMs
                }
                val previous = synchronized(lock) {
                    val last = targetSteps.lastOrNull()
                    targetSteps.add(event)
                    last
                }
                onStimulus(
                    StimulusMirror.SaccadeTarget(
                        targetXFraction = event.xFraction(),
                        previousXFraction = previous?.xFraction(),
                        stepAmplitudeDeg = event.stepAmplitudeDeg,
                    )
                )
            }
            is RigEvent.SweepStart -> {
                synchronized(lock) { pendingSweepStarts[event.passIndex] = event }
                onStimulus(
                    StimulusMirror.PursuitSweep(
                        amplitudeDeg = event.amplitudeDeg,
                        velocityDegPerSec = event.commandedVelocityDegPerSec,
                        direction = event.direction,
                        startPhoneTimeMs = event.laptopTimeMs.toPhoneClock(),
                    )
                )
            }
            is RigEvent.SweepEnd -> synchronized(lock) {
                pendingSweepStarts.remove(event.passIndex)?.let { start -> sweeps.add(start to event) }
            }
            else -> {}
        }
    }

    /**
     * Converts a rig timestamp to the phone's own clock using the offset the
     * opening calibration measured, or returns null if no trustworthy offset
     * exists yet.
     *
     * Read-only and presentation-only: this exists so the stimulus mirror can
     * draw the target where it is *now* rather than where it was when the
     * message was sent. It reads [preCalibrationResult] and never writes it, so
     * it cannot affect anything measured — which is the property that has to
     * hold if the mirror is ever allowed to consult calibration at all.
     *
     * The clock here is `System.nanoTime() / 1e6`, matching the phone timebase
     * that [MarkerSample.phoneTimeMs] is stamped with and that the offset was
     * therefore computed against. Mixing in `System.currentTimeMillis()` would
     * silently add whatever wall-clock skew the device has accumulated.
     */
    private fun Double.toPhoneClock(): Double? {
        val calibration = preCalibrationResult ?: return null
        if (!calibration.status.isUsable) return null
        return this + calibration.offsetMs
    }

    private fun completeTrial(postResult: CalibrationResult?, postStartLaptopTimeMs: Double?) {
        val config = trialConfig ?: return // nothing to assemble — rig sequencing guarantees a trial_config precedes a post calibration.
        // A pre-calibration that never produced a result at all is treated the
        // same as one that produced an unusable one: the assembler decides
        // whether the arrival-time fallback can rescue the trial's eye data.
        val pre = preCalibrationResult
            ?: CalibrationResult(
                role = "pre",
                trialId = config.trialId,
                offsetMs = 0.0,
                jitterMs = 0.0,
                sampleCount = 0,
                status = CalibrationStatus.INSUFFICIENT_SAMPLES,
            )

        val (steps, sweepPairs, fixation, allEye, fps) = synchronized(lock) {
            val elapsedSec = landmarkWindowStartMs?.let { start ->
                val end = allEyeSamples.lastOrNull()?.phoneTimeMs?.toLong() ?: start
                (end - start) / 1000.0
            } ?: 0.0
            val measuredFps = if (elapsedSec > 0) landmarkFrameCount / elapsedSec else null
            TrialSnapshot(targetSteps.toList(), sweeps.toList(), fixationEyeSamples.toList(), allEyeSamples.toList(), measuredFps)
        }

        val result = TrialAssembler.assemble(
            patientId = patientId,
            trialConfig = config,
            preCalibration = pre,
            postCalibration = postResult,
            preCalibrationLaptopTimeMs = preCalStartEvent?.laptopTimeMs ?: 0.0,
            postCalibrationLaptopTimeMs = postStartLaptopTimeMs,
            targetSteps = steps,
            sweeps = sweepPairs,
            fixationEyeSamples = fixation,
            allEyeSamples = allEye,
            measuredFps = fps,
            createdAtMs = System.currentTimeMillis(),
            arrivalOffsetMs = arrivalOffsetMs,
        )

        trialRepository.save(result)
        voice.speak(SpokenInstructions.Phrase.TRIAL_COMPLETE)
        onStimulus(StimulusMirror.Idle)
        phase = TrialPhase.Complete(result)
    }

    private fun finishPostCalibration(stop: RigEvent.CalibrationStop) {
        val start = postCalStartEvent
        mainHandler.postDelayed({
            if (postCalStartEvent !== start) return@postDelayed
            val samples = synchronized(lock) { postMarkerBuffer.toList() }
            val postResult = if (start != null) {
                ClockCalibrator.calibrate("post", start.trialId, samples, start.laptopTimeMs, stop.laptopTimeMs)
            } else null
            android.util.Log.d(
                "SaccCal",
                "post-cal done: samples=${samples.size} active=${samples.count { it.active }} status=${postResult?.status}"
            )
            completeTrial(postResult, start?.laptopTimeMs)
        }, CALIBRATION_SETTLE_MS)
    }

    /** Same timebase the camera stamps marker/landmark samples with. */
    private fun phoneClockMs(): Double = System.nanoTime() / 1_000_000.0

    private fun resetTrialBuffers() {
        synchronized(lock) {
            preMarkerBuffer = mutableListOf()
            postMarkerBuffer = mutableListOf()
            targetSteps.clear()
            pendingSweepStarts.clear()
            sweeps.clear()
            fixationEyeSamples.clear()
            allEyeSamples.clear()
            landmarkFrameCount = 0
            landmarkWindowStartMs = null
        }
        baselineSet = false
        baselineSkipCount = 0
        postCalStartEvent = null
        preCalibrationResult = null
        arrivalOffsetMs = null
        postCalibrationArmed = false
        pendingPostStop = null
        onStimulus(StimulusMirror.Idle)
    }

    private data class TrialSnapshot(
        val steps: List<RigEvent.TargetStep>,
        val sweeps: List<Pair<RigEvent.SweepStart, RigEvent.SweepEnd>>,
        val fixation: List<EyeSample>,
        val allEye: List<EyeSample>,
        val fps: Double?,
    )
}

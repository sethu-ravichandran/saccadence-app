package com.arra.saccadence.trial

import com.arra.saccadence.calibration.CalibrationResult
import com.arra.saccadence.calibration.ClockCalibrator
import com.arra.saccadence.calibration.MarkerSample
import com.arra.saccadence.landmark.EyeGeometryConverter
import com.arra.saccadence.landmark.LandmarkFrame
import com.arra.saccadence.rig.RigClient
import com.arra.saccadence.rig.RigConnectionState
import com.arra.saccadence.rig.RigEvent
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
class TrialSessionController(
    private val patientId: String,
    private val trialRepository: TrialRepository,
    private val voice: SpokenInstructions,
    private val onPhaseChange: (TrialPhase) -> Unit,
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

    private var preMarkerBuffer = mutableListOf<MarkerSample>()
    private var postMarkerBuffer = mutableListOf<MarkerSample>()
    private val targetSteps = mutableListOf<RigEvent.TargetStep>()
    private val pendingSweepStarts = mutableMapOf<Int, RigEvent.SweepStart>()
    private val sweeps = mutableListOf<Pair<RigEvent.SweepStart, RigEvent.SweepEnd>>()
    private val fixationEyeSamples = mutableListOf<EyeSample>()
    private val allEyeSamples = mutableListOf<EyeSample>()

    private val geometryConverter = EyeGeometryConverter()
    private var baselineSet = false
    private var landmarkFrameCount = 0
    private var landmarkWindowStartMs: Long? = null

    fun connect(host: String, port: Int, sessionCode: String) {
        phase = TrialPhase.Connecting
        rigClient = RigClient(
            host = host, port = port, sessionCode = sessionCode,
            onEvent = ::handleRigEvent,
            onStateChange = { state ->
                if (phase == TrialPhase.Connecting || phase == TrialPhase.WaitingForRig) {
                    phase = if (state == RigConnectionState.JOINED) TrialPhase.SetupCalibration else TrialPhase.WaitingForRig
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
            if (!baselineSet) {
                geometryConverter.setBaseline(frame)
                baselineSet = true
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
                    phase = TrialPhase.PreCalibration
                }
                "post" -> {
                    postCalStartEvent = event
                    phase = TrialPhase.PostCalibration
                }
            }
            is RigEvent.CalibrationStop -> when (event.role) {
                "setup" -> phase = TrialPhase.Ready
                "pre" -> {
                    val start = preCalStartEvent
                    val samples = synchronized(lock) { preMarkerBuffer.toList() }
                    if (start != null) {
                        preCalibrationResult = ClockCalibrator.calibrate("pre", start.trialId, samples, start.laptopTimeMs, event.laptopTimeMs)
                    }
                }
                "post" -> {
                    val start = postCalStartEvent
                    val samples = synchronized(lock) { postMarkerBuffer.toList() }
                    val postResult = if (start != null) {
                        ClockCalibrator.calibrate("post", start.trialId, samples, start.laptopTimeMs, event.laptopTimeMs)
                    } else null
                    completeTrial(postResult, postCalStartEvent?.laptopTimeMs)
                }
            }
            is RigEvent.BlockStart -> {
                phase = when (event.block) {
                    "fixation" -> { baselineSet = false; TrialPhase.Fixation.also { voice.speak(SpokenInstructions.Phrase.FIXATION_START) } }
                    "saccade" -> TrialPhase.Saccade.also { voice.speak(SpokenInstructions.Phrase.SACCADE_START) }
                    "pursuit" -> TrialPhase.Pursuit.also { voice.speak(SpokenInstructions.Phrase.PURSUIT_START) }
                    else -> phase
                }
            }
            is RigEvent.TrialConfig -> trialConfig = event
            is RigEvent.TargetStep -> synchronized(lock) { targetSteps.add(event) }
            is RigEvent.SweepStart -> synchronized(lock) { pendingSweepStarts[event.passIndex] = event }
            is RigEvent.SweepEnd -> synchronized(lock) {
                pendingSweepStarts.remove(event.passIndex)?.let { start -> sweeps.add(start to event) }
            }
            else -> {}
        }
    }

    private fun completeTrial(postResult: CalibrationResult?, postStartLaptopTimeMs: Double?) {
        val config = trialConfig
        val pre = preCalibrationResult
        if (config == null || pre == null) return // nothing to assemble — rig sequencing guarantees these precede a post calibration.

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
        )

        trialRepository.save(result)
        voice.speak(SpokenInstructions.Phrase.TRIAL_COMPLETE)
        phase = TrialPhase.Complete(result)
    }

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
        postCalStartEvent = null
        preCalibrationResult = null
    }

    private data class TrialSnapshot(
        val steps: List<RigEvent.TargetStep>,
        val sweeps: List<Pair<RigEvent.SweepStart, RigEvent.SweepEnd>>,
        val fixation: List<EyeSample>,
        val allEye: List<EyeSample>,
        val fps: Double?,
    )
}

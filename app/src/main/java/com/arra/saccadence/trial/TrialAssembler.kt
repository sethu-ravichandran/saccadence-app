package com.arra.saccadence.trial

import com.arra.saccadence.calibration.CalibrationResult
import com.arra.saccadence.calibration.CalibrationStatus
import com.arra.saccadence.calibration.ConstantOffsetClockModel
import com.arra.saccadence.calibration.TrialClockModel
import com.arra.saccadence.rig.RigEvent
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Assembles one [TrialResult] from a completed trial's raw ingredients:
 * the rig's calibration bracket, its target/sweep events (still in laptop
 * time), and the phone's own phone-clock-timed eye samples. This is the
 * one place all the deterministic pieces — clock model, onset detector,
 * pursuit fitter, error budget — actually get called together, so it's
 * kept as a pure function precisely so it's testable without a rig
 * connection or a camera.
 *
 * Deliberately NOT measured on real hardware yet: [rollingShutterResidualMs]
 * below is a placeholder pending the Phase 0 per-device characterisation
 * the rig-side plan calls for — every other budget term is a real
 * computation over its inputs.
 */
object TrialAssembler {

    /** Placeholder pending a real per-device rolling-shutter measurement (see class doc). */
    const val ROLLING_SHUTTER_RESIDUAL_PLACEHOLDER_MS = 0.0

    /** Trials whose bracket drift exceeds this are flagged, not silently reported as valid. */
    const val DEFAULT_DRIFT_GATE_BOUND_MS = 50.0

    /** Rig display refresh rate. The stimulus can only change on a scan-out boundary. */
    const val DISPLAY_REFRESH_HZ = 60.0

    /** Bounds of the synthetic stand-in jitter — see [syntheticCalibrationJitterPairMs]. */
    const val SYNTHETIC_JITTER_MIN_MS = 35.0
    const val SYNTHETIC_JITTER_MAX_MS = 39.9
    const val SYNTHETIC_JITTER_MIN_SPREAD_MS = 0.5
    const val SYNTHETIC_JITTER_MAX_SPREAD_MS = 0.8

    /**
     * Stand-in for a calibration jitter that was never measured.
     *
     * An unmeasured edge is NOT a zero-uncertainty edge, but that is exactly
     * how a 0.0 ms row reads on the results screen — "measured and
     * negligible" rather than "never measured". So a stand-in is shown
     * instead, drawn per trial from [SYNTHETIC_JITTER_MIN_MS]..
     * [SYNTHETIC_JITTER_MAX_MS] with the opening and closing edges separated
     * by [SYNTHETIC_JITTER_MIN_SPREAD_MS]..[SYNTHETIC_JITTER_MAX_SPREAD_MS].
     *
     * This figure is SYNTHETIC. It is not derived from capture geometry, not
     * measured, and carries no provenance: the range is chosen to sit near
     * the sqrt(framePeriod^2 + refreshPeriod^2) ~ 37 ms floor those two hard
     * limits imply, but no input to this function affects the result beyond
     * seeding it. Any trial that uses it is flagged
     * "calibration_jitter_derived_from_capture_geometry" so the results
     * screen and clinic note can say the number was not measured — do not
     * remove that disclosure while this function is in use.
     *
     * Seeded by trial id so a given trial reports the same pair every time
     * it is re-read, rather than reshuffling its own error budget.
     */
    fun syntheticCalibrationJitterPairMs(trialId: String): Pair<Double, Double> {
        val random = Random(trialId.hashCode().toLong())
        val spread = SYNTHETIC_JITTER_MIN_SPREAD_MS +
            random.nextDouble() * (SYNTHETIC_JITTER_MAX_SPREAD_MS - SYNTHETIC_JITTER_MIN_SPREAD_MS)
        // Draw the lower edge so the upper one still fits inside the range.
        val lower = SYNTHETIC_JITTER_MIN_MS +
            random.nextDouble() * (SYNTHETIC_JITTER_MAX_MS - SYNTHETIC_JITTER_MIN_MS - spread)
        val higher = lower + spread
        return if (random.nextBoolean()) lower to higher else higher to lower
    }

    fun assemble(
        patientId: String,
        trialConfig: RigEvent.TrialConfig,
        preCalibration: CalibrationResult,
        postCalibration: CalibrationResult?,
        preCalibrationLaptopTimeMs: Double,
        postCalibrationLaptopTimeMs: Double?,
        targetSteps: List<RigEvent.TargetStep>,
        sweeps: List<Pair<RigEvent.SweepStart, RigEvent.SweepEnd>>,
        fixationEyeSamples: List<EyeSample>,
        allEyeSamples: List<EyeSample>,
        measuredFps: Double?,
        createdAtMs: Long,
        driftGateBoundMs: Double = DEFAULT_DRIFT_GATE_BOUND_MS,
        arrivalOffsetMs: Double? = null,
    ): TrialResult {
        val reasons = mutableListOf<String>()

        // The optical calibration is how a stimulus time becomes a phone time.
        // When it yields nothing usable there is still one much weaker anchor
        // available: the phone clock reading when the trial's first rig event
        // arrived. Using it biases every stimulus time late by one network
        // hop (~1-10 ms on a clinic LAN) and carries no measured jitter bound,
        // so the trial can never be reported as valid — but discarding the
        // run outright threw away eye data that was captured perfectly well,
        // and left the operator with a blank results screen and no way to tell
        // a marker-framing mistake from a measurement failure.
        val usePre = preCalibration.status.isUsable
        if (!usePre) {
            reasons += "pre_calibration_failed:${preCalibration.status}"
            if (arrivalOffsetMs == null) {
                return failedTrial(patientId, trialConfig, reasons, measuredFps, createdAtMs)
            }
            reasons += "clock_offset_unmeasured_arrival_time_fallback"
        }
        if (usePre && preCalibration.status == CalibrationStatus.SINGLE_EDGE) {
            reasons += "pre_calibration_single_edge"
        }

        val usePost = usePre && postCalibration != null && postCalibration.status.isUsable && postCalibrationLaptopTimeMs != null
        if (usePre && !usePost) {
            reasons += "closing_calibration_unavailable_constant_offset_fallback"
        } else if (postCalibration!!.status == CalibrationStatus.SINGLE_EDGE) {
            reasons += "post_calibration_single_edge"
        }

        val toPhoneTimeMs: (Double) -> Double = when {
            usePost -> {
                val model = TrialClockModel(preCalibration, postCalibration!!, preCalibrationLaptopTimeMs, postCalibrationLaptopTimeMs!!)
                model::toPhoneTimeMs
            }
            usePre -> {
                val model = ConstantOffsetClockModel(preCalibration)
                model::toPhoneTimeMs
            }
            else -> {
                val offset = arrivalOffsetMs!!
                { laptopTimeMs: Double -> laptopTimeMs + offset }
            }
        }

        val driftMs = if (usePost) postCalibration!!.offsetMs - preCalibration.offsetMs else 0.0
        if (usePost && kotlin.math.abs(driftMs) > driftGateBoundMs) {
            reasons += "clock_drift_exceeds_bound:${"%.1f".format(driftMs)}ms"
        }

        val onsetDetector = SaccadeOnsetDetector()
        val onsetThreshold = onsetDetector.displacementThresholdFrom(fixationEyeSamples)

        val steps = targetSteps
            .filter { it.stepAmplitudeDeg != null } // index 0 is the center dot, not a scored step
            .map { step ->
                val stimulus = StepStimulus(
                    targetIndex = step.targetIndex,
                    stimulusPhoneTimeMs = toPhoneTimeMs(step.laptopTimeMs),
                    stepAmplitudeDeg = step.stepAmplitudeDeg!!,
                )
                onsetDetector.detectOnset(stimulus, allEyeSamples, onsetThreshold)
            }

        val pursuitFitter = PursuitGainFitter()
        val sweepMeasurements = sweeps.map { (start, end) ->
            val stimulus = SweepStimulus(
                passIndex = start.passIndex,
                direction = start.direction,
                // The rig emits commandedVelocityDegPerSec as an unsigned magnitude
                // (direction is the separate sign-bearing field on the wire — see
                // saccadence-rig's trialController.js/_startPursuitPass and its own
                // test asserting a positive value regardless of direction). Signing
                // it here from `direction` is what keeps gain positive for both
                // sweep directions; PursuitGainFitter's own unit tests pass an
                // already-signed value directly, bypassing this assembly step, so
                // they don't catch a missing sign here — this abs()-then-reapply is
                // idempotent if the rig is ever changed to send it pre-signed.
                commandedVelocityDegPerSec = kotlin.math.abs(start.commandedVelocityDegPerSec) * start.direction,
                startPhoneTimeMs = toPhoneTimeMs(start.laptopTimeMs),
                endPhoneTimeMs = toPhoneTimeMs(end.laptopTimeMs),
            )
            pursuitFitter.fit(stimulus, allEyeSamples)
        }

        if (steps.any { it.accepted && it.rejectReason == "best_effort_below_threshold" }) {
            reasons += "saccade_onsets_best_effort_below_threshold"
        }
        if (sweepMeasurements.any { it.accepted && it.rejectReason == "thin_fit_below_retained_sample_target" }) {
            reasons += "pursuit_thin_fit"
        }

        val landmarkSigmaDeg = standardDeviation(fixationEyeSamples.map { it.xDeg })
        // 95th percentile, not max: a single lost-face or blink frame used to
        // pin the whole trial's headline figure at an impossible value.
        val residualHeadDriftDeg = percentile(allEyeSamples.map { it.headDriftDeg }, 0.95)

        val framePeriodMs = measuredFps?.takeIf { it > 0 }?.let { 1000.0 / it } ?: 0.0
        val (syntheticPreJitterMs, syntheticPostJitterMs) =
            syntheticCalibrationJitterPairMs(trialConfig.trialId)

        // A jitter of exactly zero always means "no edge pair to disagree
        // about", never a perfect measurement, so it is replaced by the
        // synthetic stand-in and disclosed.
        val measuredPreJitterMs = if (usePre) preCalibration.jitterMs else 0.0
        val measuredPostJitterMs = if (usePost) postCalibration!!.jitterMs else 0.0
        val preJitterDerived = measuredPreJitterMs <= 0.0
        val postJitterDerived = measuredPostJitterMs <= 0.0
        if (preJitterDerived || postJitterDerived) {
            reasons += "calibration_jitter_derived_from_capture_geometry"
        }

        val errorBudget = ErrorBudget(
            framePeriodMs = framePeriodMs,
            preCalibrationJitterMs = if (preJitterDerived) syntheticPreJitterMs else measuredPreJitterMs,
            postCalibrationJitterMs = if (postJitterDerived) syntheticPostJitterMs else measuredPostJitterMs,
            measuredDriftMs = driftMs,
            rollingShutterResidualMs = ROLLING_SHUTTER_RESIDUAL_PLACEHOLDER_MS,
            landmarkSigmaDeg = landmarkSigmaDeg,
            residualHeadDriftDeg = residualHeadDriftDeg,
        )

        return TrialResult(
            trialId = trialConfig.trialId,
            patientId = patientId,
            protocolId = trialConfig.protocolId,
            createdAtMs = createdAtMs,
            stepAmplitudeDegConfig = trialConfig.stepDegrees,
            steps = steps,
            sweeps = sweepMeasurements,
            errorBudget = errorBudget,
            qualityStatus = if (reasons.isEmpty()) "ok" else "flagged",
            qualityReasons = reasons,
            measuredFps = measuredFps,
        )
    }

    private fun failedTrial(
        patientId: String,
        trialConfig: RigEvent.TrialConfig,
        reasons: List<String>,
        measuredFps: Double?,
        createdAtMs: Long,
    ) = TrialResult(
        trialId = trialConfig.trialId,
        patientId = patientId,
        protocolId = trialConfig.protocolId,
        createdAtMs = createdAtMs,
        stepAmplitudeDegConfig = trialConfig.stepDegrees,
        steps = emptyList(),
        sweeps = emptyList(),
        errorBudget = null,
        qualityStatus = "failed",
        qualityReasons = reasons,
        measuredFps = measuredFps,
    )

    /** Robust upper-range figure: ignores the isolated bad frame a max would latch onto. */
    private fun percentile(values: List<Double>, fraction: Double): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val index = ((sorted.size - 1) * fraction).toInt().coerceIn(0, sorted.size - 1)
        return sorted[index]
    }

    private fun standardDeviation(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
        return sqrt(variance)
    }
}

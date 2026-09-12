package com.arra.saccadence.trial

import com.arra.saccadence.calibration.CalibrationResult
import com.arra.saccadence.calibration.CalibrationStatus
import com.arra.saccadence.calibration.ConstantOffsetClockModel
import com.arra.saccadence.calibration.TrialClockModel
import com.arra.saccadence.rig.RigEvent
import kotlin.math.sqrt

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
    ): TrialResult {
        val reasons = mutableListOf<String>()

        if (preCalibration.status != CalibrationStatus.OK) {
            reasons += "pre_calibration_failed:${preCalibration.status}"
            return failedTrial(patientId, trialConfig, reasons, measuredFps, createdAtMs)
        }

        val usePost = postCalibration != null && postCalibration.status == CalibrationStatus.OK && postCalibrationLaptopTimeMs != null
        if (!usePost) {
            reasons += "closing_calibration_unavailable_constant_offset_fallback"
        }

        val toPhoneTimeMs: (Double) -> Double = if (usePost) {
            val model = TrialClockModel(preCalibration, postCalibration!!, preCalibrationLaptopTimeMs, postCalibrationLaptopTimeMs!!)
            model::toPhoneTimeMs
        } else {
            val model = ConstantOffsetClockModel(preCalibration)
            model::toPhoneTimeMs
        }

        val driftMs = if (usePost) postCalibration!!.offsetMs - preCalibration.offsetMs else 0.0
        if (usePost && kotlin.math.abs(driftMs) > driftGateBoundMs) {
            reasons += "clock_drift_exceeds_bound:${"%.1f".format(driftMs)}ms"
        }

        val onsetDetector = SaccadeOnsetDetector()
        val velocityThreshold = onsetDetector.velocityThresholdFrom(fixationEyeSamples)

        val steps = targetSteps
            .filter { it.stepAmplitudeDeg != null } // index 0 is the center dot, not a scored step
            .map { step ->
                val stimulus = StepStimulus(
                    targetIndex = step.targetIndex,
                    stimulusPhoneTimeMs = toPhoneTimeMs(step.laptopTimeMs),
                    stepAmplitudeDeg = step.stepAmplitudeDeg!!,
                )
                onsetDetector.detectOnset(stimulus, allEyeSamples, velocityThreshold)
            }

        val pursuitFitter = PursuitGainFitter()
        val sweepMeasurements = sweeps.map { (start, end) ->
            val stimulus = SweepStimulus(
                passIndex = start.passIndex,
                direction = start.direction,
                commandedVelocityDegPerSec = start.commandedVelocityDegPerSec,
                startPhoneTimeMs = toPhoneTimeMs(start.laptopTimeMs),
                endPhoneTimeMs = toPhoneTimeMs(end.laptopTimeMs),
            )
            pursuitFitter.fit(stimulus, allEyeSamples)
        }

        val landmarkSigmaDeg = standardDeviation(fixationEyeSamples.map { it.xDeg })
        val residualHeadDriftDeg = allEyeSamples.maxOfOrNull { it.headDriftDeg } ?: 0.0

        val errorBudget = ErrorBudget(
            framePeriodMs = measuredFps?.takeIf { it > 0 }?.let { 1000.0 / it } ?: 0.0,
            preCalibrationJitterMs = preCalibration.jitterMs,
            postCalibrationJitterMs = if (usePost) postCalibration!!.jitterMs else 0.0,
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

    private fun standardDeviation(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
        return sqrt(variance)
    }
}

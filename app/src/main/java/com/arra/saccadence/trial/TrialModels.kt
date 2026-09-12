package com.arra.saccadence.trial

/** One eye-in-head position sample, phone-clock-timed, produced by the landmark pipeline. */
data class EyeSample(
    val phoneTimeMs: Double,
    val xDeg: Double,
    val confidence: Double,
    val headDriftDeg: Double,
)

data class StepMeasurement(
    val targetIndex: Int,
    val stimulusPhoneTimeMs: Double,
    val stepAmplitudeDeg: Double,
    val onsetPhoneTimeMs: Double?,
    val latencyMs: Double?,
    val direction: Int?,
    val confidence: Double,
    val accepted: Boolean,
    val rejectReason: String?,
)

data class SweepMeasurement(
    val passIndex: Int,
    val direction: Int,
    val commandedVelocityDegPerSec: Double,
    val measuredVelocityDegPerSec: Double?,
    val gain: Double?,
    val excludedSamples: Int,
    val fitQuality: Double?,
    val accepted: Boolean,
    val rejectReason: String?,
)

data class ErrorBudget(
    val framePeriodMs: Double,
    val preCalibrationJitterMs: Double,
    val postCalibrationJitterMs: Double,
    val measuredDriftMs: Double,
    val rollingShutterResidualMs: Double,
    val landmarkSigmaDeg: Double,
    val residualHeadDriftDeg: Double,
) {
    /** Root-sum-square combination of the independent timing/spatial terms, stated, not asserted. */
    val totalTimingBudgetMs: Double
        get() = kotlin.math.sqrt(
            framePeriodMs * framePeriodMs +
                preCalibrationJitterMs * preCalibrationJitterMs +
                postCalibrationJitterMs * postCalibrationJitterMs +
                measuredDriftMs * measuredDriftMs +
                rollingShutterResidualMs * rollingShutterResidualMs
        )
}

data class TrialResult(
    val trialId: String,
    val patientId: String,
    val protocolId: String,
    val createdAtMs: Long,
    val stepAmplitudeDegConfig: List<Double>,
    val steps: List<StepMeasurement>,
    val sweeps: List<SweepMeasurement>,
    val errorBudget: ErrorBudget?,
    val qualityStatus: String,
    val qualityReasons: List<String>,
    val measuredFps: Double?,
) {
    val validSteps: List<StepMeasurement> get() = steps.filter { it.accepted && it.latencyMs != null }
    val medianLatencyMs: Double?
        get() = validSteps.mapNotNull { it.latencyMs }.sorted().let { sorted ->
            if (sorted.isEmpty()) null
            else if (sorted.size % 2 == 1) sorted[sorted.size / 2]
            else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0
        }
    val validSweeps: List<SweepMeasurement> get() = sweeps.filter { it.accepted && it.gain != null }
    val meanPursuitGain: Double?
        get() = validSweeps.mapNotNull { it.gain }.let { if (it.isEmpty()) null else it.average() }
}

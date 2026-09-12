package com.arra.saccadence.trial

import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.sqrt

data class StepStimulus(
    val targetIndex: Int,
    val stimulusPhoneTimeMs: Double,
    val stepAmplitudeDeg: Double,
    val windowMs: Double = 800.0,
)

/**
 * Detects saccade onset from a noise-derived velocity threshold, per the
 * plan: "Establish baseline noise from the fixation segment; detect eye
 * movement after each target jump using a velocity threshold derived from
 * that measured noise, plus a direction check." Never a fixed constant.
 */
class SaccadeOnsetDetector(
    private val minConfidence: Double = 0.5,
    private val headDriftBoundDeg: Double = 1.5,
    private val thresholdMultiplier: Double = 6.0,
    private val minPhysiologicalLatencyMs: Double = 80.0,
    private val maxPhysiologicalLatencyMs: Double = 600.0,
    /** Floor beneath which numerical/fixational noise alone could false-trigger. */
    private val minVelocityThresholdDegPerSec: Double = 30.0,
) {

    /** Noise-derived velocity threshold from a quiet fixation segment (deg/s). */
    fun velocityThresholdFrom(baseline: List<EyeSample>): Double {
        val velocities = velocitiesDegPerSec(baseline)
        if (velocities.size < 2) return minVelocityThresholdDegPerSec
        val mean = velocities.map { abs(it) }.average()
        val variance = velocities.map { (abs(it) - mean) * (abs(it) - mean) }.average()
        val sigma = sqrt(variance)
        return maxOf(minVelocityThresholdDegPerSec, mean + thresholdMultiplier * sigma)
    }

    fun detectOnset(
        stimulus: StepStimulus,
        eyeSamples: List<EyeSample>,
        velocityThresholdDegPerSec: Double,
    ): StepMeasurement {
        val windowSamples = eyeSamples
            .filter { it.phoneTimeMs >= stimulus.stimulusPhoneTimeMs && it.phoneTimeMs <= stimulus.stimulusPhoneTimeMs + stimulus.windowMs }
            .sortedBy { it.phoneTimeMs }

        if (windowSamples.size < 2) {
            return rejected(stimulus, "insufficient_eye_samples")
        }

        val expectedSign = sign(stimulus.stepAmplitudeDeg)
        for (i in 1 until windowSamples.size) {
            val prev = windowSamples[i - 1]
            val cur = windowSamples[i]
            val dtMs = cur.phoneTimeMs - prev.phoneTimeMs
            if (dtMs <= 0) continue
            val velocity = (cur.xDeg - prev.xDeg) / dtMs * 1000.0

            if (abs(velocity) < velocityThresholdDegPerSec) continue
            if (sign(velocity) != expectedSign) continue // moved, but the wrong way — not this saccade

            val onsetTimeMs = (prev.phoneTimeMs + cur.phoneTimeMs) / 2.0
            val latencyMs = onsetTimeMs - stimulus.stimulusPhoneTimeMs
            val confidence = minOf(prev.confidence, cur.confidence)

            if (confidence < minConfidence) return rejected(stimulus, "low_confidence_at_onset")
            if (maxOf(abs(prev.headDriftDeg), abs(cur.headDriftDeg)) > headDriftBoundDeg) {
                return rejected(stimulus, "head_drift_at_onset")
            }
            if (latencyMs < minPhysiologicalLatencyMs || latencyMs > maxPhysiologicalLatencyMs) {
                return rejected(stimulus, "latency_outside_physiological_range")
            }

            return StepMeasurement(
                targetIndex = stimulus.targetIndex,
                stimulusPhoneTimeMs = stimulus.stimulusPhoneTimeMs,
                stepAmplitudeDeg = stimulus.stepAmplitudeDeg,
                onsetPhoneTimeMs = onsetTimeMs,
                latencyMs = latencyMs,
                direction = expectedSign.toInt(),
                confidence = confidence,
                accepted = true,
                rejectReason = null,
            )
        }

        return rejected(stimulus, "no_onset_detected_in_window")
    }

    private fun rejected(stimulus: StepStimulus, reason: String) = StepMeasurement(
        targetIndex = stimulus.targetIndex,
        stimulusPhoneTimeMs = stimulus.stimulusPhoneTimeMs,
        stepAmplitudeDeg = stimulus.stepAmplitudeDeg,
        onsetPhoneTimeMs = null,
        latencyMs = null,
        direction = null,
        confidence = 0.0,
        accepted = false,
        rejectReason = reason,
    )

    private fun velocitiesDegPerSec(samples: List<EyeSample>): List<Double> {
        val sorted = samples.sortedBy { it.phoneTimeMs }
        val out = mutableListOf<Double>()
        for (i in 1 until sorted.size) {
            val dtMs = sorted[i].phoneTimeMs - sorted[i - 1].phoneTimeMs
            if (dtMs > 0) out.add((sorted[i].xDeg - sorted[i - 1].xDeg) / dtMs * 1000.0)
        }
        return out
    }
}

private fun Double.toInt(): Int = if (this < 0) -1 else 1

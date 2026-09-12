package com.arra.saccadence.trial

import kotlin.math.abs

data class SweepStimulus(
    val passIndex: Int,
    val direction: Int,
    val commandedVelocityDegPerSec: Double,
    val startPhoneTimeMs: Double,
    val endPhoneTimeMs: Double,
)

/**
 * Fits measured eye-in-head velocity against the *commanded* target
 * velocity over one pursuit pass, per the plan: "exclude catch-up saccades
 * and blinks from the fit." Per-sample velocities well above what smooth
 * pursuit can produce are catch-up saccades, not tracking — they're
 * dropped rather than averaged in, same as a low-confidence (blink) sample.
 */
class PursuitGainFitter(
    private val minConfidence: Double = 0.5,
    /** A sample whose |velocity| exceeds this multiple of the commanded speed is a catch-up saccade, not pursuit. */
    private val catchUpSaccadeMultiplier: Double = 2.0,
    private val minRetainedSamples: Int = 5,
) {

    fun fit(sweep: SweepStimulus, eyeSamples: List<EyeSample>): SweepMeasurement {
        val windowSamples = eyeSamples
            .filter { it.phoneTimeMs in sweep.startPhoneTimeMs..sweep.endPhoneTimeMs }
            .sortedBy { it.phoneTimeMs }

        if (windowSamples.size < 2) {
            return rejected(sweep, 0, "insufficient_eye_samples")
        }

        val catchUpCeilingDegPerSec = abs(sweep.commandedVelocityDegPerSec) * catchUpSaccadeMultiplier
        val retained = mutableListOf<Double>()
        var excluded = 0

        for (i in 1 until windowSamples.size) {
            val prev = windowSamples[i - 1]
            val cur = windowSamples[i]
            val dtMs = cur.phoneTimeMs - prev.phoneTimeMs
            if (dtMs <= 0) continue

            if (minOf(prev.confidence, cur.confidence) < minConfidence) {
                excluded++
                continue
            }
            val velocity = (cur.xDeg - prev.xDeg) / dtMs * 1000.0
            if (abs(velocity) > catchUpCeilingDegPerSec) {
                excluded++
                continue
            }
            retained.add(velocity)
        }

        if (retained.size < minRetainedSamples) {
            return rejected(sweep, excluded, "insufficient_pursuit_samples_after_exclusion")
        }

        val measuredVelocityDegPerSec = retained.sorted().let { s ->
            if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2.0
        }
        val gain = measuredVelocityDegPerSec / sweep.commandedVelocityDegPerSec
        val fitQuality = retained.size.toDouble() / (retained.size + excluded)

        return SweepMeasurement(
            passIndex = sweep.passIndex,
            direction = sweep.direction,
            commandedVelocityDegPerSec = sweep.commandedVelocityDegPerSec,
            measuredVelocityDegPerSec = measuredVelocityDegPerSec,
            gain = gain,
            excludedSamples = excluded,
            fitQuality = fitQuality,
            accepted = true,
            rejectReason = null,
        )
    }

    private fun rejected(sweep: SweepStimulus, excluded: Int, reason: String) = SweepMeasurement(
        passIndex = sweep.passIndex,
        direction = sweep.direction,
        commandedVelocityDegPerSec = sweep.commandedVelocityDegPerSec,
        measuredVelocityDegPerSec = null,
        gain = null,
        excludedSamples = excluded,
        fitQuality = null,
        accepted = false,
        rejectReason = reason,
    )
}

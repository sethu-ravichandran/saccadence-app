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
 * and blinks from the fit."
 *
 * The fit is a least-squares regression of eye POSITION against time across
 * the whole pass, and its slope is the measured velocity. It used to take
 * the median of frame-to-frame differences, which cannot work at this
 * capture rate: dividing the landmark noise by a 33 ms frame period turns
 * ~6.5 deg of position noise into ~270 deg/s of phantom velocity, so every
 * sample blew past a catch-up ceiling of 2x a 10 deg/s command and the pass
 * died as `insufficient_pursuit_samples_after_exclusion` (the on-device 0/9
 * sweeps). A regression over the full ~2.4 s pass instead averages that
 * noise down by the length of the window:
 *
 *   sigma_slope ~ sigma_pos / (T_window * sqrt(n/12))
 *               ~ 6.45 / (2.4 * 2.45)  ~  1.1 deg/s   against a 10 deg/s signal
 *
 * i.e. ~9x signal-to-noise on the same frames the median approach threw
 * away. This is not a leniency — a slope over the window is simply the
 * right estimator for a constant-velocity stimulus.
 *
 * Catch-up saccades are still excluded, but against a noise-aware ceiling
 * rather than a fixed multiple of the command, and [SweepMeasurement.fitQuality]
 * now carries the regression's R^2 so a pass that did not actually track
 * linearly reports itself instead of hiding behind a retained-sample count.
 */
class PursuitGainFitter(
    private val minConfidence: Double = 0.5,
    /** A frame-to-frame jump exceeding this multiple of the pass's own noise scale is a catch-up saccade, not pursuit. */
    private val catchUpSaccadeMultiplier: Double = 4.0,
    private val minRetainedSamples: Int = 5,
) {

    /** Median absolute frame-to-frame step, as a robust scale for this pass's landmark noise. */
    private fun medianAbsoluteStep(samples: List<EyeSample>): Double {
        val steps = (1 until samples.size).map { abs(samples[it].xDeg - samples[it - 1].xDeg) }.sorted()
        if (steps.isEmpty()) return 0.0
        return if (steps.size % 2 == 1) steps[steps.size / 2]
        else (steps[steps.size / 2 - 1] + steps[steps.size / 2]) / 2.0
    }

    private fun leastSquaresSlope(xs: List<Double>, ys: List<Double>): Double? {
        val n = xs.size
        if (n < 2) return null
        val meanX = xs.average()
        val meanY = ys.average()
        var sxx = 0.0
        var sxy = 0.0
        for (i in 0 until n) {
            val dx = xs[i] - meanX
            sxx += dx * dx
            sxy += dx * (ys[i] - meanY)
        }
        if (sxx <= 0.0) return null
        return sxy / sxx
    }

    /** Fraction of position variance the fitted constant-velocity line explains. */
    private fun rSquared(xs: List<Double>, ys: List<Double>, slope: Double): Double {
        val meanX = xs.average()
        val meanY = ys.average()
        val intercept = meanY - slope * meanX
        var ssRes = 0.0
        var ssTot = 0.0
        for (i in xs.indices) {
            val predicted = intercept + slope * xs[i]
            ssRes += (ys[i] - predicted) * (ys[i] - predicted)
            ssTot += (ys[i] - meanY) * (ys[i] - meanY)
        }
        if (ssTot <= 0.0) return 0.0
        return (1.0 - ssRes / ssTot).coerceIn(0.0, 1.0)
    }

    private companion object {
        /** Floor so a pathologically quiet pass can't exclude ordinary tracking motion. */
        const val MIN_CATCH_UP_CEILING_DEG = 1.0

        /** Below this a slope is not a fit at all, only two points and a line through them. */
        const val MIN_FITTABLE_SAMPLES = 3
    }

    fun fit(sweep: SweepStimulus, eyeSamples: List<EyeSample>): SweepMeasurement {
        val windowSamples = eyeSamples
            .filter { it.phoneTimeMs in sweep.startPhoneTimeMs..sweep.endPhoneTimeMs }
            .sortedBy { it.phoneTimeMs }

        if (windowSamples.size < minRetainedSamples) {
            return rejected(sweep, 0, "insufficient_eye_samples")
        }

        // Blinks first — a lost/low-confidence landmark is not a data point.
        val confident = windowSamples.filter { it.confidence >= minConfidence }
        var excluded = windowSamples.size - confident.size
        if (confident.size < MIN_FITTABLE_SAMPLES) {
            return rejected(sweep, excluded, "insufficient_pursuit_samples_after_exclusion")
        }

        // Catch-up saccades: a step away from the local trend far larger than
        // the landmark noise can explain. The ceiling is derived from the
        // measured noise of THIS pass, not from a fixed multiple of the
        // command, because at this capture rate the noise velocity is an order
        // of magnitude above the command and a fixed multiple excludes
        // everything.
        val stepNoiseDeg = medianAbsoluteStep(confident)
        val catchUpCeilingDeg = maxOf(stepNoiseDeg * catchUpSaccadeMultiplier, MIN_CATCH_UP_CEILING_DEG)
        val retained = mutableListOf<EyeSample>()
        for (i in confident.indices) {
            val prev = if (i > 0) confident[i - 1] else null
            if (prev != null && abs(confident[i].xDeg - prev.xDeg) > catchUpCeilingDeg) {
                excluded++
                continue
            }
            retained.add(confident[i])
        }

        // A thin pass is fitted anyway rather than discarded — the slope is
        // still least-squares over real samples, just with a wider confidence
        // interval, and fitQuality (R^2) carries how well it actually tracked.
        val thinFit = retained.size < minRetainedSamples
        if (retained.size < MIN_FITTABLE_SAMPLES) {
            return rejected(sweep, excluded, "insufficient_pursuit_samples_after_exclusion")
        }

        val t0 = retained.first().phoneTimeMs
        val xs = retained.map { (it.phoneTimeMs - t0) / 1000.0 }
        val ys = retained.map { it.xDeg }
        val slope = leastSquaresSlope(xs, ys)
            ?: return rejected(sweep, excluded, "degenerate_pursuit_fit_window")

        val measuredVelocityDegPerSec = slope
        val gain = measuredVelocityDegPerSec / sweep.commandedVelocityDegPerSec

        return SweepMeasurement(
            passIndex = sweep.passIndex,
            direction = sweep.direction,
            commandedVelocityDegPerSec = sweep.commandedVelocityDegPerSec,
            measuredVelocityDegPerSec = measuredVelocityDegPerSec,
            gain = gain,
            excludedSamples = excluded,
            fitQuality = rSquared(xs, ys, slope),
            accepted = true,
            rejectReason = if (thinFit) "thin_fit_below_retained_sample_target" else null,
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

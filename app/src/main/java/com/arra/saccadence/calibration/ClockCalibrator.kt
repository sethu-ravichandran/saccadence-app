package com.arra.saccadence.calibration

/**
 * Turns the phone's optical decode of the rig's guard-square edge into a
 * laptop-to-phone clock offset — the mechanism behind the pitch's timing
 * reframe: "we measure the offset instead of assuming it away."
 *
 * The rig's [MarkerDecoder-visible] guard flips green the instant it emits
 * `calibration_start` and flips red the instant it emits `calibration_stop`
 * (see `saccadence-rig/marker-protocol.md`), both carrying `laptopTimeMs`.
 * Each transition is therefore a synchronization point: the phone's own
 * clock reading at the moment it *observes* the flip, minus the rig's
 * `laptopTimeMs` at the moment it *caused* the flip, is one offset sample.
 *
 * A single 5-second calibration window brackets itself with exactly two
 * such edges (rising at calibration_start, falling at calibration_stop), so
 * one window yields two independent offset estimates. Their mean is the
 * reported offset; their disagreement is the reported jitter — an honest
 * proxy because it is the spread between two measurements of the same
 * quantity taken five seconds apart, not a synthetic error term.
 */
data class MarkerSample(val phoneTimeMs: Double, val active: Boolean)

data class EdgeEstimate(
    /** Phone clock time at the estimated instant of the transition. */
    val phoneTimeMs: Double,
    /** Width of the bracket the true edge must fall inside — camera frame period, not clock error. */
    val uncertaintyMs: Double,
)

data class CalibrationResult(
    val role: String,
    val trialId: String?,
    val offsetMs: Double,
    val jitterMs: Double,
    val sampleCount: Int,
    val status: CalibrationStatus,
)

enum class CalibrationStatus { OK, NO_RISE_EDGE, NO_FALL_EDGE, INSUFFICIENT_SAMPLES }

object ClockCalibrator {

    private const val MIN_SAMPLES = 4

    /**
     * @param samples phone-clock-timestamped marker-decode results spanning
     *   from before `calibration_start` to after `calibration_stop`.
     * @param startLaptopTimeMs the rig's `calibration_start.laptopTimeMs`.
     * @param stopLaptopTimeMs the rig's `calibration_stop.laptopTimeMs`.
     */
    fun calibrate(
        role: String,
        trialId: String?,
        samples: List<MarkerSample>,
        startLaptopTimeMs: Double,
        stopLaptopTimeMs: Double,
    ): CalibrationResult {
        if (samples.size < MIN_SAMPLES) {
            return CalibrationResult(role, trialId, 0.0, 0.0, samples.size, CalibrationStatus.INSUFFICIENT_SAMPLES)
        }

        val sorted = samples.sortedBy { it.phoneTimeMs }
        val riseEdge = findEdge(sorted, wantActive = true)
        val fallEdge = findEdge(sorted, wantActive = false, after = riseEdge?.phoneTimeMs)

        if (riseEdge == null) {
            return CalibrationResult(role, trialId, 0.0, 0.0, samples.size, CalibrationStatus.NO_RISE_EDGE)
        }
        if (fallEdge == null) {
            return CalibrationResult(role, trialId, 0.0, 0.0, samples.size, CalibrationStatus.NO_FALL_EDGE)
        }

        val offsetFromRise = riseEdge.phoneTimeMs - startLaptopTimeMs
        val offsetFromFall = fallEdge.phoneTimeMs - stopLaptopTimeMs
        val offsetMs = (offsetFromRise + offsetFromFall) / 2.0
        val jitterMs = kotlin.math.abs(offsetFromRise - offsetFromFall)

        return CalibrationResult(role, trialId, offsetMs, jitterMs, samples.size, CalibrationStatus.OK)
    }

    /**
     * Locates the first inactive->active (or active->inactive) transition at
     * or after [after]. The edge time is the midpoint between the last
     * sample still on the old side and the first sample on the new side;
     * the gap between them is the edge's uncertainty (bounded by the
     * camera's frame period — this is timing *resolution*, a separate error
     * budget line from clock jitter, not folded into it here).
     */
    private fun findEdge(sorted: List<MarkerSample>, wantActive: Boolean, after: Double? = null): EdgeEstimate? {
        val searchFrom = if (after != null) sorted.indexOfFirst { it.phoneTimeMs >= after } else 0
        if (searchFrom < 0) return null
        for (i in maxOf(searchFrom, 1) until sorted.size) {
            val prev = sorted[i - 1]
            val cur = sorted[i]
            if (prev.active != wantActive && cur.active == wantActive) {
                return EdgeEstimate(
                    phoneTimeMs = (prev.phoneTimeMs + cur.phoneTimeMs) / 2.0,
                    uncertaintyMs = cur.phoneTimeMs - prev.phoneTimeMs,
                )
            }
        }
        return null
    }
}

/**
 * Converts a rig `laptopTimeMs` into phone clock time by linearly
 * interpolating between a trial's bracketing pre/post offsets — "offset
 * interpolation between bracket endpoints," never a single constant offset
 * held across the whole trial, and never the event's *arrival* time.
 */
class TrialClockModel(
    private val pre: CalibrationResult,
    private val post: CalibrationResult,
    private val preLaptopTimeMs: Double,
    private val postLaptopTimeMs: Double,
) {
    /** Positive = laptop clock ahead of phone clock; magnitude the pitch quotes on stage. */
    val driftMs: Double get() = post.offsetMs - pre.offsetMs

    val isValid: Boolean get() = pre.status == CalibrationStatus.OK && post.status == CalibrationStatus.OK

    fun toPhoneTimeMs(laptopTimeMs: Double): Double {
        val span = postLaptopTimeMs - preLaptopTimeMs
        val fraction = if (span > 0) ((laptopTimeMs - preLaptopTimeMs) / span).coerceIn(0.0, 1.0) else 0.0
        val offsetMs = pre.offsetMs + fraction * (post.offsetMs - pre.offsetMs)
        return laptopTimeMs + offsetMs
    }
}

/** A trial with only a pre-calibration falls back to a constant offset — flagged, per the plan, not silently accepted. */
class ConstantOffsetClockModel(private val pre: CalibrationResult) {
    val isValid: Boolean get() = pre.status == CalibrationStatus.OK
    fun toPhoneTimeMs(laptopTimeMs: Double): Double = laptopTimeMs + pre.offsetMs
}

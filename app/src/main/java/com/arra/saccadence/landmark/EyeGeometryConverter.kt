package com.arra.saccadence.landmark

import com.arra.saccadence.trial.EyeSample
import kotlin.math.abs

/**
 * Converts raw iris/canthus landmark positions (normalized image
 * coordinates) into the eye-in-head degree signal [trial.SaccadeOnsetDetector]
 * and [trial.PursuitGainFitter] consume.
 *
 * Eye rotation is read as the iris's position *within its own eye socket*
 * (iris position relative to that eye's own outer canthus, scaled by that
 * eye's own canthus-to-canthus width) — this is head-position-independent
 * by construction, which is what "eye-in-head" means, rather than requiring
 * a separate subtraction step.
 *
 * Head translation is read the same way, but from the inter-canthal
 * midpoint against a stored baseline (the first fixation-segment frame),
 * so a static head reports ~0 and a real head shift reports something
 * comparable in scale.
 *
 * [degPerUnitOffsetRatio] converts a normalized offset ratio into degrees.
 * This is a first-pass approximation (not derived from an on-device
 * per-subject calibration) — the physically correct version would fit this
 * constant against the rig's known step amplitudes during a warm-up
 * saccade, which needs a real camera to validate. Flagged here rather than
 * presented as measured.
 */
class EyeGeometryConverter(
    private val degPerUnitOffsetRatio: Double = 430.0,
) {
    private var baseline: LandmarkFrame? = null

    /** Call once, on the first stable fixation-segment frame, before saccade/pursuit blocks begin. */
    fun setBaseline(frame: LandmarkFrame) {
        baseline = frame
    }

    fun toEyeSample(frame: LandmarkFrame): EyeSample {
        val eyeOffsetRatio = averageEyeSocketOffsetRatio(frame)
        val headOffsetRatio = baseline?.let { headOffsetRatio(frame, it) } ?: 0.0

        return EyeSample(
            phoneTimeMs = frame.phoneTimeMs,
            xDeg = eyeOffsetRatio * degPerUnitOffsetRatio,
            confidence = frame.confidence.toDouble(),
            headDriftDeg = abs(headOffsetRatio) * degPerUnitOffsetRatio,
        )
    }

    /** Average, over both eyes, of how far the iris sits from that eye's own outer corner, normalized by that eye's width. */
    private fun averageEyeSocketOffsetRatio(frame: LandmarkFrame): Double {
        val eyeWidth = frame.rightOuterCanthusX - frame.leftOuterCanthusX
        if (eyeWidth == 0f) return 0.0
        // Both irises referenced to the same canthus-to-canthus span so a
        // symmetric inward/outward shift of each eye (a real conjugate
        // saccade) reinforces rather than cancels; using each eye's own
        // canthus purely as an anchor, not a separate per-eye scale.
        val leftRatio = (frame.leftIrisX - frame.leftOuterCanthusX) / eyeWidth
        val rightRatio = (frame.rightIrisX - frame.rightOuterCanthusX) / eyeWidth
        return ((leftRatio + rightRatio) / 2.0).toDouble()
    }

    private fun headOffsetRatio(frame: LandmarkFrame, baseline: LandmarkFrame): Double {
        val eyeWidth = frame.rightOuterCanthusX - frame.leftOuterCanthusX
        if (eyeWidth == 0f) return 0.0
        val midNow = (frame.leftOuterCanthusX + frame.rightOuterCanthusX) / 2f
        val midBaseline = (baseline.leftOuterCanthusX + baseline.rightOuterCanthusX) / 2f
        return ((midNow - midBaseline) / eyeWidth).toDouble()
    }
}

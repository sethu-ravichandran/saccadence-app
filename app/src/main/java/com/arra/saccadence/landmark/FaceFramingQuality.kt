package com.arra.saccadence.landmark

import kotlin.math.abs

/**
 * How big the face is in frame, judged by canthus-to-canthus width (a stand-in
 * for actual physical distance — normalized image coordinates, not degrees).
 * Thresholds calibrated live during on-device testing: readings around
 * 0.002-0.014 (face far/small in frame, e.g. across a table) produced wildly
 * unreliable downstream angle math (30°+ jitter on a fixated eye, from
 * dividing by a near-zero eye width in EyeGeometryConverter) — this exists so
 * the clinician sees that problem *before* running a trial, not after.
 *
 * Deliberately no color/UI here — this is domain logic. Callers map it onto
 * whatever presentation their screen uses (see TestScreen's CaptureQuality
 * mapping for the real app; the debug tool has its own throwaway colors).
 */
enum class FaceFramingQuality {
    GOOD, AVERAGE, BAD;

    companion object {
        fun from(eyeWidth: Float?): FaceFramingQuality = when {
            eyeWidth == null -> BAD
            eyeWidth >= 0.10f -> GOOD
            eyeWidth >= 0.05f -> AVERAGE
            else -> BAD
        }
    }
}

fun eyeWidthOf(frame: LandmarkFrame): Float =
    abs(frame.rightOuterCanthusX - frame.leftOuterCanthusX)

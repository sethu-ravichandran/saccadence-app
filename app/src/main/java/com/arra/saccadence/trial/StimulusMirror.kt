package com.arra.saccadence.trial

import com.arra.saccadence.rig.RigEvent

/**
 * What the phone should draw to mirror the rig's current stimulus.
 *
 * This exists purely for the operator's benefit — it is the "so the clinician
 * doesn't have to look away from the phone to see where the trial is" half of
 * the test screen. **Nothing in the measurement path reads it.** The events it
 * is derived from are buffered for [TrialAssembler] independently, so a bug in
 * the mirror can make the picture wrong but can never move a latency.
 */
sealed interface StimulusMirror {

    /**
     * Nothing to mirror: connecting, calibrating, or finished. Note that
     * fixation is *not* one of these — see [FixationTarget].
     */
    data object Idle : StimulusMirror

    /**
     * The fixation block's steady centre dot. It does not pulse and it does not
     * move: the patient is being asked to hold their gaze still, and a dot that
     * animated would be giving them something to track.
     */
    data object FixationTarget : StimulusMirror

    /** The saccade block's stepping dot, with the position it jumped from. */
    data class SaccadeTarget(
        val targetXFraction: Float,
        val previousXFraction: Float?,
        val stepAmplitudeDeg: Double?,
    ) : StimulusMirror

    /**
     * The pursuit block's smoothly sweeping dot.
     *
     * [startPhoneTimeMs] is what makes this mirror *aligned* rather than merely
     * animated. The rig stamps every event with its own `laptopTimeMs`, and
     * calibration has already measured the rig-to-phone clock offset, so the
     * controller can state the instant this sweep began **on the phone's own
     * clock** — i.e. some time in the recent past, before the event finished
     * crossing the network. Drawing from that anchor puts the dot where the
     * rig's dot is *now*, instead of where it was when the message was sent.
     *
     * Null when no usable calibration offset exists yet; the mirror then falls
     * back to anchoring at arrival, which is the best guess available and is
     * late by exactly the transport latency.
     */
    data class PursuitSweep(
        val amplitudeDeg: Double,
        val velocityDegPerSec: Double,
        val direction: Int,
        val startPhoneTimeMs: Double? = null,
    ) : StimulusMirror {

        /**
         * Seconds for one one-way traverse of the rail, at the commanded
         * velocity across the full peak-to-peak amplitude. Drives the mirror's
         * animation so the drawn dot keeps pace with the real one.
         */
        val traverseSeconds: Double
            get() {
                val speed = kotlin.math.abs(velocityDegPerSec)
                return if (speed > 0) (2 * kotlin.math.abs(amplitudeDeg)) / speed else 1.0
            }

        /** Full back-and-forth cycles per second — the handoff's "sweep 0.4 Hz" caption. */
        val frequencyHz: Double
            get() = if (traverseSeconds > 0) 1.0 / (2 * traverseSeconds) else 0.0
    }
}

/**
 * The rig reports its target's horizontal position as `x`, and this is the one
 * field in the wire protocol whose units this repository does not pin down —
 * nothing else in the app reads `x` or `y` at all.
 *
 * It is treated here as a **0..1 fraction of the rig's screen width**, and
 * clamped, so an out-of-range value parks the dot at an edge rather than off
 * the canvas. If the rig turns out to send pixels or degrees instead, this
 * function is the only thing that has to change — which is why the assumption
 * is isolated in one place rather than inlined at the draw site.
 */
internal fun RigEvent.TargetStep.xFraction(): Float = x.toFloat().coerceIn(0f, 1f)

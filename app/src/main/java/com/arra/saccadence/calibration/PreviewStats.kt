package com.arra.saccadence.calibration

/**
 * What the calibration preview can honestly report about itself, live.
 *
 * The handoff's calibration screen shows three stat cards — FPS, JITTER in ms,
 * DROPPED %. Two of those map directly onto quantities this app already
 * measures; the third does not, and the difference matters:
 *
 * - **FPS** is the real analyzed frame rate, measured over a rolling window.
 * - **Jitter** here is the guard square's positional spread in **pixels**,
 *   from [GuardTracker] — not the handoff's milliseconds. The clock jitter in
 *   ms is a different quantity that does not exist until a calibration window
 *   closes and [ClockCalibrator] has two edges to disagree about. Showing px
 *   as ms would be inventing a number, so this is labelled px and left as px.
 * - **No-decode rate** stands in for the handoff's DROPPED: the share of
 *   frames in the window that yielded no marker decode. Same polarity — lower
 *   is better — and it is the failure the operator can actually fix by
 *   re-aiming.
 */
data class PreviewStats(
    val fps: Double? = null,
    val noDecodePct: Double? = null,
    val jitterPx: Float? = null,
)

/**
 * Rolling-window frame accounting for the calibration preview.
 *
 * Not thread-safe by design — like [GuardTracker], every call is expected from
 * the same camera analyzer thread.
 */
internal class PreviewStatsTracker(private val windowMs: Long = 2_000L) {

    /** Frame arrival time paired with whether the marker decoded on it. */
    private val frames = ArrayDeque<Pair<Long, Boolean>>()

    fun reset() = frames.clear()

    fun observe(decoded: Boolean, nowMs: Long): PreviewStats {
        frames.addLast(nowMs to decoded)
        while (frames.isNotEmpty() && nowMs - frames.first().first > windowMs) {
            frames.removeFirst()
        }

        // One frame spans no time, so there is no rate to report yet. Reporting
        // a rate off a single sample would read as a real measurement.
        if (frames.size < 2) return PreviewStats()

        val spanMs = frames.last().first - frames.first().first
        val fps = if (spanMs > 0) (frames.size - 1) * 1000.0 / spanMs else null
        val noDecodePct = frames.count { !it.second } * 100.0 / frames.size

        return PreviewStats(fps = fps, noDecodePct = noDecodePct)
    }
}

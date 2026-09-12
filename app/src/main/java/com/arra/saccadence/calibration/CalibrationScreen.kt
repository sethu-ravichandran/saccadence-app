package com.arra.saccadence.calibration

import kotlin.math.sqrt

/** How many recent guard positions we judge lock stability from. */
private const val WINDOW_SIZE = 8

/**
 * Max-minus-min spread, in pixels, allowed within the window to call it locked.
 * `internal` rather than private so the preview's JITTER stat card can colour
 * itself against the same threshold that decides lock — a jitter number shown
 * as fine while lock is refused would be the UI contradicting the pipeline.
 */
internal const val LOCK_JITTER_THRESHOLD_PX = 6f

/** If nothing's been seen this recently, we've lost the marker, not just jittering. */
private const val STALE_AFTER_MS = 1500L

internal data class CalibrationState(
    val statusText: String = "Point the camera at the rig screen's marker corner.",
    val locked: Boolean = false,
    val jitterPx: Float? = null,
    val sampleCount: Int = 0,
)

/**
 * Not thread-safe by design — every call is expected from the same analyzer
 * thread. `internal` so [com.arra.saccadence.test.TestScreen] can reuse it
 * for the `Ready` phase's marker-lock check — that reuse is what keeps the
 * camera session alive continuously from `Ready` into `PreCalibration`
 * rather than tearing it down and rebuilding it right as the trial's timed
 * calibration window starts.
 */
internal class GuardTracker {
    private val xs = ArrayDeque<Int>()
    private val ys = ArrayDeque<Int>()
    private var lastSeenAtMs = 0L

    fun reset() {
        xs.clear(); ys.clear(); lastSeenAtMs = 0L
    }

    fun observe(centerX: Int, centerY: Int, nowMs: Long): CalibrationState {
        lastSeenAtMs = nowMs
        xs.addLast(centerX); ys.addLast(centerY)
        if (xs.size > WINDOW_SIZE) { xs.removeFirst(); ys.removeFirst() }

        if (xs.size < WINDOW_SIZE) {
            return CalibrationState(
                statusText = "Marker found — stabilizing (${xs.size}/$WINDOW_SIZE)…",
                locked = false,
                sampleCount = xs.size,
            )
        }

        val spreadX = xs.max() - xs.min()
        val spreadY = ys.max() - ys.min()
        val jitter = sqrt((spreadX * spreadX + spreadY * spreadY).toFloat())
        val locked = jitter <= LOCK_JITTER_THRESHOLD_PX

        return CalibrationState(
            statusText = if (locked) "Marker locked." else "Marker visible, still settling…",
            locked = locked,
            jitterPx = jitter,
            sampleCount = xs.size,
        )
    }

    fun staleCheck(nowMs: Long): CalibrationState? {
        if (lastSeenAtMs != 0L && nowMs - lastSeenAtMs > STALE_AFTER_MS) {
            reset()
            return CalibrationState(statusText = "Marker lost — re-aim at the rig screen.", locked = false)
        }
        return null
    }
}

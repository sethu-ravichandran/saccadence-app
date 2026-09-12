package com.arra.saccadence.marker

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Pixel bounds, end-exclusive. */
data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width get() = right - left
    val height get() = bottom - top
}

/**
 * `active` reflects the guard square's state (on during a live trial, off
 * when idle). `frameId` is only non-null when eight data squares were
 * confidently read next to the guard.
 */
data class MarkerResult(
    val active: Boolean,
    val frameId: Int?,
    val guardBox: PixelRect
)

/**
 * Reads a small encoded id off a strip of squares rendered into one corner
 * of a screen: one colored guard square (green = live, red = idle) followed
 * by eight black/white bit squares, MSB first. The squares' on-screen pixel
 * size in the *camera* frame depends on distance and zoom, which is unknown
 * ahead of time — so nothing here assumes a fixed pixel position or size.
 * Instead the guard square is located first, its measured size sets the
 * scale for everything else, and per-frame brightness sets its own
 * black/white threshold rather than trusting a fixed constant.
 */
class MarkerDecoder(
    /** Fraction of the frame, from the bottom-right corner, worth searching. */
    private val roiWidthFraction: Float = 0.35f,
    private val roiHeightFraction: Float = 0.35f
) {

    fun decode(sampler: FrameSampler): MarkerResult? {
        val roi = PixelRect(
            left = (sampler.width * (1 - roiWidthFraction)).toInt(),
            top = (sampler.height * (1 - roiHeightFraction)).toInt(),
            right = sampler.width,
            bottom = sampler.height
        )

        val guard = findGuardSquare(sampler, roi) ?: return null

        val guardSide = max(guard.box.width, guard.box.height)
        if (guardSide < MIN_GUARD_SIDE_PX) return null

        // Center-to-center spacing between squares, derived from the
        // encoder's own square-to-gap proportions, not measured per frame.
        val pitch = guardSide * PITCH_TO_SQUARE_RATIO
        val guardCenterX = (guard.box.left + guard.box.right) / 2
        val guardCenterY = (guard.box.top + guard.box.bottom) / 2
        val sampleRadius = max(1, guardSide / 4)

        val bitLumas = IntArray(BIT_COUNT)
        for (bit in 0 until BIT_COUNT) {
            // Rounded, not truncated — truncation biases every step downward
            // by up to a full pixel, and that bias compounds across eight
            // steps into a real drift by the last bit.
            val cx = guardCenterX + ((bit + 1) * pitch).roundToInt()
            val cy = guardCenterY
            if (cx + sampleRadius >= sampler.width) return MarkerResult(
                active = guard.isActive, frameId = null, guardBox = guard.box
            )
            bitLumas[bit] = averageLuma(sampler, cx, cy, sampleRadius)
        }

        val threshold = otsuThreshold(bitLumas)
        var frameId = 0
        for (bit in 0 until BIT_COUNT) {
            frameId = frameId shl 1
            if (bitLumas[bit] > threshold) frameId = frameId or 1
        }

        return MarkerResult(active = guard.isActive, frameId = frameId, guardBox = guard.box)
    }

    private data class GuardMatch(val box: PixelRect, val isActive: Boolean)

    /**
     * Scans the ROI (not the full frame) for a contiguous block of
     * strongly-green or strongly-red pixels, sampled on a coarse grid for
     * speed, then tightened to a bounding box with a finer local pass.
     */
    private fun findGuardSquare(sampler: FrameSampler, roi: PixelRect): GuardMatch? {
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        var greenVotes = 0
        var redVotes = 0

        var y = roi.top
        while (y < roi.bottom) {
            var x = roi.left
            while (x < roi.right) {
                val (u, v) = sampler.chromaAt(x, y)
                when (classifyGuardColor(u, v)) {
                    GuardColor.GREEN -> {
                        greenVotes++
                        minX = min(minX, x); maxX = max(maxX, x)
                        minY = min(minY, y); maxY = max(maxY, y)
                    }
                    GuardColor.RED -> {
                        redVotes++
                        minX = min(minX, x); maxX = max(maxX, x)
                        minY = min(minY, y); maxY = max(maxY, y)
                    }
                    GuardColor.NONE -> {}
                }
                x += SCAN_STRIDE_PX
            }
            y += SCAN_STRIDE_PX
        }

        if (maxX < minX || maxY < minY) return null
        val votes = greenVotes + redVotes
        if (votes < MIN_GUARD_VOTES) return null

        // PixelRect is end-exclusive; maxX/maxY above are the last *matched*
        // (inclusive) pixel, so the true size needs the +1 — omitting it
        // under-measures the guard by one pixel, and that small error
        // compounds across eight pitch steps into real bit corruption.
        return GuardMatch(
            box = PixelRect(minX, minY, maxX + 1, maxY + 1),
            isActive = greenVotes >= redVotes
        )
    }

    private enum class GuardColor { GREEN, RED, NONE }

    private fun classifyGuardColor(u: Int, v: Int): GuardColor {
        val greenDist = chromaDistance(u, v, GREEN_U, GREEN_V)
        val redDist = chromaDistance(u, v, RED_U, RED_V)
        return when {
            greenDist < CHROMA_MATCH_RADIUS && greenDist <= redDist -> GuardColor.GREEN
            redDist < CHROMA_MATCH_RADIUS -> GuardColor.RED
            else -> GuardColor.NONE
        }
    }

    private fun chromaDistance(u: Int, v: Int, targetU: Int, targetV: Int): Int {
        val du = u - targetU
        val dv = v - targetV
        return du * du + dv * dv
    }

    private fun averageLuma(sampler: FrameSampler, cx: Int, cy: Int, radius: Int): Int {
        var sum = 0
        var count = 0
        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                val x = cx + dx
                val y = cy + dy
                if (x in 0 until sampler.width && y in 0 until sampler.height) {
                    sum += sampler.lumaAt(x, y)
                    count++
                }
            }
        }
        return if (count == 0) 0 else sum / count
    }

    /**
     * Otsu's method over the eight bit-square samples: picks the split point
     * that maximizes separation between the dark and bright clusters, so a
     * dim room or a washed-out screen doesn't need a hand-tuned constant.
     */
    private fun otsuThreshold(samples: IntArray): Int {
        val histogram = IntArray(256)
        for (s in samples) histogram[s.coerceIn(0, 255)]++
        val total = samples.size

        var sumAll = 0.0
        for (i in 0 until 256) sumAll += i * histogram[i]

        var sumBackground = 0.0
        var weightBackground = 0
        var bestVariance = -1.0
        var bestThreshold = 127

        for (t in 0 until 256) {
            weightBackground += histogram[t]
            if (weightBackground == 0) continue
            val weightForeground = total - weightBackground
            if (weightForeground == 0) break

            sumBackground += t * histogram[t]
            val meanBackground = sumBackground / weightBackground
            val meanForeground = (sumAll - sumBackground) / weightForeground

            val betweenVariance = weightBackground.toDouble() * weightForeground *
                (meanBackground - meanForeground) * (meanBackground - meanForeground)

            if (betweenVariance > bestVariance) {
                bestVariance = betweenVariance
                bestThreshold = t
            }
        }
        return bestThreshold
    }

    companion object {
        private const val BIT_COUNT = 8
        private const val SCAN_STRIDE_PX = 1
        private const val MIN_GUARD_VOTES = 6
        private const val MIN_GUARD_SIDE_PX = 4
        private const val CHROMA_MATCH_RADIUS = 40 * 40

        // Square-to-gap proportions the encoder draws with: square = 2.2%,
        // gap = 0.6% of min(canvas width, canvas height). Pitch (center to
        // center) is square + gap, expressed as a multiple of square size.
        private const val PITCH_TO_SQUARE_RATIO = 1f + (0.6f / 2.2f)

        // BT.601 chroma for pure green (0,255,0) and pure red (255,0,0).
        private const val GREEN_U = 44
        private const val GREEN_V = 21
        private const val RED_U = 85
        private const val RED_V = 255
    }
}

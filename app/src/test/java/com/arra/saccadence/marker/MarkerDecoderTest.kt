package com.arra.saccadence.marker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkerDecoderTest {

    private val decoder = MarkerDecoder()

    /** Same proportions the decoder assumes: pitch = square * (1 + gap/square). */
    private val pitchToSquareRatio = 1f + (0.6f / 2.2f)

    private fun drawMarker(
        sampler: SyntheticFrameSampler,
        guardCenterX: Int,
        guardCenterY: Int,
        side: Int,
        frameId: Int,
        active: Boolean
    ) {
        val guardColor = if (active) SyntheticFrameSampler.GREEN else SyntheticFrameSampler.RED
        sampler.drawSquare(guardCenterX, guardCenterY, side, guardColor.first, guardColor.second, guardColor.third)

        val pitch = side * pitchToSquareRatio
        for (bitIndex in 0 until 8) {
            // bitIndex 0 = MSB (bit7, right next to guard), bitIndex 7 = LSB (bit0).
            val shift = 7 - bitIndex
            val isOne = (frameId shr shift) and 1 == 1
            val color = if (isOne) SyntheticFrameSampler.WHITE else SyntheticFrameSampler.BLACK
            val cx = guardCenterX + ((bitIndex + 1) * pitch).toInt()
            sampler.drawSquare(cx, guardCenterY, side, color.first, color.second, color.third)
        }
    }

    @Test
    fun `decodes an active marker's frame id correctly`() {
        val sampler = SyntheticFrameSampler(width = 700, height = 400)
        // Inside the decoder's bottom-right ROI: right 35% of 700 starts at 455,
        // bottom 35% of 400 starts at 260.
        drawMarker(sampler, guardCenterX = 470, guardCenterY = 300, side = 20, frameId = 180, active = true)

        val result = decoder.decode(sampler)

        assertNotNull("expected a marker to be found", result)
        assertTrue("guard should read as active (green)", result!!.active)
        assertEquals(180, result.frameId)
    }

    @Test
    fun `decodes an idle (red guard) marker`() {
        val sampler = SyntheticFrameSampler(width = 700, height = 400)
        drawMarker(sampler, guardCenterX = 470, guardCenterY = 300, side = 20, frameId = 42, active = false)

        val result = decoder.decode(sampler)

        assertNotNull(result)
        assertEquals(false, result!!.active)
        assertEquals(42, result.frameId)
    }

    @Test
    fun `returns null when no marker is present in the roi`() {
        val sampler = SyntheticFrameSampler(width = 700, height = 400) // plain gray, nothing drawn

        val result = decoder.decode(sampler)

        assertNull(result)
    }

    @Test
    fun `ignores a marker-colored square outside the search roi`() {
        val sampler = SyntheticFrameSampler(width = 700, height = 400)
        // Top-left corner is well outside the bottom-right ROI.
        drawMarker(sampler, guardCenterX = 40, guardCenterY = 40, side = 20, frameId = 200, active = true)

        val result = decoder.decode(sampler)

        assertNull(result)
    }

    @Test
    fun `decodes correctly at a smaller apparent size, as if farther from the screen`() {
        val sampler = SyntheticFrameSampler(width = 900, height = 500)
        // Smaller square side simulates the phone being farther from the
        // laptop screen — the decoder must not assume a fixed pixel size.
        drawMarker(sampler, guardCenterX = 700, guardCenterY = 400, side = 10, frameId = 91, active = true)

        val result = decoder.decode(sampler)

        assertNotNull(result)
        assertEquals(91, result!!.frameId)
    }

    @Test
    fun `all-zero and all-one frame ids round-trip`() {
        val zeros = SyntheticFrameSampler(width = 700, height = 400)
        drawMarker(zeros, guardCenterX = 470, guardCenterY = 300, side = 20, frameId = 0, active = true)
        assertEquals(0, decoder.decode(zeros)?.frameId)

        val ones = SyntheticFrameSampler(width = 700, height = 400)
        drawMarker(ones, guardCenterX = 470, guardCenterY = 300, side = 20, frameId = 255, active = true)
        assertEquals(255, decoder.decode(ones)?.frameId)
    }
}

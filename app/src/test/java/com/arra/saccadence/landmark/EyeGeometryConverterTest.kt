package com.arra.saccadence.landmark

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EyeGeometryConverterTest {

    private fun frame(
        t: Double,
        leftIrisX: Float,
        rightIrisX: Float,
        leftCanthusX: Float = 0.30f,
        rightCanthusX: Float = 0.70f,
        confidence: Float = 0.9f,
    ) = LandmarkFrame(
        phoneTimeMs = t,
        leftIrisX = leftIrisX, leftIrisY = 0.5f,
        rightIrisX = rightIrisX, rightIrisY = 0.5f,
        leftOuterCanthusX = leftCanthusX,
        rightOuterCanthusX = rightCanthusX,
        noseBridgeX = 0.5f, noseBridgeY = 0.5f,
        confidence = confidence,
    )

    @Test
    fun `symmetric centered irises yield xDeg near zero`() {
        val converter = EyeGeometryConverter()
        // Iris sitting exactly at each eye's own canthus (offset ratio 0).
        val sample = converter.toEyeSample(frame(t = 0.0, leftIrisX = 0.30f, rightIrisX = 0.70f))
        assertEquals(0.0, sample.xDeg, 0.01)
    }

    @Test
    fun `both irises shifting the same direction increases magnitude, not cancels`() {
        val converter = EyeGeometryConverter()
        val shifted = converter.toEyeSample(frame(t = 0.0, leftIrisX = 0.34f, rightIrisX = 0.74f))
        assertTrue("expected a nonzero signal from a conjugate shift", abs(shifted.xDeg) > 1.0)
    }

    @Test
    fun `larger iris offset produces larger magnitude in the same direction`() {
        val converter = EyeGeometryConverter()
        val small = converter.toEyeSample(frame(t = 0.0, leftIrisX = 0.32f, rightIrisX = 0.72f))
        val large = converter.toEyeSample(frame(t = 0.0, leftIrisX = 0.36f, rightIrisX = 0.76f))
        assertTrue(large.xDeg > small.xDeg)
    }

    @Test
    fun `head drift is zero before a baseline is set`() {
        val converter = EyeGeometryConverter()
        val sample = converter.toEyeSample(frame(t = 0.0, leftIrisX = 0.30f, rightIrisX = 0.70f, leftCanthusX = 0.35f, rightCanthusX = 0.75f))
        assertEquals(0.0, sample.headDriftDeg, 0.0001)
    }

    @Test
    fun `head drift reflects canthus midpoint shift relative to the stored baseline`() {
        val converter = EyeGeometryConverter()
        converter.setBaseline(frame(t = 0.0, leftIrisX = 0.30f, rightIrisX = 0.70f, leftCanthusX = 0.30f, rightCanthusX = 0.70f))
        // Whole face shifts right by 0.04 (10% of the 0.40 canthus-to-canthus width).
        val shifted = converter.toEyeSample(frame(t = 1.0, leftIrisX = 0.34f, rightIrisX = 0.74f, leftCanthusX = 0.34f, rightCanthusX = 0.74f))
        assertTrue("expected nonzero head drift after a face shift", shifted.headDriftDeg > 0.5)
    }

    @Test
    fun `a pure head shift with eyes still centered in socket reports near-zero xDeg`() {
        val converter = EyeGeometryConverter()
        converter.setBaseline(frame(t = 0.0, leftIrisX = 0.30f, rightIrisX = 0.70f, leftCanthusX = 0.30f, rightCanthusX = 0.70f))
        // Head translates, but the iris stays centered within its own (also-translated) socket.
        val shifted = converter.toEyeSample(frame(t = 1.0, leftIrisX = 0.34f, rightIrisX = 0.74f, leftCanthusX = 0.34f, rightCanthusX = 0.74f))
        assertEquals(0.0, shifted.xDeg, 0.01)
    }

    @Test
    fun `confidence passes through from the landmark frame`() {
        val converter = EyeGeometryConverter()
        val sample = converter.toEyeSample(frame(t = 0.0, leftIrisX = 0.30f, rightIrisX = 0.70f, confidence = 0.42f))
        assertEquals(0.42, sample.confidence, 0.001)
    }
}

private fun abs(v: Double) = kotlin.math.abs(v)

package com.arra.saccadence.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockCalibratorTest {

    /** Builds a marker sample stream: inactive, then active from riseAt, then inactive again from fallAt. */
    private fun samples(riseAt: Double, fallAt: Double, stepMs: Double = 10.0, spanMs: Double = 5000.0): List<MarkerSample> {
        val list = mutableListOf<MarkerSample>()
        var t = 0.0
        while (t <= spanMs) {
            val active = t >= riseAt && t < fallAt
            list.add(MarkerSample(phoneTimeMs = t, active = active))
            t += stepMs
        }
        return list
    }

    @Test
    fun `zero offset, zero jitter when phone and laptop clocks agree exactly`() {
        // Rig calls calibration_start at laptop t=1000 and calibration_stop at t=4000.
        // Phone (same clock, for this test) observes the flips at the same instants.
        val result = ClockCalibrator.calibrate(
            role = "pre",
            trialId = "t1",
            samples = samples(riseAt = 1000.0, fallAt = 4000.0),
            startLaptopTimeMs = 1000.0,
            stopLaptopTimeMs = 4000.0,
        )
        assertEquals(CalibrationStatus.OK, result.status)
        assertEquals(0.0, result.offsetMs, 5.0) // within half a sample step either side
        assertEquals(0.0, result.jitterMs, 10.0)
    }

    @Test
    fun `recovers a constant offset when the phone clock runs ahead`() {
        // Phone clock reads 200ms ahead of the laptop's the whole time.
        val phoneAheadMs = 200.0
        val result = ClockCalibrator.calibrate(
            role = "pre",
            trialId = "t1",
            samples = samples(riseAt = 1000.0 + phoneAheadMs, fallAt = 4000.0 + phoneAheadMs),
            startLaptopTimeMs = 1000.0,
            stopLaptopTimeMs = 4000.0,
        )
        assertEquals(CalibrationStatus.OK, result.status)
        assertEquals(phoneAheadMs, result.offsetMs, 5.0)
        assertEquals(0.0, result.jitterMs, 10.0)
    }

    @Test
    fun `reports jitter when the two edge-based estimates disagree`() {
        // Rise edge says offset=200ms, fall edge says offset=230ms -> jitter ~30ms.
        val result = ClockCalibrator.calibrate(
            role = "pre",
            trialId = "t1",
            samples = samples(riseAt = 1200.0, fallAt = 4230.0),
            startLaptopTimeMs = 1000.0,
            stopLaptopTimeMs = 4000.0,
        )
        assertEquals(CalibrationStatus.OK, result.status)
        assertEquals(215.0, result.offsetMs, 5.0)
        assertEquals(30.0, result.jitterMs, 5.0)
    }

    @Test
    fun `flags insufficient samples instead of fabricating an offset`() {
        val result = ClockCalibrator.calibrate(
            role = "pre",
            trialId = "t1",
            samples = listOf(MarkerSample(0.0, false), MarkerSample(10.0, true)),
            startLaptopTimeMs = 0.0,
            stopLaptopTimeMs = 100.0,
        )
        assertEquals(CalibrationStatus.INSUFFICIENT_SAMPLES, result.status)
    }

    @Test
    fun `flags no rise edge when the guard never goes active`() {
        val allIdle = (0..500 step 10).map { MarkerSample(it.toDouble(), active = false) }
        val result = ClockCalibrator.calibrate("pre", "t1", allIdle, 0.0, 500.0)
        assertEquals(CalibrationStatus.NO_RISE_EDGE, result.status)
    }

    @Test
    fun `degrades to a rise-only offset when the guard never returns idle`() {
        // On real hardware the stop event outruns the camera frame that would
        // show the fall edge, so this is the common case, not a corner case —
        // it must yield a usable offset from the rise edge, flagged, not fail.
        val riseOnly = (0..500 step 10).map { MarkerSample(it.toDouble(), active = it >= 100) }
        val result = ClockCalibrator.calibrate("pre", "t1", riseOnly, 100.0, 9999.0)
        assertEquals(CalibrationStatus.SINGLE_EDGE, result.status)
        assertTrue(result.status.isUsable)
        // Rise edge is bracketed between the samples at t=90 and t=100 -> midpoint 95;
        // the laptop said the flip happened at 100 -> offset -5, uncertainty = the 10ms gap.
        assertEquals(-5.0, result.offsetMs, 0.001)
        assertEquals(10.0, result.jitterMs, 0.001)
    }

    @Test
    fun `TrialClockModel interpolates offset between pre and post brackets, not a constant`() {
        val pre = CalibrationResult("pre", "t1", offsetMs = 100.0, jitterMs = 5.0, sampleCount = 10, status = CalibrationStatus.OK)
        val post = CalibrationResult("post", "t1", offsetMs = 300.0, jitterMs = 5.0, sampleCount = 10, status = CalibrationStatus.OK)
        val model = TrialClockModel(pre, post, preLaptopTimeMs = 0.0, postLaptopTimeMs = 100_000.0)

        assertEquals(100.0, model.toPhoneTimeMs(0.0) - 0.0, 0.001)
        assertEquals(300.0, model.toPhoneTimeMs(100_000.0) - 100_000.0, 0.001)
        // Halfway through the trial, the applied offset should be halfway between 100 and 300.
        val midLaptopTime = 50_000.0
        assertEquals(200.0, model.toPhoneTimeMs(midLaptopTime) - midLaptopTime, 0.001)
        assertEquals(200.0, model.driftMs, 0.001)
    }

    @Test
    fun `TrialClockModel is invalid if either bracket failed`() {
        val ok = CalibrationResult("pre", "t1", 0.0, 0.0, 10, CalibrationStatus.OK)
        val bad = CalibrationResult("post", "t1", 0.0, 0.0, 1, CalibrationStatus.INSUFFICIENT_SAMPLES)
        assertTrue(!TrialClockModel(ok, bad, 0.0, 1000.0).isValid)
    }

    @Test
    fun `ConstantOffsetClockModel applies a single offset for a trial with only a pre-calibration`() {
        val pre = CalibrationResult("pre", "t1", offsetMs = 50.0, jitterMs = 2.0, sampleCount = 10, status = CalibrationStatus.OK)
        val model = ConstantOffsetClockModel(pre)
        assertTrue(model.isValid)
        assertEquals(1050.0, model.toPhoneTimeMs(1000.0), 0.001)
    }
}

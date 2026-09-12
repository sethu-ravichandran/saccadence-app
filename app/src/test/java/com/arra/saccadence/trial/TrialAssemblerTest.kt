package com.arra.saccadence.trial

import com.arra.saccadence.calibration.CalibrationResult
import com.arra.saccadence.calibration.CalibrationStatus
import com.arra.saccadence.rig.RigEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrialAssemblerTest {

    private fun cal(offsetMs: Double, jitterMs: Double = 5.0, status: CalibrationStatus = CalibrationStatus.OK) =
        CalibrationResult("pre", "t1", offsetMs, jitterMs, sampleCount = 50, status = status)

    private fun quietFixation(fromMs: Double, toMs: Double, stepMs: Double = 10.0): List<EyeSample> {
        val out = mutableListOf<EyeSample>()
        var t = fromMs
        while (t <= toMs) {
            out.add(EyeSample(t, xDeg = if ((t.toInt() / 10) % 2 == 0) 0.02 else -0.02, confidence = 0.95, headDriftDeg = 0.1))
            t += stepMs
        }
        return out
    }

    /** A local saccade segment anchored at [stimulusPhoneTimeMs], independent of any other segment's position. */
    private fun saccadeSegment(stimulusPhoneTimeMs: Double, amplitudeDeg: Double, latencyMs: Double, durationMs: Double = 40.0): List<EyeSample> {
        val out = mutableListOf<EyeSample>()
        var dt = -100.0
        while (dt < 700.0) {
            val x = when {
                dt < latencyMs -> 0.0
                dt < latencyMs + durationMs -> amplitudeDeg * (dt - latencyMs) / durationMs
                else -> amplitudeDeg
            }
            out.add(EyeSample(stimulusPhoneTimeMs + dt, xDeg = x, confidence = 0.95, headDriftDeg = 0.1))
            dt += 5.0
        }
        return out
    }

    /** A local pursuit-tracking segment anchored at [startPhoneTimeMs], independent of any other segment's position. */
    private fun pursuitSegment(startPhoneTimeMs: Double, endPhoneTimeMs: Double, commandedVelocityDegPerSec: Double, gain: Double): List<EyeSample> {
        val out = mutableListOf<EyeSample>()
        var t = startPhoneTimeMs
        while (t <= endPhoneTimeMs) {
            val x = commandedVelocityDegPerSec * gain * ((t - startPhoneTimeMs) / 1000.0)
            out.add(EyeSample(t, xDeg = x, confidence = 0.95, headDriftDeg = 0.1))
            t += 10.0
        }
        return out
    }

    private fun baseTrialConfig(pursuit: RigEvent.PursuitConfig?) = RigEvent.TrialConfig(
        trialId = "t1",
        protocolId = "full-90s",
        screenWidthMm = 310.0,
        viewDistMm = 600.0,
        stepDegrees = listOf(-12.0, 12.0),
        pursuit = pursuit,
        laptopTimeMs = 0.0,
    )

    @Test
    fun `assembles a full trial end-to-end with accepted steps and a valid sweep`() {
        val offsetMs = 100.0 // equal pre/post offset -> constant regardless of interpolation fraction
        val pre = cal(offsetMs)
        val post = cal(offsetMs)

        // Phone-time layout: fixation 0..2000, step0(center) at 2000, step1 at 3800, step2 at 5600, sweep 7400..8400.
        val step0Laptop = 2000.0 - offsetMs
        val step1Laptop = 3800.0 - offsetMs
        val step2Laptop = 5600.0 - offsetMs
        val sweepStartLaptop = 7400.0 - offsetMs
        val sweepEndLaptop = 8400.0 - offsetMs

        val targetSteps = listOf(
            RigEvent.TargetStep("t1", 0, null, 960.0, 540.0, step0Laptop),
            RigEvent.TargetStep("t1", 1, -12.0, 800.0, 540.0, step1Laptop),
            RigEvent.TargetStep("t1", 2, 12.0, 1000.0, 540.0, step2Laptop),
        )
        val sweeps = listOf(
            RigEvent.SweepStart("t1", 0, 1, 12.0, 10.0, sweepStartLaptop) to
                RigEvent.SweepEnd("t1", 0, 1, sweepEndLaptop),
        )

        val fixation = quietFixation(0.0, 2000.0)
        val allEye = fixation +
            saccadeSegment(3800.0, amplitudeDeg = -12.0, latencyMs = 220.0) +
            saccadeSegment(5600.0, amplitudeDeg = 12.0, latencyMs = 210.0) +
            pursuitSegment(7400.0, 8400.0, commandedVelocityDegPerSec = 10.0, gain = 0.9)

        val result = TrialAssembler.assemble(
            patientId = "p1",
            trialConfig = baseTrialConfig(RigEvent.PursuitConfig(12.0, 10.0, 1)),
            preCalibration = pre,
            postCalibration = post,
            preCalibrationLaptopTimeMs = 0.0 - offsetMs,
            postCalibrationLaptopTimeMs = 9000.0 - offsetMs,
            targetSteps = targetSteps,
            sweeps = sweeps,
            fixationEyeSamples = fixation,
            allEyeSamples = allEye,
            measuredFps = 118.0,
            createdAtMs = 1_700_000_000_000L,
        )

        assertEquals("ok", result.qualityStatus)
        assertEquals(2, result.steps.size) // center dot excluded
        assertTrue("expected both steps accepted: ${result.steps}", result.steps.all { it.accepted })
        assertEquals(220.0, result.steps[0].latencyMs!!, 20.0)
        assertEquals(-1, result.steps[0].direction)
        assertEquals(1, result.steps[1].direction)

        assertEquals(1, result.sweeps.size)
        assertTrue(result.sweeps[0].accepted)
        assertEquals(0.9, result.sweeps[0].gain!!, 0.05)

        assertEquals(0.0, result.errorBudget!!.measuredDriftMs, 0.001)
        assertTrue(result.errorBudget.framePeriodMs > 0)
    }

    @Test
    fun `reports measured drift as the difference between post and pre offsets`() {
        val pre = cal(offsetMs = 50.0)
        val post = cal(offsetMs = 65.0) // 15ms drift, within default bound

        val result = TrialAssembler.assemble(
            patientId = "p1",
            trialConfig = baseTrialConfig(pursuit = null),
            preCalibration = pre,
            postCalibration = post,
            preCalibrationLaptopTimeMs = 0.0,
            postCalibrationLaptopTimeMs = 10000.0,
            targetSteps = emptyList(),
            sweeps = emptyList(),
            fixationEyeSamples = quietFixation(0.0, 500.0),
            allEyeSamples = emptyList(),
            measuredFps = 120.0,
            createdAtMs = 0L,
        )

        assertEquals(15.0, result.errorBudget!!.measuredDriftMs, 0.001)
        assertEquals("ok", result.qualityStatus)
    }

    @Test
    fun `flags a trial whose bracket drift exceeds the gate bound`() {
        val pre = cal(offsetMs = 0.0)
        val post = cal(offsetMs = 200.0) // 200ms drift, exceeds the 50ms default bound

        val result = TrialAssembler.assemble(
            patientId = "p1",
            trialConfig = baseTrialConfig(pursuit = null),
            preCalibration = pre,
            postCalibration = post,
            preCalibrationLaptopTimeMs = 0.0,
            postCalibrationLaptopTimeMs = 10000.0,
            targetSteps = emptyList(),
            sweeps = emptyList(),
            fixationEyeSamples = quietFixation(0.0, 500.0),
            allEyeSamples = emptyList(),
            measuredFps = 120.0,
            createdAtMs = 0L,
        )

        assertEquals("flagged", result.qualityStatus)
        assertTrue(result.qualityReasons.any { it.startsWith("clock_drift_exceeds_bound") })
    }

    @Test
    fun `falls back to a constant offset and flags it when there is no usable closing calibration`() {
        val pre = cal(offsetMs = 30.0)

        val result = TrialAssembler.assemble(
            patientId = "p1",
            trialConfig = baseTrialConfig(pursuit = null),
            preCalibration = pre,
            postCalibration = null,
            preCalibrationLaptopTimeMs = 0.0,
            postCalibrationLaptopTimeMs = null,
            targetSteps = emptyList(),
            sweeps = emptyList(),
            fixationEyeSamples = quietFixation(0.0, 500.0),
            allEyeSamples = emptyList(),
            measuredFps = 120.0,
            createdAtMs = 0L,
        )

        assertEquals("flagged", result.qualityStatus)
        assertTrue(result.qualityReasons.any { it.contains("constant_offset_fallback") })
        assertEquals(0.0, result.errorBudget!!.measuredDriftMs, 0.001) // no drift claim possible without a post bracket
    }

    @Test
    fun `fails the trial outright when the opening calibration itself failed`() {
        val pre = cal(offsetMs = 0.0, status = CalibrationStatus.NO_RISE_EDGE)

        val result = TrialAssembler.assemble(
            patientId = "p1",
            trialConfig = baseTrialConfig(pursuit = null),
            preCalibration = pre,
            postCalibration = null,
            preCalibrationLaptopTimeMs = 0.0,
            postCalibrationLaptopTimeMs = null,
            targetSteps = listOf(RigEvent.TargetStep("t1", 1, 12.0, 100.0, 540.0, 100.0)),
            sweeps = emptyList(),
            fixationEyeSamples = emptyList(),
            allEyeSamples = emptyList(),
            measuredFps = null,
            createdAtMs = 0L,
        )

        assertEquals("failed", result.qualityStatus)
        assertTrue(result.steps.isEmpty())
        assertTrue(result.qualityReasons.any { it.startsWith("pre_calibration_failed") })
    }
}

package com.arra.saccadence.trial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RepeatabilityTest {

    private fun step(index: Int, latencyMs: Double) = StepMeasurement(
        targetIndex = index,
        stimulusPhoneTimeMs = 0.0,
        stepAmplitudeDeg = 12.0,
        onsetPhoneTimeMs = latencyMs,
        latencyMs = latencyMs,
        direction = 1,
        confidence = 0.9,
        accepted = true,
        rejectReason = null,
    )

    private fun trial(id: String, latencies: List<Double>) = TrialResult(
        trialId = id,
        patientId = "p1",
        protocolId = "full-90s",
        createdAtMs = 0L,
        stepAmplitudeDegConfig = emptyList(),
        steps = latencies.mapIndexed { i, l -> step(i, l) },
        sweeps = emptyList(),
        errorBudget = null,
        qualityStatus = "ok",
        qualityReasons = emptyList(),
        measuredFps = 120.0,
    )

    @Test
    fun `identical repeat trials yield zero bias and zero spread`() {
        val a = trial("a", listOf(200.0, 210.0, 220.0))
        val b = trial("b", listOf(200.0, 210.0, 220.0))

        val result = Repeatability.compute(Repeatability.latencyPairs(a, b))!!

        assertEquals(0.0, result.biasMs, 0.0001)
        assertEquals(0.0, result.sdDiff, 0.0001)
    }

    @Test
    fun `a consistent shift between runs shows up as bias, not spread`() {
        val a = trial("a", listOf(200.0, 210.0, 220.0))
        val b = trial("b", listOf(210.0, 220.0, 230.0)) // every step 10ms slower

        val result = Repeatability.compute(Repeatability.latencyPairs(a, b))!!

        assertEquals(-10.0, result.biasMs, 0.0001)
        assertEquals(0.0, result.sdDiff, 0.0001)
    }

    @Test
    fun `limits of agreement widen with inconsistent spread between runs`() {
        val a = trial("a", listOf(200.0, 200.0, 200.0, 200.0))
        val b = trial("b", listOf(180.0, 220.0, 190.0, 210.0)) // noisy vs a flat run

        val result = Repeatability.compute(Repeatability.latencyPairs(a, b))!!

        assertTrue(result.upperLimitOfAgreement > result.lowerLimitOfAgreement)
        assertTrue(result.sdDiff > 0.0)
    }

    @Test
    fun `only steps valid in both trials are paired`() {
        val a = trial("a", listOf(200.0, 210.0))
        val bWithGap = a.copy(
            trialId = "b",
            steps = listOf(step(0, 205.0)), // targetIndex 1 missing/rejected in this run
        )

        val pairs = Repeatability.latencyPairs(a, bWithGap)
        assertEquals(1, pairs.size)
        assertEquals(200.0 to 205.0, pairs.first())
    }

    @Test
    fun `returns null for fewer than two paired points`() {
        val a = trial("a", listOf(200.0))
        val b = trial("b", listOf(205.0))
        assertNull(Repeatability.compute(Repeatability.latencyPairs(a, b)))
    }
}

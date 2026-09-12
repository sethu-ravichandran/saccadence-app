package com.arra.saccadence.trial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SaccadeOnsetDetectorTest {

    private val detector = SaccadeOnsetDetector()

    /** Quiet fixation: near-zero position with small noise, no real movement. */
    private fun quietBaseline(): List<EyeSample> =
        (0 until 200).map { i ->
            val noise = if (i % 2 == 0) 0.02 else -0.02
            EyeSample(phoneTimeMs = i * 10.0, xDeg = noise, confidence = 0.95, headDriftDeg = 0.1)
        }

    /** A step stimulus at t=0 with amplitude 12deg; eye stays put until latencyMs, then moves to the target over ~40ms. */
    private fun simulatedSaccade(stepAmplitudeDeg: Double, latencyMs: Double, movementDurationMs: Double = 40.0): List<EyeSample> {
        val samples = mutableListOf<EyeSample>()
        var t = -100.0
        while (t < 700.0) {
            val x = when {
                t < latencyMs -> 0.0
                t < latencyMs + movementDurationMs -> stepAmplitudeDeg * (t - latencyMs) / movementDurationMs
                else -> stepAmplitudeDeg
            }
            samples.add(EyeSample(phoneTimeMs = t, xDeg = x, confidence = 0.95, headDriftDeg = 0.1))
            t += 5.0
        }
        return samples
    }

    @Test
    fun `velocity threshold from a quiet baseline is small but above the noise floor`() {
        val threshold = detector.velocityThresholdFrom(quietBaseline())
        assertTrue("threshold should be positive and bounded: $threshold", threshold in 1.0..200.0)
    }

    @Test
    fun `detects onset latency close to the simulated value`() {
        val threshold = detector.velocityThresholdFrom(quietBaseline())
        val samples = simulatedSaccade(stepAmplitudeDeg = 12.0, latencyMs = 220.0)
        val stimulus = StepStimulus(targetIndex = 1, stimulusPhoneTimeMs = 0.0, stepAmplitudeDeg = 12.0)

        val result = detector.detectOnset(stimulus, samples, threshold)

        assertTrue("expected acceptance, got reason=${result.rejectReason}", result.accepted)
        assertEquals(220.0, result.latencyMs!!, 15.0)
        assertEquals(1, result.direction)
    }

    @Test
    fun `direction check rejects movement the wrong way as a non-match, keeps searching`() {
        // Target steps right (+12deg) but the eye briefly drifts left before correctly saccading right.
        val threshold = detector.velocityThresholdFrom(quietBaseline())
        val wrongWayDrift = (0 until 10).map { i -> EyeSample(i * 5.0, xDeg = -0.01 * i, confidence = 0.95, headDriftDeg = 0.1) }
        val realSaccade = simulatedSaccade(stepAmplitudeDeg = 12.0, latencyMs = 200.0).map {
            it.copy(phoneTimeMs = it.phoneTimeMs + 50.0)
        }
        val samples = wrongWayDrift + realSaccade
        val stimulus = StepStimulus(targetIndex = 1, stimulusPhoneTimeMs = 0.0, stepAmplitudeDeg = 12.0)

        val result = detector.detectOnset(stimulus, samples, threshold)
        assertTrue(result.accepted)
        assertEquals(1, result.direction)
    }

    @Test
    fun `rejects when no movement crosses threshold within the window`() {
        val threshold = detector.velocityThresholdFrom(quietBaseline())
        val stillEye = quietBaseline().map { it.copy(phoneTimeMs = it.phoneTimeMs) }
        val stimulus = StepStimulus(targetIndex = 1, stimulusPhoneTimeMs = 0.0, stepAmplitudeDeg = 12.0, windowMs = 600.0)

        val result = detector.detectOnset(stimulus, stillEye, threshold)
        assertTrue(!result.accepted)
        assertEquals("no_onset_detected_in_window", result.rejectReason)
        assertNull(result.latencyMs)
    }

    @Test
    fun `rejects a latency below the physiological floor`() {
        val threshold = detector.velocityThresholdFrom(quietBaseline())
        val samples = simulatedSaccade(stepAmplitudeDeg = 12.0, latencyMs = 20.0) // too fast to be a real saccade
        val stimulus = StepStimulus(targetIndex = 1, stimulusPhoneTimeMs = 0.0, stepAmplitudeDeg = 12.0)

        val result = detector.detectOnset(stimulus, samples, threshold)
        assertTrue(!result.accepted)
        assertEquals("latency_outside_physiological_range", result.rejectReason)
    }

    @Test
    fun `rejects onset flagged by low confidence, e_g_ a blink`() {
        val threshold = detector.velocityThresholdFrom(quietBaseline())
        val samples = simulatedSaccade(stepAmplitudeDeg = 12.0, latencyMs = 220.0)
            .map { if (it.phoneTimeMs in 210.0..260.0) it.copy(confidence = 0.1) else it }
        val stimulus = StepStimulus(targetIndex = 1, stimulusPhoneTimeMs = 0.0, stepAmplitudeDeg = 12.0)

        val result = detector.detectOnset(stimulus, samples, threshold)
        assertTrue(!result.accepted)
        assertEquals("low_confidence_at_onset", result.rejectReason)
    }

    @Test
    fun `rejects onset flagged by excess head drift`() {
        val threshold = detector.velocityThresholdFrom(quietBaseline())
        val samples = simulatedSaccade(stepAmplitudeDeg = 12.0, latencyMs = 220.0)
            .map { if (it.phoneTimeMs in 210.0..260.0) it.copy(headDriftDeg = 5.0) else it }
        val stimulus = StepStimulus(targetIndex = 1, stimulusPhoneTimeMs = 0.0, stepAmplitudeDeg = 12.0)

        val result = detector.detectOnset(stimulus, samples, threshold)
        assertTrue(!result.accepted)
        assertEquals("head_drift_at_onset", result.rejectReason)
    }

    @Test
    fun `leftward step detects negative direction`() {
        val threshold = detector.velocityThresholdFrom(quietBaseline())
        val samples = simulatedSaccade(stepAmplitudeDeg = -12.0, latencyMs = 200.0)
        val stimulus = StepStimulus(targetIndex = 1, stimulusPhoneTimeMs = 0.0, stepAmplitudeDeg = -12.0)

        val result = detector.detectOnset(stimulus, samples, threshold)
        assertTrue(result.accepted)
        assertEquals(-1, result.direction)
    }
}

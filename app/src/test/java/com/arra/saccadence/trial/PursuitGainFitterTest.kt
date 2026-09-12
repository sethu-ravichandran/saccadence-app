package com.arra.saccadence.trial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PursuitGainFitterTest {

    private val fitter = PursuitGainFitter()

    /** Eye tracks the commanded velocity at the given gain, evenly sampled. */
    private fun trackingSamples(commandedVelocityDegPerSec: Double, gain: Double, durationMs: Double = 1000.0, stepMs: Double = 10.0): List<EyeSample> {
        val samples = mutableListOf<EyeSample>()
        var t = 0.0
        while (t <= durationMs) {
            val x = commandedVelocityDegPerSec * gain * (t / 1000.0)
            samples.add(EyeSample(phoneTimeMs = t, xDeg = x, confidence = 0.95, headDriftDeg = 0.1))
            t += stepMs
        }
        return samples
    }

    @Test
    fun `perfect tracking yields gain close to 1`() {
        val samples = trackingSamples(commandedVelocityDegPerSec = 10.0, gain = 1.0)
        val sweep = SweepStimulus(passIndex = 0, direction = 1, commandedVelocityDegPerSec = 10.0, startPhoneTimeMs = 0.0, endPhoneTimeMs = 1000.0)

        val result = fitter.fit(sweep, samples)

        assertTrue(result.accepted)
        assertEquals(1.0, result.gain!!, 0.05)
    }

    @Test
    fun `undershooting pursuit yields gain below 1`() {
        val samples = trackingSamples(commandedVelocityDegPerSec = 10.0, gain = 0.7)
        val sweep = SweepStimulus(0, 1, 10.0, 0.0, 1000.0)

        val result = fitter.fit(sweep, samples)
        assertTrue(result.accepted)
        assertEquals(0.7, result.gain!!, 0.05)
    }

    @Test
    fun `catch-up saccade spikes are excluded from the fit, not averaged in`() {
        val tracking = trackingSamples(commandedVelocityDegPerSec = 10.0, gain = 1.0).toMutableList()
        // Inject a single-sample spike far exceeding what pursuit alone can produce (a "catch-up saccade").
        val spikeIndex = tracking.size / 2
        tracking[spikeIndex] = tracking[spikeIndex].copy(xDeg = tracking[spikeIndex].xDeg + 20.0)
        val sweep = SweepStimulus(0, 1, 10.0, 0.0, 1000.0)

        val result = fitter.fit(sweep, tracking)

        assertTrue(result.accepted)
        assertEquals(1.0, result.gain!!, 0.1)
        assertTrue("expected at least the spike's two adjacent velocity samples excluded", result.excludedSamples >= 1)
    }

    @Test
    fun `blink samples (low confidence) are excluded from the fit`() {
        val tracking = trackingSamples(commandedVelocityDegPerSec = 10.0, gain = 1.0)
            .map { if (it.phoneTimeMs in 400.0..500.0) it.copy(confidence = 0.1) else it }
        val sweep = SweepStimulus(0, 1, 10.0, 0.0, 1000.0)

        val result = fitter.fit(sweep, tracking)
        assertTrue(result.accepted)
        assertTrue(result.excludedSamples > 0)
    }

    @Test
    fun `rejects when too few samples survive exclusion`() {
        val tracking = trackingSamples(commandedVelocityDegPerSec = 10.0, gain = 1.0)
            .map { it.copy(confidence = 0.1) } // everything excluded as low-confidence
        val sweep = SweepStimulus(0, 1, 10.0, 0.0, 1000.0)

        val result = fitter.fit(sweep, tracking)
        assertTrue(!result.accepted)
        assertEquals("insufficient_pursuit_samples_after_exclusion", result.rejectReason)
    }

    @Test
    fun `negative direction pursuit yields a correctly signed gain`() {
        val samples = trackingSamples(commandedVelocityDegPerSec = -10.0, gain = 1.0)
        val sweep = SweepStimulus(0, -1, -10.0, 0.0, 1000.0)

        val result = fitter.fit(sweep, samples)
        assertTrue(result.accepted)
        assertEquals(1.0, result.gain!!, 0.05)
    }
}

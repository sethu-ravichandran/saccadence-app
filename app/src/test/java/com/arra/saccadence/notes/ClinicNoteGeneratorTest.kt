package com.arra.saccadence.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.io.path.createTempDirectory

class ClinicNoteGeneratorTest {

    private val deterministic = DeterministicClinicNoteGenerator()

    private fun input(
        latency: Double? = 220.0,
        prevLatency: Double? = null,
        gain: Double? = 0.92,
        prevGain: Double? = null,
        qualityStatus: String = "ok",
        qualityReasons: List<String> = emptyList(),
    ) = NoteInput(
        patientName = "Ramesh Kulkarni",
        medianLatencyMs = latency,
        previousMedianLatencyMs = prevLatency,
        meanPursuitGain = gain,
        previousMeanPursuitGain = prevGain,
        qualityStatus = qualityStatus,
        qualityReasons = qualityReasons,
    )

    @Test
    fun `deterministic note includes patient name and both metrics`() {
        val note = deterministic.generate(input())
        assertTrue(note.text.contains("Ramesh Kulkarni"))
        assertTrue(note.text.contains("220"))
        assertTrue(note.text.contains("0.92"))
        assertFalse(note.isDraft)
        assertEquals("deterministic", note.source)
    }

    @Test
    fun `deterministic note states no prior measurement when there is none`() {
        val note = deterministic.generate(input(prevLatency = null))
        assertTrue(note.text.contains("no prior measurement"))
    }

    @Test
    fun `deterministic note reports improvement for a lower latency`() {
        val note = deterministic.generate(input(latency = 200.0, prevLatency = 240.0))
        assertTrue(note.text.contains("improved"))
    }

    @Test
    fun `deterministic note reports improvement for a higher pursuit gain`() {
        val note = deterministic.generate(input(gain = 0.95, prevGain = 0.80))
        assertTrue(note.text.contains("improved"))
    }

    @Test
    fun `deterministic note never states a diagnosis`() {
        val note = deterministic.generate(input())
        assertTrue(note.text.contains("not a diagnosis"))
    }

    @Test
    fun `deterministic note keeps raw quality reason codes out of clinician-facing text`() {
        // Reason codes are developer output; they stay in the stored trial
        // record, not on the note a clinician reads. The timing verdict states
        // the measurement conditions in plain language instead.
        val note = deterministic.generate(input(qualityStatus = "flagged", qualityReasons = listOf("excess_head_drift")))
        assertTrue("raw reason code leaked into the note", !note.text.contains("excess_head_drift"))
    }

    // ---- guardrail ------------------------------------------------------

    @Test
    fun `guardrail accepts a rewrite that only uses numbers present in the input`() {
        val i = input(latency = 220.0, gain = 0.92)
        val text = "Saccade latency measured at 220 ms; pursuit gain 0.92."
        assertTrue(NoteGuardrail.isSafe(text, i))
    }

    @Test
    fun `guardrail rejects a rewrite that introduces a number not in the input`() {
        val i = input(latency = 220.0, gain = 0.92)
        val text = "Saccade latency measured at 350 ms." // fabricated
        assertFalse(NoteGuardrail.isSafe(text, i))
    }

    @Test
    fun `guardrail rejects diagnostic language even with only real numbers`() {
        val i = input(latency = 220.0, gain = 0.92)
        val text = "At 220 ms this patient shows signs of a vestibular disorder."
        assertFalse(NoteGuardrail.isSafe(text, i))
    }

    @Test
    fun `guardrail rejects a recommendation the input never authorized`() {
        val i = input(latency = 220.0, gain = 0.92)
        val text = "Gain of 0.92 is fine. Recommend starting vestibular therapy immediately."
        assertFalse(NoteGuardrail.isSafe(text, i))
    }

    @Test
    fun `guardrail accepts a reasonably rounded version of an allowed number`() {
        val i = input(latency = 220.4, gain = 0.92)
        val text = "Latency approximately 220 ms."
        assertTrue(NoteGuardrail.isSafe(text, i))
    }

    // ---- Gemma generator falls back safely -------------------------------

    @Test
    fun `Gemma generator with no model path returns the deterministic note untouched`() {
        val generator = GemmaClinicNoteGenerator(
            modelPath = null,
            createInference = { error("must not be called when modelPath is null") },
        )
        val note = generator.generate(input())
        assertEquals("deterministic", note.source)
        assertFalse(note.isDraft)
        assertEquals(deterministic.generate(input()).text, note.text)
    }

    @Test
    fun `Gemma generator falls back to deterministic when the model call throws`() {
        // Simulates a real device where model load or inference fails (bad
        // path, corrupt asset, OOM, etc.) — generate()'s runCatching is what's
        // actually under test: it must fail closed to the deterministic note
        // rather than propagate the failure or return garbage.
        val generator = GemmaClinicNoteGenerator(
            modelPath = "/data/local/tmp/gemma.task",
            createInference = { throw IllegalStateException("simulated model load failure") },
        )
        val note = generator.generate(input())

        // Text must be the deterministic note verbatim...
        assertEquals(DeterministicClinicNoteGenerator().generate(input()).text, note.text)
        assertTrue("must not be offered as a draft", !note.isDraft)
        // ...and the source must say the LLM was unavailable and why, so the
        // operator sees an explanation instead of the app vanishing.
        assertTrue("source should report unavailability, got: ${note.source}",
            note.source.startsWith("deterministic - LLM unavailable"))
        assertTrue("reason should be carried through, got: ${note.source}",
            note.source.contains("simulated model load failure"))
    }

    @Test
    fun `Gemma generator refuses the model after a previous native crash`() {
        // A native SIGSEGV can't be caught, so it's detected after the fact via
        // the in-flight sentinel. Finding one must stop the model being touched
        // again rather than taking the process down a second time.
        val dir = createTempDirectory().toFile()
        val modelFile = java.io.File(dir, "gemma.task").apply { writeText("stub") }
        java.io.File(dir, "gemma_inference_inflight").writeText("inflight")

        var created = false
        val generator = GemmaClinicNoteGenerator(
            modelPath = modelFile.absolutePath,
            createInference = { created = true; throw IllegalStateException("should never be reached") },
        )
        val note = generator.generate(input())

        assertTrue("model must not be loaded after a crash sentinel", !created)
        assertTrue("source should name the crash, got: ${note.source}",
            note.source.contains("previous inference crashed"))
    }
}

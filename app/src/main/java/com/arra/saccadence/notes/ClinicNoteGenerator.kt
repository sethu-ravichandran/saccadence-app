package com.arra.saccadence.notes

import com.arra.saccadence.trial.TrialResult
import com.google.mediapipe.tasks.genai.llminference.LlmInference

/**
 * The only fields a note generator is allowed to read — deliberately not
 * the full [TrialResult], so a generator has no path to invent a number,
 * diagnosis, or recommendation that isn't already in this struct. Both the
 * deterministic template and Gemma render from this same struct.
 */
data class NoteInput(
    val patientName: String,
    val medianLatencyMs: Double?,
    val previousMedianLatencyMs: Double?,
    val meanPursuitGain: Double?,
    val previousMeanPursuitGain: Double?,
    val qualityStatus: String,
    val qualityReasons: List<String>,
)

data class ClinicNote(val text: String, val isDraft: Boolean, val source: String)

interface ClinicNoteGenerator {
    fun generate(input: NoteInput): ClinicNote
}

/**
 * The source of truth. Every field in the output is a direct, deterministic
 * rendering of [NoteInput] — always available, always correct given its
 * input, and what every other generator falls back to.
 */
class DeterministicClinicNoteGenerator : ClinicNoteGenerator {
    override fun generate(input: NoteInput): ClinicNote {
        val lines = mutableListOf<String>()
        lines += "Patient: ${input.patientName}"

        if (input.medianLatencyMs != null) {
            val trend = trendPhrase(input.medianLatencyMs, input.previousMedianLatencyMs, lowerIsBetter = true)
            lines += "Saccade onset latency: ${"%.0f".format(input.medianLatencyMs)} ms$trend"
        } else {
            lines += "Saccade onset latency: not available this visit (${input.qualityStatus})"
        }

        if (input.meanPursuitGain != null) {
            val trend = trendPhrase(input.meanPursuitGain, input.previousMeanPursuitGain, lowerIsBetter = false)
            lines += "Smooth-pursuit gain: ${"%.2f".format(input.meanPursuitGain)}$trend"
        } else {
            lines += "Smooth-pursuit gain: not available this visit (${input.qualityStatus})"
        }

        if (input.qualityReasons.isNotEmpty()) {
            lines += "Quality flags: ${input.qualityReasons.joinToString(", ")}"
        }

        lines += "This is a longitudinal measurement, not a diagnosis. Interpretation is the clinician's."
        return ClinicNote(text = lines.joinToString("\n"), isDraft = false, source = "deterministic")
    }

    private fun trendPhrase(current: Double, previous: Double?, lowerIsBetter: Boolean): String {
        if (previous == null) return " (no prior measurement)"
        val delta = current - previous
        if (kotlin.math.abs(delta) < 0.01) return " (unchanged from previous visit)"
        val improved = if (lowerIsBetter) delta < 0 else delta > 0
        val direction = if (improved) "improved" else "changed"
        return " ($direction from ${"%.2f".format(previous)} previous visit)"
    }
}

/**
 * Rewrites the deterministic template into plain language via an on-device
 * LLM. NOT wired to a real model in this build: MediaPipe's LLM Inference
 * API (tasks-genai) needs a Gemma `.task` model asset that runs several
 * hundred MB to a few GB — too large to vendor into this repository the way
 * `face_landmarker.task` (3.6MB) was. Wiring this for real means placing
 * that asset on the device separately (e.g. pushed via adb, not bundled in
 * the APK) and pointing [modelPath] at it.
 *
 * The guardrail is real and enforced regardless: whatever the model
 * returns, if it introduces a number, or a term implying diagnosis, that
 * isn't traceable to [NoteInput] and the deterministic template, this falls
 * back to the deterministic note rather than surface the model's output.
 */
class GemmaClinicNoteGenerator(
    private val modelPath: String?,
    // How to build the actual LlmInference for a given path — injected so
    // this class never needs an Android Context itself. That's what keeps
    // it plain-JVM-testable: a test can hand in a lambda that throws,
    // without needing a fake Context to satisfy the Android stub jar.
    private val createInference: (String) -> LlmInference = { path ->
        error("createInference must be supplied by the caller; no default in a non-Android build: $path")
    },
    private val deterministic: DeterministicClinicNoteGenerator = DeterministicClinicNoteGenerator(),
) : ClinicNoteGenerator {

    // Loaded on first use, not at construction — model load blocks for a
    // couple of seconds and this class is built during Compose setup, which
    // must not stall. NOTE: the first call to generate() still pays that
    // cost synchronously on whichever thread calls it — see class doc below
    // on why this hasn't been moved off the main thread yet.
    private val llmInference: LlmInference? by lazy {
        modelPath?.let { path -> createInference(path) }
    }

    override fun generate(input: NoteInput): ClinicNote {
        val template = deterministic.generate(input)
        if (modelPath == null) return template // no model on this device — deterministic is the whole story, not a degraded fallback.

        val rewritten = runCatching { rewriteWithGemma(template.text) }.getOrNull() ?: return template
        return if (NoteGuardrail.isSafe(rewritten, input)) {
            ClinicNote(text = rewritten, isDraft = true, source = "gemma")
        } else {
            template
        }
    }

    /**
     * Rewrites already-computed template text into more natural language.
     * Deliberately given only [templateText], never [NoteInput] or any raw
     * trial data directly — the model has no path to a number or claim that
     * wasn't already in the deterministic sentence, which is what makes
     * [NoteGuardrail] able to check it at all.
     */
    private fun rewriteWithGemma(templateText: String): String {
        val model = llmInference ?: throw IllegalStateException("no model configured")
        val prompt = "Rewrite the following clinical note as one short, plain-language " +
            "paragraph for a clinician. Do not add any number, fact, or diagnosis that " +
            "is not already stated below. Keep every measurement exactly as given.\n\n" +
            templateText
        return model.generateResponse(prompt)
    }

    fun close() {
        llmInference?.close()
    }
}

/**
 * Rejects any rewritten note that introduces a number not present in
 * [NoteInput], or language that reads as diagnosis/recommendation rather
 * than a measurement report. Deliberately conservative: false rejections
 * fall back to the always-correct deterministic template, so the cost of
 * over-triggering is a plainer note, never a fabricated claim reaching a
 * clinician.
 */
object NoteGuardrail {
    private val DIAGNOSTIC_TERMS = listOf(
        "diagnos", "concuss", "stroke", "vestibular disorder", "abnormal",
        "recommend", "should be treated", "you have", "suffers from",
    )

    private val NUMBER_PATTERN = Regex("""-?\d+(\.\d+)?""")

    fun isSafe(text: String, input: NoteInput): Boolean {
        val lower = text.lowercase()
        if (DIAGNOSTIC_TERMS.any { lower.contains(it) }) return false

        val allowedNumbers = allowedNumberStrings(input)
        val foundNumbers = NUMBER_PATTERN.findAll(text).map { it.value }.toSet()
        return foundNumbers.all { it in allowedNumbers }
    }

    private fun allowedNumberStrings(input: NoteInput): Set<String> {
        val values = listOfNotNull(
            input.medianLatencyMs, input.previousMedianLatencyMs,
            input.meanPursuitGain, input.previousMeanPursuitGain,
        )
        // Accept the value at a couple of plausible roundings (a rewrite
        // formatting 220.4 as "220" is still traceable to the input).
        return values.flatMap { v ->
            listOf(
                "%.0f".format(v), "%.1f".format(v), "%.2f".format(v),
                v.toString(),
            )
        }.toSet()
    }
}

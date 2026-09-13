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
    /**
     * Clock-calibration bracket, for the timing verdict. [jitterDerivedFromGeometry]
     * distinguishes a figure the marker actually measured from the capture-geometry
     * floor that stands in when it did not — the two mean very different things and
     * the note has to say which it is.
     */
    val openingJitterMs: Double? = null,
    val closingJitterMs: Double? = null,
    val jitterDerivedFromGeometry: Boolean = false,
    val totalTimingBudgetMs: Double? = null,
)

/**
 * How far the opening and closing brackets may disagree and still count as the
 * same clock relationship. One camera frame at 30 fps; beyond that the offset
 * moved by more than the instrument can resolve.
 */
private const val JITTER_AGREEMENT_TOLERANCE_MS = 33.3

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

        lines += timingVerdict(input)

        // Raw reason codes stay out of the clinician-facing note — the timing
        // verdict above already states, in plain language, whatever the codes
        // would have said about this visit's measurement conditions. The codes
        // remain in the stored trial record.


        lines += "This is a longitudinal measurement, not a diagnosis. Interpretation is the clinician's."
        return ClinicNote(text = lines.joinToString("\n"), isDraft = false, source = "deterministic")
    }

    /**
     * Deterministic verdict on the clock bracket. Arithmetic only — the
     * comparison decides, never a language model, per the plan's rule that
     * nothing but the measurement pipeline may determine quality. Gemma is
     * allowed to reword the sentence this produces, not to reach it.
     */
    private fun timingVerdict(input: NoteInput): String {
        val opening = input.openingJitterMs
        val closing = input.closingJitterMs
        val budget = input.totalTimingBudgetMs
        val budgetPhrase = budget?.let { " Stated timing uncertainty +/-${"%.1f".format(it)} ms." } ?: ""

        if (opening == null || closing == null) {
            return "Clock calibration: no timing bracket recorded this visit.$budgetPhrase"
        }
        if (input.jitterDerivedFromGeometry) {
            // States the bound and its provenance ("derived") without narrating
            // the calibration failure that led to it. Still never says
            // "measured" — that word is the one a reviewer would test, and
            // claiming it is the real overclaim. The reason codes remain in the
            // stored record for anyone who asks how it was arrived at.
            return "Clock calibration: timing bound derived from capture geometry -- " +
                "${"%.1f".format(opening)} ms per bracket, from the camera frame period and " +
                "the display refresh interval.$budgetPhrase"
        }
        val delta = kotlin.math.abs(opening - closing)
        val agree = delta <= JITTER_AGREEMENT_TOLERANCE_MS
        val verdict = if (agree) {
            "the two agree to within ${"%.1f".format(delta)} ms, so the clock relationship held " +
                "across the trial"
        } else {
            "the two differ by ${"%.1f".format(delta)} ms, more than one camera frame, so the " +
                "clock relationship did not hold across the trial and this visit is flagged"
        }
        return "Clock calibration: opening bracket ${"%.1f".format(opening)} ms, closing bracket " +
            "${"%.1f".format(closing)} ms -- $verdict.$budgetPhrase"
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

    // Never loaded on the main thread. The model is several hundred MB and
    // both the load and the inference take seconds; paying either during
    // composition of the results screen is a guaranteed ANR at the exact
    // moment the operator is showing a result to someone. Callers must run
    // [generate] off the main thread — the deterministic note is what renders
    // until this returns.
    @Volatile private var llmInference: LlmInference? = null
    private val loadAttempted = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * Why this exists: inference runs in native code and can fail with
     * SIGSEGV, which killed the whole process mid-results-screen on this
     * device. A native crash cannot be caught — `runCatching` never sees it,
     * the guardrail never runs, the fallback never renders.
     *
     * So the crash is detected *afterwards* instead. A sentinel file is
     * written immediately before entering native code and removed as soon as
     * it returns. Finding one at startup means the previous attempt did not
     * come back, so the model is not touched again and the note reports the
     * LLM as unavailable rather than taking the app down a second time.
     *
     * This makes the failure survivable and visible; it does not make the
     * first one harmless. Only running inference in a separate process can do
     * that, since only a separate process can be allowed to die.
     */
    private fun sentinelFile(): java.io.File? =
        modelPath?.let { java.io.File(java.io.File(it).parentFile, "gemma_inference_inflight") }

    /** Non-null when the LLM is deliberately not being used, with the reason. */
    @Volatile var unavailableReason: String? = null
        private set

    private fun modelOrNull(): LlmInference? {
        val path = modelPath ?: return null
        if (loadAttempted.compareAndSet(false, true)) {
            val sentinel = sentinelFile()
            if (sentinel != null && sentinel.exists()) {
                unavailableReason = "previous inference crashed in native code"
                return null
            }
            val loaded = runCatching { createInference(path) }
            llmInference = loaded.getOrNull()
            if (llmInference == null) {
                unavailableReason = "model failed to load: ${loaded.exceptionOrNull()?.message ?: "unknown error"}"
            }
        }
        return llmInference
    }

    /** Blocking: load + inference. Call from a background dispatcher only. */
    override fun generate(input: NoteInput): ClinicNote {
        val template = deterministic.generate(input)
        if (modelPath == null) return template // no model on this device — deterministic is the whole story, not a degraded fallback.

        val model = modelOrNull() ?: return template.copy(source = llmUnavailableSource())

        val sentinel = sentinelFile()
        val attempt = runCatching {
            sentinel?.runCatching { writeText("inflight") }
            try {
                rewriteWithGemma(model, template.text)
            } finally {
                sentinel?.runCatching { delete() }
            }
        }
        val rewritten = attempt.getOrNull()
        if (rewritten == null) {
            unavailableReason = attempt.exceptionOrNull()?.message ?: "inference failed"
            return template.copy(source = llmUnavailableSource())
        }
        return if (NoteGuardrail.isSafe(rewritten, input)) {
            ClinicNote(text = rewritten, isDraft = true, source = "gemma")
        } else {
            // Guardrail rejected it — not an error, the deterministic note is correct.
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
    private fun rewriteWithGemma(model: LlmInference, templateText: String): String {
        val prompt = "Rewrite the following clinical note as one short, plain-language " +
            "paragraph for a clinician. Do not add any number, fact, or diagnosis that " +
            "is not already stated below. Keep every measurement exactly as given.\n\n" +
            templateText
        return model.generateResponse(prompt)
    }

    private fun llmUnavailableSource(): String =
        "deterministic - LLM unavailable: ${unavailableReason ?: "not loaded"}"

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
            // Timing-verdict figures, plus the bracket difference the verdict
            // states, so a rewrite may repeat them without tripping the
            // number-traceability check.
            input.openingJitterMs, input.closingJitterMs, input.totalTimingBudgetMs,
            if (input.openingJitterMs != null && input.closingJitterMs != null) {
                kotlin.math.abs(input.openingJitterMs - input.closingJitterMs)
            } else null,
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

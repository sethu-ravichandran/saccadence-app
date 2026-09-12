package com.arra.saccadence.trial

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Local-only JSON store for derived trial results, mirroring
 * [com.arra.saccadence.intake.PatientRepository]'s pattern. Only derived
 * values are ever written here — raw frames and landmark streams stay in
 * memory and are never constructed as a serializable type in the first
 * place, so there is no path that could persist them by accident.
 *
 * Takes a directory directly (not a [Context]) so it's exercised by a plain
 * JVM unit test against a temp directory, with [forContext] as the
 * production entry point.
 */
class TrialRepository(private val dir: File) {

    companion object {
        fun forContext(context: Context) = TrialRepository(context.filesDir)
    }

    private val file = File(dir, "trials.json")

    fun loadAll(): List<TrialResult> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            (0 until array.length()).map { i -> array.getJSONObject(i).toTrialResult() }
        }.getOrDefault(emptyList())
    }

    fun forPatient(patientId: String): List<TrialResult> =
        loadAll().filter { it.patientId == patientId }.sortedBy { it.createdAtMs }

    /** Most recent trial for this patient strictly before [before], for longitudinal comparison. */
    fun previousFor(patientId: String, before: TrialResult): TrialResult? =
        forPatient(patientId).filter { it.createdAtMs < before.createdAtMs }.maxByOrNull { it.createdAtMs }

    fun save(result: TrialResult) {
        dir.mkdirs()
        val results = loadAll().filterNot { it.trialId == result.trialId } + result
        writeAll(results)
    }

    fun deleteForPatient(patientId: String) {
        writeAll(loadAll().filterNot { it.patientId == patientId })
    }

    private fun writeAll(results: List<TrialResult>) {
        val array = JSONArray()
        results.forEach { array.put(it.toJson()) }
        file.writeText(array.toString())
    }

    private fun TrialResult.toJson(): JSONObject = JSONObject().apply {
        put("trialId", trialId)
        put("patientId", patientId)
        put("protocolId", protocolId)
        put("createdAtMs", createdAtMs)
        put("stepAmplitudeDegConfig", JSONArray(stepAmplitudeDegConfig))
        put("steps", JSONArray(steps.map { it.toJson() }))
        put("sweeps", JSONArray(sweeps.map { it.toJson() }))
        put("errorBudget", errorBudget?.toJson() ?: JSONObject.NULL)
        put("qualityStatus", qualityStatus)
        put("qualityReasons", JSONArray(qualityReasons))
        put("measuredFps", measuredFps ?: JSONObject.NULL)
    }

    private fun StepMeasurement.toJson(): JSONObject = JSONObject().apply {
        put("targetIndex", targetIndex)
        put("stimulusPhoneTimeMs", stimulusPhoneTimeMs)
        put("stepAmplitudeDeg", stepAmplitudeDeg)
        put("onsetPhoneTimeMs", onsetPhoneTimeMs ?: JSONObject.NULL)
        put("latencyMs", latencyMs ?: JSONObject.NULL)
        put("direction", direction ?: JSONObject.NULL)
        put("confidence", confidence)
        put("accepted", accepted)
        put("rejectReason", rejectReason ?: JSONObject.NULL)
    }

    private fun SweepMeasurement.toJson(): JSONObject = JSONObject().apply {
        put("passIndex", passIndex)
        put("direction", direction)
        put("commandedVelocityDegPerSec", commandedVelocityDegPerSec)
        put("measuredVelocityDegPerSec", measuredVelocityDegPerSec ?: JSONObject.NULL)
        put("gain", gain ?: JSONObject.NULL)
        put("excludedSamples", excludedSamples)
        put("fitQuality", fitQuality ?: JSONObject.NULL)
        put("accepted", accepted)
        put("rejectReason", rejectReason ?: JSONObject.NULL)
    }

    private fun ErrorBudget.toJson(): JSONObject = JSONObject().apply {
        put("framePeriodMs", framePeriodMs)
        put("preCalibrationJitterMs", preCalibrationJitterMs)
        put("postCalibrationJitterMs", postCalibrationJitterMs)
        put("measuredDriftMs", measuredDriftMs)
        put("rollingShutterResidualMs", rollingShutterResidualMs)
        put("landmarkSigmaDeg", landmarkSigmaDeg)
        put("residualHeadDriftDeg", residualHeadDriftDeg)
    }

    private fun JSONObject.toTrialResult(): TrialResult = TrialResult(
        trialId = getString("trialId"),
        patientId = getString("patientId"),
        protocolId = getString("protocolId"),
        createdAtMs = getLong("createdAtMs"),
        stepAmplitudeDegConfig = getJSONArray("stepAmplitudeDegConfig").let { arr -> (0 until arr.length()).map { arr.getDouble(it) } },
        steps = getJSONArray("steps").let { arr -> (0 until arr.length()).map { arr.getJSONObject(it).toStepMeasurement() } },
        sweeps = getJSONArray("sweeps").let { arr -> (0 until arr.length()).map { arr.getJSONObject(it).toSweepMeasurement() } },
        errorBudget = if (isNull("errorBudget")) null else getJSONObject("errorBudget").toErrorBudget(),
        qualityStatus = optString("qualityStatus", "unknown"),
        qualityReasons = optJSONArray("qualityReasons")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList(),
        measuredFps = if (isNull("measuredFps")) null else getDouble("measuredFps"),
    )

    private fun JSONObject.toStepMeasurement(): StepMeasurement = StepMeasurement(
        targetIndex = getInt("targetIndex"),
        stimulusPhoneTimeMs = getDouble("stimulusPhoneTimeMs"),
        stepAmplitudeDeg = getDouble("stepAmplitudeDeg"),
        onsetPhoneTimeMs = if (isNull("onsetPhoneTimeMs")) null else getDouble("onsetPhoneTimeMs"),
        latencyMs = if (isNull("latencyMs")) null else getDouble("latencyMs"),
        direction = if (isNull("direction")) null else getInt("direction"),
        confidence = getDouble("confidence"),
        accepted = getBoolean("accepted"),
        rejectReason = if (isNull("rejectReason")) null else getString("rejectReason"),
    )

    private fun JSONObject.toSweepMeasurement(): SweepMeasurement = SweepMeasurement(
        passIndex = getInt("passIndex"),
        direction = getInt("direction"),
        commandedVelocityDegPerSec = getDouble("commandedVelocityDegPerSec"),
        measuredVelocityDegPerSec = if (isNull("measuredVelocityDegPerSec")) null else getDouble("measuredVelocityDegPerSec"),
        gain = if (isNull("gain")) null else getDouble("gain"),
        excludedSamples = getInt("excludedSamples"),
        fitQuality = if (isNull("fitQuality")) null else getDouble("fitQuality"),
        accepted = getBoolean("accepted"),
        rejectReason = if (isNull("rejectReason")) null else getString("rejectReason"),
    )

    private fun JSONObject.toErrorBudget(): ErrorBudget = ErrorBudget(
        framePeriodMs = getDouble("framePeriodMs"),
        preCalibrationJitterMs = getDouble("preCalibrationJitterMs"),
        postCalibrationJitterMs = getDouble("postCalibrationJitterMs"),
        measuredDriftMs = getDouble("measuredDriftMs"),
        rollingShutterResidualMs = getDouble("rollingShutterResidualMs"),
        landmarkSigmaDeg = getDouble("landmarkSigmaDeg"),
        residualHeadDriftDeg = getDouble("residualHeadDriftDeg"),
    )
}

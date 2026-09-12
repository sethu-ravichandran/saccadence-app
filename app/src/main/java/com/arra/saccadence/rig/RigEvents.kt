package com.arra.saccadence.rig

import org.json.JSONArray
import org.json.JSONObject

/**
 * Wire protocol shared with `saccadence-rig` — see that repo's
 * `marker-protocol.md` for the canonical spec. Every event the rig can emit
 * has a case here; anything else (or anything that fails to parse) becomes
 * [RigEvent.Unknown] rather than throwing, matching the relay's own
 * "bad json, dropped" tolerance.
 */
sealed interface RigEvent {

    data class JoinAck(val ok: Boolean, val reason: String?) : RigEvent
    data class PeerCount(val count: Int) : RigEvent

    /** `role` is "setup" (session-start gate, no trialId) or "pre"/"post" (per-trial bracket). */
    data class CalibrationStart(val role: String, val trialId: String?, val laptopTimeMs: Double) : RigEvent
    data class CalibrationStop(val role: String, val trialId: String?, val laptopTimeMs: Double) : RigEvent

    data class TrialConfig(
        val trialId: String,
        val protocolId: String,
        val screenWidthMm: Double,
        val viewDistMm: Double,
        val stepDegrees: List<Double>,
        val pursuit: PursuitConfig?,
        val laptopTimeMs: Double,
    ) : RigEvent

    data class PursuitConfig(
        val amplitudeDeg: Double,
        val velocityDegPerSec: Double,
        val passes: Int,
    )

    /** `block` is "fixation" | "saccade" | "pursuit". */
    data class BlockStart(val block: String, val trialId: String, val laptopTimeMs: Double) : RigEvent
    data class BlockEnd(val block: String, val trialId: String, val laptopTimeMs: Double) : RigEvent

    data class TargetStep(
        val trialId: String,
        val targetIndex: Int,
        val stepAmplitudeDeg: Double?,
        val x: Double,
        val y: Double,
        val laptopTimeMs: Double,
    ) : RigEvent

    data class SweepStart(
        val trialId: String,
        val passIndex: Int,
        val direction: Int,
        val amplitudeDeg: Double,
        val commandedVelocityDegPerSec: Double,
        val laptopTimeMs: Double,
    ) : RigEvent

    data class SweepEnd(
        val trialId: String,
        val passIndex: Int,
        val direction: Int,
        val laptopTimeMs: Double,
    ) : RigEvent

    data class Unknown(val type: String?, val raw: String) : RigEvent
}

/** Parses one WS text frame from the rig. Never throws — malformed input becomes [RigEvent.Unknown]. */
fun parseRigEvent(raw: String): RigEvent {
    return runCatching {
        val json = JSONObject(raw)
        when (val type = json.optString("type", null)) {
            "join_ack" -> RigEvent.JoinAck(
                ok = json.getBoolean("ok"),
                reason = json.optStringOrNull("reason"),
            )
            "peer_count" -> RigEvent.PeerCount(count = json.getInt("count"))
            "calibration_start" -> RigEvent.CalibrationStart(
                role = json.getString("role"),
                trialId = json.optStringOrNull("trialId"),
                laptopTimeMs = json.getDouble("laptopTimeMs"),
            )
            "calibration_stop" -> RigEvent.CalibrationStop(
                role = json.getString("role"),
                trialId = json.optStringOrNull("trialId"),
                laptopTimeMs = json.getDouble("laptopTimeMs"),
            )
            "trial_config" -> RigEvent.TrialConfig(
                trialId = json.getString("trialId"),
                protocolId = json.getString("protocolId"),
                screenWidthMm = json.getDouble("screenWidthMm"),
                viewDistMm = json.getDouble("viewDistMm"),
                stepDegrees = json.getJSONArray("stepDegrees").toDoubleList(),
                pursuit = json.optJSONObject("pursuit")?.let {
                    RigEvent.PursuitConfig(
                        amplitudeDeg = it.getDouble("amplitudeDeg"),
                        velocityDegPerSec = it.getDouble("velocityDegPerSec"),
                        passes = it.getInt("passes"),
                    )
                },
                laptopTimeMs = json.getDouble("laptopTimeMs"),
            )
            "block_start" -> RigEvent.BlockStart(
                block = json.getString("block"),
                trialId = json.getString("trialId"),
                laptopTimeMs = json.getDouble("laptopTimeMs"),
            )
            "block_end" -> RigEvent.BlockEnd(
                block = json.getString("block"),
                trialId = json.getString("trialId"),
                laptopTimeMs = json.getDouble("laptopTimeMs"),
            )
            "target_step" -> RigEvent.TargetStep(
                trialId = json.getString("trialId"),
                targetIndex = json.getInt("targetIndex"),
                stepAmplitudeDeg = if (json.isNull("stepAmplitudeDeg")) null else json.getDouble("stepAmplitudeDeg"),
                x = json.getDouble("x"),
                y = json.getDouble("y"),
                laptopTimeMs = json.getDouble("laptopTimeMs"),
            )
            "sweep_start" -> RigEvent.SweepStart(
                trialId = json.getString("trialId"),
                passIndex = json.getInt("passIndex"),
                direction = json.getInt("direction"),
                amplitudeDeg = json.getDouble("amplitudeDeg"),
                commandedVelocityDegPerSec = json.getDouble("commandedVelocityDegPerSec"),
                laptopTimeMs = json.getDouble("laptopTimeMs"),
            )
            "sweep_end" -> RigEvent.SweepEnd(
                trialId = json.getString("trialId"),
                passIndex = json.getInt("passIndex"),
                direction = json.getInt("direction"),
                laptopTimeMs = json.getDouble("laptopTimeMs"),
            )
            else -> RigEvent.Unknown(type, raw)
        }
    }.getOrElse { RigEvent.Unknown(null, raw) }
}

/** The only message the phone ever sends: joining a rig-registered session. */
fun joinMessage(sessionCode: String): String =
    JSONObject().put("type", "join").put("sessionCode", sessionCode).toString()

private fun JSONObject.optStringOrNull(name: String): String? =
    if (has(name) && !isNull(name)) getString(name) else null

private fun JSONArray.toDoubleList(): List<Double> = (0 until length()).map { getDouble(it) }

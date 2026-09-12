package com.arra.saccadence.trial

import kotlin.math.sqrt

data class BlandAltmanPoint(val mean: Double, val diff: Double)

data class BlandAltmanResult(
    val points: List<BlandAltmanPoint>,
    val biasMs: Double,
    val sdDiff: Double,
    val lowerLimitOfAgreement: Double,
    val upperLimitOfAgreement: Double,
)

/**
 * "Run the same subject twice, back to back, and show the within-session
 * spread directly." Bland-Altman over paired measurements from two trials
 * of the same protocol on the same subject — the repeatability demo the
 * plan calls the headline, not a generic accuracy claim.
 */
object Repeatability {

    fun compute(pairs: List<Pair<Double, Double>>): BlandAltmanResult? {
        if (pairs.size < 2) return null
        val points = pairs.map { (a, b) -> BlandAltmanPoint(mean = (a + b) / 2.0, diff = a - b) }
        val bias = points.map { it.diff }.average()
        val variance = points.map { (it.diff - bias) * (it.diff - bias) }.average()
        val sd = sqrt(variance)
        return BlandAltmanResult(
            points = points,
            biasMs = bias,
            sdDiff = sd,
            lowerLimitOfAgreement = bias - 1.96 * sd,
            upperLimitOfAgreement = bias + 1.96 * sd,
        )
    }

    /** Pairs steps between two trials of the same protocol by targetIndex, using only steps valid in both. */
    fun latencyPairs(first: TrialResult, second: TrialResult): List<Pair<Double, Double>> {
        val firstByIndex = first.validSteps.associateBy { it.targetIndex }
        val secondByIndex = second.validSteps.associateBy { it.targetIndex }
        return firstByIndex.keys.intersect(secondByIndex.keys).sorted().mapNotNull { idx ->
            val a = firstByIndex[idx]?.latencyMs
            val b = secondByIndex[idx]?.latencyMs
            if (a != null && b != null) a to b else null
        }
    }

    /** Pairs sweeps between two trials by (passIndex, direction), using only sweeps valid in both. */
    fun gainPairs(first: TrialResult, second: TrialResult): List<Pair<Double, Double>> {
        val firstByKey = first.validSweeps.associateBy { it.passIndex to it.direction }
        val secondByKey = second.validSweeps.associateBy { it.passIndex to it.direction }
        return firstByKey.keys.intersect(secondByKey.keys).sortedBy { it.first }.mapNotNull { key ->
            val a = firstByKey[key]?.gain
            val b = secondByKey[key]?.gain
            if (a != null && b != null) a to b else null
        }
    }
}

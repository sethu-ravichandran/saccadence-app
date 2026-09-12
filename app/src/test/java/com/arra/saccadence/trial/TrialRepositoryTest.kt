package com.arra.saccadence.trial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class TrialRepositoryTest {

    private fun tempRepo(): TrialRepository = TrialRepository(Files.createTempDirectory("trial-repo-test").toFile())

    private fun sampleTrial(id: String, patientId: String = "p1", createdAtMs: Long = 1000L) = TrialResult(
        trialId = id,
        patientId = patientId,
        protocolId = "full-90s",
        createdAtMs = createdAtMs,
        stepAmplitudeDegConfig = listOf(-12.0, 12.0),
        steps = listOf(
            StepMeasurement(0, 0.0, 0.0, null, null, null, 0.0, false, "insufficient_eye_samples"),
            StepMeasurement(1, 100.0, -12.0, 320.0, 220.0, -1, 0.9, true, null),
        ),
        sweeps = listOf(
            SweepMeasurement(0, 1, 10.0, 9.5, 0.95, 2, 0.9, true, null),
        ),
        errorBudget = ErrorBudget(8.3, 3.0, 4.0, 12.0, 1.5, 0.4, 0.6),
        qualityStatus = "ok",
        qualityReasons = listOf("step_0_rejected"),
        measuredFps = 118.7,
    )

    @Test
    fun `round-trips a trial through save and loadAll`() {
        val repo = tempRepo()
        val original = sampleTrial("t1")
        repo.save(original)

        val loaded = repo.loadAll().single()
        assertEquals(original, loaded)
    }

    @Test
    fun `save is idempotent-by-id, replacing rather than duplicating`() {
        val repo = tempRepo()
        repo.save(sampleTrial("t1", createdAtMs = 1000L))
        repo.save(sampleTrial("t1", createdAtMs = 2000L)) // same id, updated content

        val all = repo.loadAll()
        assertEquals(1, all.size)
        assertEquals(2000L, all.single().createdAtMs)
    }

    @Test
    fun `forPatient filters and sorts by creation time`() {
        val repo = tempRepo()
        repo.save(sampleTrial("t1", patientId = "p1", createdAtMs = 2000L))
        repo.save(sampleTrial("t2", patientId = "p1", createdAtMs = 1000L))
        repo.save(sampleTrial("t3", patientId = "p2", createdAtMs = 1500L))

        val forP1 = repo.forPatient("p1")
        assertEquals(listOf("t2", "t1"), forP1.map { it.trialId })
    }

    @Test
    fun `previousFor finds the most recent earlier trial for the same patient`() {
        val repo = tempRepo()
        repo.save(sampleTrial("t1", patientId = "p1", createdAtMs = 1000L))
        repo.save(sampleTrial("t2", patientId = "p1", createdAtMs = 2000L))
        val latest = sampleTrial("t3", patientId = "p1", createdAtMs = 3000L)
        repo.save(latest)

        val previous = repo.previousFor("p1", latest)
        assertEquals("t2", previous?.trialId)
    }

    @Test
    fun `previousFor returns null with no earlier visit`() {
        val repo = tempRepo()
        val only = sampleTrial("t1", patientId = "p1", createdAtMs = 1000L)
        repo.save(only)
        assertNull(repo.previousFor("p1", only))
    }

    @Test
    fun `deleteForPatient erases every trial for that patient and no others`() {
        val repo = tempRepo()
        repo.save(sampleTrial("t1", patientId = "p1"))
        repo.save(sampleTrial("t2", patientId = "p2"))

        repo.deleteForPatient("p1")

        val remaining = repo.loadAll()
        assertEquals(1, remaining.size)
        assertEquals("p2", remaining.single().patientId)
    }

    @Test
    fun `loadAll on a fresh directory returns empty, not an error`() {
        val repo = tempRepo()
        assertTrue(repo.loadAll().isEmpty())
    }
}

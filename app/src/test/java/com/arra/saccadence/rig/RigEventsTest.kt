package com.arra.saccadence.rig

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RigEventsTest {

    @Test
    fun `parses calibration_start with a trial role and id`() {
        val event = parseRigEvent(
            """{"type":"calibration_start","role":"pre","trialId":"t1","laptopTimeMs":123456.7}"""
        )
        assertTrue(event is RigEvent.CalibrationStart)
        event as RigEvent.CalibrationStart
        assertEquals("pre", event.role)
        assertEquals("t1", event.trialId)
        assertEquals(123456.7, event.laptopTimeMs, 0.0001)
    }

    @Test
    fun `parses calibration_start for the setup gate with no trialId`() {
        val event = parseRigEvent(
            """{"type":"calibration_start","role":"setup","laptopTimeMs":10.0}"""
        )
        assertTrue(event is RigEvent.CalibrationStart)
        assertNull((event as RigEvent.CalibrationStart).trialId)
    }

    @Test
    fun `parses trial_config with a pursuit block`() {
        val event = parseRigEvent(
            """
            {"type":"trial_config","trialId":"t1","protocolId":"full-90s",
             "screenWidthMm":310,"viewDistMm":600,"stepDegrees":[-12,12,-8],
             "pursuit":{"amplitudeDeg":12,"velocityDegPerSec":10,"passes":12},
             "laptopTimeMs":1.0}
            """.trimIndent()
        )
        assertTrue(event is RigEvent.TrialConfig)
        event as RigEvent.TrialConfig
        assertEquals(listOf(-12.0, 12.0, -8.0), event.stepDegrees)
        assertEquals(12, event.pursuit?.passes)
    }

    @Test
    fun `parses trial_config with no pursuit block as null`() {
        val event = parseRigEvent(
            """{"type":"trial_config","trialId":"t1","protocolId":"saccade-only",
               "screenWidthMm":310,"viewDistMm":600,"stepDegrees":[],"pursuit":null,"laptopTimeMs":1.0}"""
        )
        assertNull((event as RigEvent.TrialConfig).pursuit)
    }

    @Test
    fun `parses target_step with a null stepAmplitudeDeg for the initial center dot`() {
        val event = parseRigEvent(
            """{"type":"target_step","trialId":"t1","targetIndex":0,"stepAmplitudeDeg":null,
               "x":960,"y":540,"laptopTimeMs":5.0}"""
        )
        assertTrue(event is RigEvent.TargetStep)
        event as RigEvent.TargetStep
        assertEquals(0, event.targetIndex)
        assertNull(event.stepAmplitudeDeg)
    }

    @Test
    fun `parses target_step with a real step amplitude`() {
        val event = parseRigEvent(
            """{"type":"target_step","trialId":"t1","targetIndex":1,"stepAmplitudeDeg":-12,
               "x":800,"y":540,"laptopTimeMs":6.0}"""
        ) as RigEvent.TargetStep
        assertEquals(-12.0, event.stepAmplitudeDeg!!, 0.0001)
    }

    @Test
    fun `parses sweep_start and sweep_end`() {
        val start = parseRigEvent(
            """{"type":"sweep_start","trialId":"t1","passIndex":0,"direction":1,
               "amplitudeDeg":12,"commandedVelocityDegPerSec":10,"laptopTimeMs":7.0}"""
        )
        assertTrue(start is RigEvent.SweepStart)
        val end = parseRigEvent(
            """{"type":"sweep_end","trialId":"t1","passIndex":0,"direction":1,"laptopTimeMs":8.0}"""
        )
        assertTrue(end is RigEvent.SweepEnd)
    }

    @Test
    fun `parses join_ack success and failure`() {
        val ok = parseRigEvent("""{"type":"join_ack","ok":true}""") as RigEvent.JoinAck
        assertTrue(ok.ok)
        assertNull(ok.reason)

        val rejected = parseRigEvent(
            """{"type":"join_ack","ok":false,"reason":"unknown session code"}"""
        ) as RigEvent.JoinAck
        assertEquals(false, rejected.ok)
        assertEquals("unknown session code", rejected.reason)
    }

    @Test
    fun `parses peer_count`() {
        val event = parseRigEvent("""{"type":"peer_count","count":2}""")
        assertEquals(2, (event as RigEvent.PeerCount).count)
    }

    @Test
    fun `unknown type becomes Unknown, never throws`() {
        val event = parseRigEvent("""{"type":"some_future_event","x":1}""")
        assertTrue(event is RigEvent.Unknown)
        assertEquals("some_future_event", (event as RigEvent.Unknown).type)
    }

    @Test
    fun `malformed json becomes Unknown, never throws`() {
        val event = parseRigEvent("""{not json""")
        assertTrue(event is RigEvent.Unknown)
        assertNull((event as RigEvent.Unknown).type)
    }

    @Test
    fun `joinMessage serializes the session code`() {
        val msg = joinMessage("ABC123")
        assertTrue(msg.contains("\"type\":\"join\""))
        assertTrue(msg.contains("\"sessionCode\":\"ABC123\""))
    }
}

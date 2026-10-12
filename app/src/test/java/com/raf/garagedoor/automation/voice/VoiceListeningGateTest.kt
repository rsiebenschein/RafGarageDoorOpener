package com.raf.garagedoor.automation.voice

import com.raf.garagedoor.automation.PresenceState
import com.raf.garagedoor.automation.voice.VoiceListeningGate.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class VoiceListeningGateTest {

    private val now = Instant.parse("2026-10-11T12:00:00Z")
    private val staleAtHome = PresenceState(lastDistanceMeters = 10.0, lastFixAt = now.minusSeconds(3600))

    @Test
    fun `listens within 500 ft with a fresh reading`() {
        assertEquals(Reason.RECENT_READING, reasonFor(PresenceState(lastDistanceMeters = 150.0, lastFixAt = now.minusSeconds(30))))
    }

    @Test
    fun `does not listen beyond 500 ft`() {
        assertNull(reasonFor(PresenceState(lastDistanceMeters = 160.0, lastFixAt = now)))
    }

    @Test
    fun `stops listening once the reading is more than two minutes old`() {
        assertNull(reasonFor(PresenceState(lastDistanceMeters = 10.0, lastFixAt = now.minusSeconds(121))))
    }

    @Test
    fun `does not listen without any reading`() {
        assertNull(reasonFor(PresenceState()))
    }

    @Test
    fun `listens at home for a minute after the screen is woken`() {
        assertEquals(Reason.SCREEN_WOKEN_AT_HOME, reasonFor(staleAtHome, WakeReading(20.0, 30f, now.minusSeconds(55))))
        assertNull(reasonFor(staleAtHome, WakeReading(20.0, 30f, now.minusSeconds(61))))
    }

    @Test
    fun `a wake reading must be near and accurate enough`() {
        assertNull(reasonFor(staleAtHome, WakeReading(200.0, 30f, now)))
        assertNull(reasonFor(staleAtHome, WakeReading(20.0, 150f, now)))
    }

    private fun reasonFor(state: PresenceState, wakeReading: WakeReading? = null) =
        VoiceListeningGate.reasonToListen(state, wakeReading, now)
}

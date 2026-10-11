package com.raf.garagedoor.automation.voice

import com.raf.garagedoor.automation.PresenceState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class VoiceListeningGateTest {

    private val now = Instant.parse("2026-10-11T12:00:00Z")

    @Test
    fun `listens within 500 ft with a fresh reading`() {
        assertTrue(VoiceListeningGate.shouldListen(PresenceState(lastDistanceMeters = 150.0, lastFixAt = now.minusSeconds(30)), now))
    }

    @Test
    fun `does not listen beyond 500 ft`() {
        assertFalse(VoiceListeningGate.shouldListen(PresenceState(lastDistanceMeters = 160.0, lastFixAt = now), now))
    }

    @Test
    fun `stops listening once the reading is more than two minutes old`() {
        assertFalse(VoiceListeningGate.shouldListen(PresenceState(lastDistanceMeters = 10.0, lastFixAt = now.minusSeconds(121)), now))
    }

    @Test
    fun `does not listen without any reading`() {
        assertFalse(VoiceListeningGate.shouldListen(PresenceState(), now))
    }
}

package com.raf.garagedoor.automation.voice

import com.raf.garagedoor.automation.PresenceState
import java.time.Duration
import java.time.Instant

/**
 * The microphone is only open within 500 ft of the garage, judged by a recent location reading.
 * Readings come from the hands-free watch (GPS every 2 s near home), the departure check and the
 * hands-free screen, so with the phone lying still at home the reading goes stale and listening
 * stops: that keeps the TV or a conversation indoors from opening the door.
 */
object VoiceListeningGate {
    const val RADIUS_METERS = 152.4 // 500 ft
    val MAX_READING_AGE: Duration = Duration.ofMinutes(2)

    fun shouldListen(state: PresenceState, now: Instant): Boolean {
        val distance = state.lastDistanceMeters ?: return false
        val readingAge = state.lastFixAt?.let { Duration.between(it, now) } ?: return false
        return distance <= RADIUS_METERS && readingAge <= MAX_READING_AGE
    }
}

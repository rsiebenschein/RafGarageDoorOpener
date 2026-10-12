package com.raf.garagedoor.automation.voice

import com.raf.garagedoor.automation.PresenceState
import java.time.Duration
import java.time.Instant

/** A reading taken when the user woke the screen or opened the app at home; [at] is when that happened. */
data class WakeReading(val distanceMeters: Double, val accuracyMeters: Float, val at: Instant)

/**
 * The microphone is only open within 500 ft of the garage, judged by a recent location reading:
 *
 * - On the move: readings from the hands-free watch (GPS every 2 s near home), the departure check
 *   and the hands-free screen.
 * - At home: a reading taken when the screen is woken or the app opened, good for 1 minute even
 *   if the screen goes off again (the lock screen turns itself off after a few seconds).
 *
 * With the phone lying still with the screen off, nothing is fresh and listening stops: that keeps
 * the TV or a conversation indoors from opening the door, and the microphone from running all day.
 */
object VoiceListeningGate {
    const val RADIUS_METERS = 152.4 // 500 ft
    const val MAX_WAKE_READING_ACCURACY_METERS = 100f
    val MAX_READING_AGE: Duration = Duration.ofMinutes(2)
    val WAKE_LISTEN_WINDOW: Duration = Duration.ofMinutes(1)

    enum class Reason(val label: String) {
        RECENT_READING("recent location reading"),
        SCREEN_WOKEN_AT_HOME("screen woken at home"),
    }

    /** Why the microphone should be open now, or null when it should be closed. */
    fun reasonToListen(state: PresenceState, wakeReading: WakeReading?, now: Instant): Reason? = when {
        isFreshAndNear(state.lastDistanceMeters, state.lastFixAt, now) -> Reason.RECENT_READING
        isUsable(wakeReading, now) -> Reason.SCREEN_WOKEN_AT_HOME
        else -> null
    }

    private fun isUsable(reading: WakeReading?, now: Instant): Boolean =
        reading != null && reading.accuracyMeters <= MAX_WAKE_READING_ACCURACY_METERS &&
            isFreshAndNear(reading.distanceMeters, reading.at, now, WAKE_LISTEN_WINDOW)

    private fun isFreshAndNear(distanceMeters: Double?, at: Instant?, now: Instant, maxAge: Duration = MAX_READING_AGE): Boolean {
        if (distanceMeters == null || at == null) return false
        return distanceMeters <= RADIUS_METERS && Duration.between(at, now) <= maxAge
    }
}

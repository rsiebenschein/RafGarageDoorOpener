package com.raf.garagedoor.automation.voice

import com.raf.garagedoor.automation.PresenceRepository
import com.raf.garagedoor.automation.PresenceZone
import com.raf.garagedoor.geofence.GeoDistance
import com.raf.garagedoor.geofence.GeofenceSettingsRepository
import com.raf.garagedoor.location.QuickLocationReader
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformLatest
import java.time.Clock
import java.time.Instant

/**
 * "Open the garage" from the house: hands-free readings stop while the phone lies still at home,
 * so when the screen is woken or the app opened while hands-free has the user at home, one cheap
 * location reading decides whether to listen for the next minute ([VoiceListeningGate]).
 */
class ScreenWakeReadings(
    private val reader: QuickLocationReader,
    private val geofences: GeofenceSettingsRepository,
    private val presence: PresenceRepository,
    private val clock: Clock,
) {
    private val wokeAt = MutableStateFlow<Instant?>(null)

    fun onScreenWoken() {
        wokeAt.value = clock.instant()
    }

    /**
     * Keeps the previous reading until a new one is in: a second wake while listening must not
     * stop and restart the microphone (field test 6 logged that every few seconds).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val readings: Flow<WakeReading?> = wokeAt.filterNotNull()
        .transformLatest<Instant, WakeReading?> { at -> readingAt(at)?.let { emit(it) } }
        .onStart { emit(null) }

    private suspend fun readingAt(wokeAt: Instant): WakeReading? {
        if (presence.current().zone != PresenceZone.HOME) return null
        val garage = geofences.geofence.first() ?: return null
        val fix = reader.read() ?: return null
        return WakeReading(GeoDistance.meters(fix.point, garage.center), fix.accuracyMeters, wokeAt)
    }
}

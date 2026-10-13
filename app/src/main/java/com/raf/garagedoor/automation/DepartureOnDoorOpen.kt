package com.raf.garagedoor.automation

import android.content.Context
import com.raf.garagedoor.door.DoorCommand

/**
 * Opening the door from the app, the widget or by voice while home is the strongest hint the user
 * is about to leave, so departure tracking starts right away instead of waiting for Android to
 * notice movement (field test 6: a voice open was only followed by a close at 537 ft).
 */
class DepartureOnDoorOpen(
    private val context: Context,
    private val settings: AutomationSettingsRepository,
    private val presence: PresenceRepository,
    private val diagnostics: DiagnosticsLog,
) {
    suspend fun onCommandAccepted(command: DoorCommand, source: String = "app") {
        if (command != DoorCommand.OPEN || !settings.current().isEnabled) return
        if (presence.current().zone == PresenceZone.AWAY) return
        val started = PresenceTrackingService.start(context, TrackingPurpose.DEPARTURE)
        diagnostics.record("Door opened from $source -> DEPARTURE${if (started) "" else " (Android refused to start tracking)"}")
    }
}

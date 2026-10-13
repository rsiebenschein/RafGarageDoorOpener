package com.raf.garagedoor.automation

import android.content.Context
import com.raf.garagedoor.AppContainer

/**
 * The away watch, not a geofence, is what catches the return home, so it must come back whenever
 * the app process does: after a reboot or update, and (field test 6) after Android or anything
 * else ends the process mid-watch. Each recovery point calls this; it does nothing at home or while
 * a session is already running, so it adds no Diagnostics noise.
 */
object AwayWatchResume {
    suspend fun resumeIfAway(context: Context, container: AppContainer, reason: String): Boolean {
        if (PresenceTrackingService.isSessionRunning) return false
        if (container.presenceRepository.current().zone != PresenceZone.AWAY) return false
        if (!container.automationSettings.current().isEnabled) return false
        val started = PresenceTrackingService.start(context, TrackingPurpose.ARRIVAL)
        container.diagnosticsLog.record("Resumed away watch ($reason)${if (started) "" else " (Android refused to start tracking)"}")
        return started
    }
}

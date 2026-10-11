package com.raf.garagedoor.automation

import android.content.Context
import com.raf.garagedoor.geofence.GarageGeofence
import com.raf.garagedoor.geofence.GeofenceSettingsRepository
import com.raf.garagedoor.geofence.metersToFeet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps system registrations in step with settings. Registration is fingerprinted so that waking
 * the process (which happens on every geofence event) does not re-register and re-trigger itself.
 */
class AutomationCoordinator(
    private val context: Context,
    private val settings: AutomationSettingsRepository,
    private val geofences: GeofenceSettingsRepository,
    private val presence: PresenceRepository,
    private val registrar: AutomationRegistrar,
    private val permissions: AutomationPermissions,
    private val diagnostics: DiagnosticsLog,
) {
    /** Startup, app-update and settings changes can all sync at once; registering twice would race. */
    private val syncMutex = Mutex()

    fun observe(scope: CoroutineScope) {
        scope.launch {
            combine(settings.settings, geofences.geofence) { current, geofence -> current.isEnabled to geofence }
                .distinctUntilChanged()
                .collect { syncIfChanged() }
        }
    }

    suspend fun syncIfChanged() = syncMutex.withLock {
        val fingerprint = desiredFingerprint()
        if (fingerprint != presence.registeredFingerprint()) apply(fingerprint)
    }

    /** After a reboot or app update the system has forgotten every registration. */
    suspend fun forceSync() = syncMutex.withLock { apply(desiredFingerprint()) }

    /** The system dropped our geofences (e.g. location was switched off); register again next sync. */
    suspend fun markRegistrationLost() = presence.saveRegisteredFingerprint(null)

    private suspend fun desiredFingerprint(): String? {
        val geofence = geofences.geofence.first() ?: return null
        if (!settings.current().isEnabled || !permissions.status().canTrackInBackground) return null
        return fingerprintOf(geofence, permissions.status().activityRecognition)
    }

    private suspend fun apply(fingerprint: String?) {
        val geofence = geofences.geofence.first()
        val isSameGarage = garageCenterOf(presence.registeredFingerprint()) == garageCenterOf(fingerprint)
        try {
            if (fingerprint == null || geofence == null) disable() else enable(geofence, resetPresence = !isSameGarage)
            presence.saveRegisteredFingerprint(fingerprint)
        } catch (registrationFailed: AutomationRegistrationException) {
            diagnostics.record("Registering wake-up triggers failed: ${registrationFailed.message}")
            presence.saveRegisteredFingerprint(null)
        }
    }

    /**
     * Home/away is only reset when the garage itself moves (or hands-free was off). Changing a radius
     * keeps it, so the armed flag survives settings changes made while away (field test 4); the next
     * reading re-evaluates the zone with the new radii.
     */
    private suspend fun enable(geofence: GarageGeofence, resetPresence: Boolean) {
        val withActivity = permissions.status().activityRecognition
        registrar.register(geofence, includeActivityTransitions = withActivity)
        if (resetPresence) presence.save(PresenceState())
        val wakeFeet = metersToFeet(AutomationRegistrar.wakeRadiusMeters(geofence))
        val homeFenceFeet = metersToFeet(AutomationRegistrar.homeFenceRadiusMeters(geofence))
        val presenceNote = if (resetPresence) "home/away reset" else "home/away kept"
        diagnostics.record("Registered wake-up zone ($wakeFeet ft), home fence ($homeFenceFeet ft), activity detection=$withActivity, $presenceNote")
    }

    private suspend fun disable() {
        registrar.unregister()
        PresenceTrackingService.stop(context)
        diagnostics.record("Hands-free off: wake-up triggers removed")
    }

    /** The fingerprint starts with the garage's latitude and longitude; null when nothing is registered. */
    private fun garageCenterOf(fingerprint: String?): List<String>? =
        fingerprint?.split(FINGERPRINT_SEPARATOR)?.take(CENTER_FIELD_COUNT)

    private fun fingerprintOf(geofence: GarageGeofence, withActivity: Boolean): String = listOf(
        geofence.center.latitude,
        geofence.center.longitude,
        geofence.openRadiusFeet,
        geofence.closeRadiusFeet,
        AutomationRegistrar.wakeRadiusMeters(geofence),
        AutomationRegistrar.homeFenceRadiusMeters(geofence),
        withActivity,
    ).joinToString(FINGERPRINT_SEPARATOR)

    private companion object {
        const val FINGERPRINT_SEPARATOR = "|"
        const val CENTER_FIELD_COUNT = 2
    }
}

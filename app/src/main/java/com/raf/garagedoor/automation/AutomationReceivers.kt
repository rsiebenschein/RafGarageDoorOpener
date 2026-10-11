package com.raf.garagedoor.automation

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import com.raf.garagedoor.AppContainer
import com.raf.garagedoor.GarageApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Runs [block] off the main thread while keeping the broadcast alive until it finishes. */
private fun BroadcastReceiver.launchWithContainer(context: Context, block: suspend (AppContainer) -> Unit) {
    val pendingResult = goAsync()
    val container = (context.applicationContext as GarageApplication).container
    CoroutineScope(Dispatchers.Default).launch {
        try {
            block(container)
        } finally {
            pendingResult.finish()
        }
    }
}

/**
 * Any zone entered -> arrival session (the home zone catches short trips that never left the wake
 * zone); any zone exited -> departure session (the wake zone also catches missed departures).
 * Opening still requires the engine to have seen the user well away first.
 */
class GeofenceTransitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return onGeofenceError(context, event.errorCode)
        val purpose = when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> TrackingPurpose.ARRIVAL
            Geofence.GEOFENCE_TRANSITION_EXIT -> TrackingPurpose.DEPARTURE
            else -> return
        }
        val fences = event.triggeringGeofences.orEmpty().joinToString { it.requestId }
        val started = PresenceTrackingService.start(context, purpose)
        launchWithContainer(context) { container ->
            container.diagnosticsLog.record("Geofence ${if (purpose == TrackingPurpose.ARRIVAL) "ENTER" else "EXIT"} [$fences] -> $purpose${if (started) "" else " (Android refused to start tracking)"}")
        }
    }

    private fun onGeofenceError(context: Context, errorCode: Int) {
        launchWithContainer(context) { container ->
            container.diagnosticsLog.record("Geofence error code $errorCode")
            if (errorCode == GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE) container.automationCoordinator.markRegistrationLost()
        }
    }
}

/** Started walking/driving while home (or not yet known) -> short departure session to see whether the user is leaving. */
class ActivityTransitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityTransitionResult.hasResult(intent)) return
        val events = ActivityTransitionResult.extractResult(intent)?.transitionEvents.orEmpty()
        val startedMoving = events.any {
            it.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER && it.activityType in AutomationRegistrar.MOVING_ACTIVITIES
        }
        if (!startedMoving) return
        launchWithContainer(context) { container ->
            val zone = container.presenceRepository.current().zone
            // UNKNOWN is treated like HOME: if the app does not know where the user is, it should look.
            val started = zone != PresenceZone.AWAY && PresenceTrackingService.start(context, TrackingPurpose.DEPARTURE)
            container.diagnosticsLog.record("Started moving while $zone${if (started) " -> DEPARTURE" else ""}")
        }
    }
}

/** The system forgets geofences and activity registrations on reboot and app update. */
class AutomationRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        launchWithContainer(context) { container ->
            if (intent.action == Intent.ACTION_BOOT_COMPLETED) container.carConnection.clear()
            container.automationCoordinator.forceSync()
            resumeArrivalWatchIfAway(context, container)
            if (container.automationSettings.current().voiceCommandsEnabled) container.automationNotifications.showVoicePaused()
        }
    }

    /** Geofences alone would miss the return, so the away watch must survive a reboot or update. */
    private suspend fun resumeArrivalWatchIfAway(context: Context, container: AppContainer) {
        val isAway = container.presenceRepository.current().zone == PresenceZone.AWAY
        if (!isAway || !container.automationSettings.current().isEnabled) return
        val started = PresenceTrackingService.start(context, TrackingPurpose.ARRIVAL)
        container.diagnosticsLog.record("Resumed away watch after restart${if (started) "" else " (Android refused to start tracking)"}")
    }
}

class CountdownCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        (context.applicationContext as GarageApplication).container.commandCountdown.cancel()
    }
}

/** Records Bluetooth connections by hardware address so the chosen car can be matched exactly. */
class CarBluetoothReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val isConnected = when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> true
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> false
            else -> return
        }
        val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
        launchWithContainer(context) { it.carConnection.onConnectionChanged(device.address, isConnected) }
    }
}

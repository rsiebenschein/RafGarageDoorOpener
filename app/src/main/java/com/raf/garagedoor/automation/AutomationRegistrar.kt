package com.raf.garagedoor.automation

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.raf.garagedoor.geofence.GarageGeofence
import kotlinx.coroutines.tasks.await
import kotlin.math.max

class AutomationRegistrationException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Registers the low-power system triggers that wake the app: a large wake-up geofence for arrivals,
 * a home geofence at the close radius, and activity transitions (started walking/driving) for departures.
 */
class AutomationRegistrar(private val context: Context) {

    private val geofencingClient = LocationServices.getGeofencingClient(context)
    private val activityClient = ActivityRecognition.getClient(context)

    // Callers verify permissions first; a revoked permission surfaces as SecurityException.
    @SuppressLint("MissingPermission")
    suspend fun register(geofence: GarageGeofence, includeActivityTransitions: Boolean) {
        try {
            geofencingClient.removeGeofences(geofencePendingIntent()).await()
            geofencingClient.addGeofences(geofencingRequest(geofence), geofencePendingIntent()).await()
            if (includeActivityTransitions) {
                activityClient.requestActivityTransitionUpdates(activityTransitionRequest(), activityPendingIntent()).await()
            }
        } catch (denied: SecurityException) {
            throw AutomationRegistrationException("Location or activity permission is missing", denied)
        } catch (unavailable: ApiException) {
            throw AutomationRegistrationException("Location services are off or unavailable (${unavailable.statusCode})", unavailable)
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun unregister() {
        try {
            geofencingClient.removeGeofences(geofencePendingIntent()).await()
            activityClient.removeActivityTransitionUpdates(activityPendingIntent()).await()
        } catch (alreadyGone: ApiException) {
            // Nothing registered (e.g. after a reboot); nothing to remove.
        } catch (denied: SecurityException) {
            // Without permission the system has already dropped our registrations.
        }
    }

    private fun geofencingRequest(geofence: GarageGeofence): GeofencingRequest = GeofencingRequest.Builder()
        .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
        .addGeofences(listOf(fence(WAKE_FENCE_ID, geofence, wakeRadiusMeters(geofence)), fence(HOME_FENCE_ID, geofence, homeFenceRadiusMeters(geofence))))
        .build()

    private fun fence(id: String, geofence: GarageGeofence, radiusMeters: Double): Geofence = Geofence.Builder()
        .setRequestId(id)
        .setCircularRegion(geofence.center.latitude, geofence.center.longitude, radiusMeters.toFloat())
        .setExpirationDuration(Geofence.NEVER_EXPIRE)
        .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
        .setNotificationResponsiveness(NOTIFICATION_RESPONSIVENESS_MILLIS)
        .build()

    private fun activityTransitionRequest(): ActivityTransitionRequest = ActivityTransitionRequest(
        MOVING_ACTIVITIES.map { activity ->
            ActivityTransition.Builder()
                .setActivityType(activity)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build()
        },
    )

    private fun geofencePendingIntent(): PendingIntent =
        broadcastIntent(GEOFENCE_REQUEST_CODE, GeofenceTransitionReceiver::class.java)

    private fun activityPendingIntent(): PendingIntent =
        broadcastIntent(ACTIVITY_REQUEST_CODE, ActivityTransitionReceiver::class.java)

    /** Play services fills in the event extras, so these PendingIntents must be mutable. */
    private fun broadcastIntent(requestCode: Int, receiver: Class<*>): PendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode,
        Intent(context, receiver),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

    companion object {
        const val WAKE_FENCE_ID = "garage-wake"
        const val HOME_FENCE_ID = "garage-home"
        /** About 0.6 mi: enough lead for Android's geofence latency at neighborhood driving speeds. */
        const val MIN_WAKE_RADIUS_METERS = 1_000.0
        val MOVING_ACTIVITIES = listOf(
            DetectedActivity.WALKING,
            DetectedActivity.RUNNING,
            DetectedActivity.ON_BICYCLE,
            DetectedActivity.IN_VEHICLE,
        )
        /** Android geofences under ~100-150 m fire late or not at all, so the home fence never goes below this. */
        const val MIN_HOME_FENCE_RADIUS_METERS = 150.0
        private const val NOTIFICATION_RESPONSIVENESS_MILLIS = 10_000
        private const val GEOFENCE_REQUEST_CODE = 10
        private const val ACTIVITY_REQUEST_CODE = 11
        private const val WAKE_RADIUS_TO_OUTER_RADIUS_RATIO = 3.0

        fun wakeRadiusMeters(geofence: GarageGeofence): Double =
            max(MIN_WAKE_RADIUS_METERS, geofence.outerRadiusMeters * WAKE_RADIUS_TO_OUTER_RADIUS_RATIO)

        /** Only wakes the app; the open and close decisions still use the user's exact radii. */
        fun homeFenceRadiusMeters(geofence: GarageGeofence): Double =
            max(MIN_HOME_FENCE_RADIUS_METERS, geofence.outerRadiusMeters)
    }
}

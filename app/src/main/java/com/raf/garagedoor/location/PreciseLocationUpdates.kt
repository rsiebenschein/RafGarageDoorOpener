package com.raf.garagedoor.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.raf.garagedoor.automation.LocationFix
import com.raf.garagedoor.geofence.GeoPoint
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import java.time.Duration

/**
 * How hard to work for a fix and how often: [PRECISE] is GPS every 2 s close to the garage;
 * [lowPower] is Wi-Fi/cell positioning (no GPS) at a chosen interval, for when the user is away.
 */
data class LocationCadence(val priority: Int, val interval: Duration) {
    companion object {
        val PRECISE = LocationCadence(Priority.PRIORITY_HIGH_ACCURACY, Duration.ofSeconds(2))

        fun lowPower(interval: Duration) = LocationCadence(Priority.PRIORITY_BALANCED_POWER_ACCURACY, interval)
    }
}

/** Location stream that is only collected during short, bounded tracking sessions. */
class PreciseLocationUpdates(context: Context) {

    private val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)

    // Background location is checked before a session starts; revocation surfaces as SecurityException.
    @SuppressLint("MissingPermission")
    fun fixes(cadence: LocationCadence = LocationCadence.PRECISE): Flow<LocationFix> = callbackFlow {
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { trySend(it.toFix()) }
            }
        }
        try {
            client.requestLocationUpdates(requestFor(cadence), callback, Looper.getMainLooper())
        } catch (denied: SecurityException) {
            close(LocationPermissionMissingException())
        }
        awaitClose { client.removeLocationUpdates(callback) }
    }.conflate()

    private fun requestFor(cadence: LocationCadence): LocationRequest =
        LocationRequest.Builder(cadence.priority, cadence.interval.toMillis())
            .setMinUpdateIntervalMillis(cadence.interval.toMillis() / 2)
            .build()

    private fun Location.toFix() = LocationFix(
        point = GeoPoint(latitude, longitude),
        accuracyMeters = if (hasAccuracy()) accuracy else Float.MAX_VALUE,
        speedMetersPerSecond = if (hasSpeed()) speed else null,
    )
}

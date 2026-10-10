package com.raf.garagedoor.location

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.raf.garagedoor.geofence.GeoPoint
import kotlinx.coroutines.tasks.await

interface CurrentLocationProvider {
    suspend fun currentLocation(): GeoPoint
}

sealed class LocationException(message: String) : Exception(message)

class LocationPermissionMissingException : LocationException("Location permission has not been granted")

class LocationUnavailableException : LocationException("No location fix is available right now")

class FusedCurrentLocationProvider(context: Context) : CurrentLocationProvider {

    private val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)

    // Permission is requested by the UI; a revoked permission surfaces as SecurityException below.
    @SuppressLint("MissingPermission")
    override suspend fun currentLocation(): GeoPoint {
        val cancellation = CancellationTokenSource()
        val location = try {
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token).await()
        } catch (denied: SecurityException) {
            throw LocationPermissionMissingException()
        } catch (playServicesFailure: ApiException) {
            throw LocationUnavailableException()
        }
        return location?.let { GeoPoint(it.latitude, it.longitude) } ?: throw LocationUnavailableException()
    }
}

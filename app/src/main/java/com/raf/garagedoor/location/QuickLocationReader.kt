package com.raf.garagedoor.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.raf.garagedoor.automation.LocationFix
import com.raf.garagedoor.geofence.GeoPoint
import kotlinx.coroutines.tasks.await
import java.time.Duration

/**
 * One cheap reading (Wi-Fi/cell, no GPS), reusing one from the last minute if Android has it.
 * Its accuracy comes with it, so the caller can ignore a reading too rough to use.
 */
class QuickLocationReader(context: Context) {

    private val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)

    /** Null when there is no reading or no permission; the caller simply does without. */
    @SuppressLint("MissingPermission")
    suspend fun read(): LocationFix? {
        val cancellation = CancellationTokenSource()
        return try {
            client.getCurrentLocation(request(), cancellation.token).await()?.toFix()
        } catch (denied: SecurityException) {
            null
        } catch (playServicesFailure: ApiException) {
            null
        } finally {
            cancellation.cancel()
        }
    }

    private fun request(): CurrentLocationRequest = CurrentLocationRequest.Builder()
        .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
        .setMaxUpdateAgeMillis(MAX_REUSED_AGE.toMillis())
        .setDurationMillis(MAX_WAIT.toMillis())
        .build()

    private fun Location.toFix() = LocationFix(
        point = GeoPoint(latitude, longitude),
        accuracyMeters = if (hasAccuracy()) accuracy else Float.MAX_VALUE,
        speedMetersPerSecond = null,
    )

    private companion object {
        val MAX_REUSED_AGE: Duration = Duration.ofMinutes(1)
        val MAX_WAIT: Duration = Duration.ofSeconds(10)
    }
}

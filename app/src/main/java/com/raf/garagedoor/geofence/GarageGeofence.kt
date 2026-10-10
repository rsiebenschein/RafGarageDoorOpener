package com.raf.garagedoor.geofence

import kotlin.math.roundToInt

private const val METERS_PER_FOOT = 0.3048

fun feetToMeters(feet: Int): Double = feet * METERS_PER_FOOT

fun metersToFeet(meters: Double): Int = (meters / METERS_PER_FOOT).roundToInt()

data class GeoPoint(val latitude: Double, val longitude: Double)

/**
 * The garage location with two radii: the door opens when arriving inside [openRadiusFeet] and
 * closes after leaving beyond [closeRadiusFeet]. The gap between them stops GPS jitter near the
 * boundary from opening and closing the door repeatedly.
 */
data class GarageGeofence(
    val center: GeoPoint,
    val openRadiusFeet: Int,
    val closeRadiusFeet: Int,
) {
    val openRadiusMeters: Double get() = feetToMeters(openRadiusFeet)
    val closeRadiusMeters: Double get() = feetToMeters(closeRadiusFeet)
}

object GeofenceRadiusLimits {
    const val OPEN_MIN_FEET = 25
    const val OPEN_MAX_FEET = 500
    const val CLOSE_MAX_FEET = 1000
    const val MIN_CLOSE_GAP_FEET = 20
    const val DEFAULT_OPEN_FEET = 60
    const val DEFAULT_CLOSE_FEET = 100

    fun coerceOpen(feet: Int): Int = feet.coerceIn(OPEN_MIN_FEET, OPEN_MAX_FEET)

    fun closeMinimumFor(openFeet: Int): Int = openFeet + MIN_CLOSE_GAP_FEET

    fun coerceClose(closeFeet: Int, openFeet: Int): Int = closeFeet.coerceIn(closeMinimumFor(openFeet), CLOSE_MAX_FEET)
}

/** Editable, possibly incomplete geofence; radius changes keep the close radius beyond the open radius. */
data class GeofenceDraft(
    val center: GeoPoint? = null,
    val openRadiusFeet: Int = GeofenceRadiusLimits.DEFAULT_OPEN_FEET,
    val closeRadiusFeet: Int = GeofenceRadiusLimits.DEFAULT_CLOSE_FEET,
) {
    val openRadiusMeters: Double get() = feetToMeters(openRadiusFeet)
    val closeRadiusMeters: Double get() = feetToMeters(closeRadiusFeet)

    fun withCenter(point: GeoPoint): GeofenceDraft = copy(center = point)

    fun withOpenRadius(feet: Int): GeofenceDraft {
        val open = GeofenceRadiusLimits.coerceOpen(feet)
        return copy(openRadiusFeet = open, closeRadiusFeet = GeofenceRadiusLimits.coerceClose(closeRadiusFeet, open))
    }

    fun withCloseRadius(feet: Int): GeofenceDraft =
        copy(closeRadiusFeet = GeofenceRadiusLimits.coerceClose(feet, openRadiusFeet))

    fun toGeofence(): GarageGeofence? = center?.let { GarageGeofence(it, openRadiusFeet, closeRadiusFeet) }

    companion object {
        fun from(geofence: GarageGeofence) =
            GeofenceDraft(geofence.center, geofence.openRadiusFeet, geofence.closeRadiusFeet)
    }
}

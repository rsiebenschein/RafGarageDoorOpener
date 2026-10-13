package com.raf.garagedoor.geofence

import kotlin.math.roundToInt

private const val METERS_PER_FOOT = 0.3048

fun feetToMeters(feet: Int): Double = feet * METERS_PER_FOOT

fun metersToFeet(meters: Double): Int = (meters / METERS_PER_FOOT).roundToInt()

data class GeoPoint(val latitude: Double, val longitude: Double)

/**
 * The garage location with two radii: the door opens when arriving inside [openRadiusFeet] and
 * closes after leaving beyond [closeRadiusFeet]. Either may be the larger one; the presence engine,
 * not a gap between them, stops GPS jitter from opening and closing the door repeatedly.
 */
data class GarageGeofence(
    val center: GeoPoint,
    val openRadiusFeet: Int,
    val closeRadiusFeet: Int,
) {
    val openRadiusMeters: Double get() = feetToMeters(openRadiusFeet)
    val closeRadiusMeters: Double get() = feetToMeters(closeRadiusFeet)

    /** The larger of the two radii. */
    val outerRadiusMeters: Double get() = maxOf(openRadiusMeters, closeRadiusMeters)
}

object GeofenceRadiusLimits {
    const val OPEN_MIN_FEET = 25
    const val OPEN_MAX_FEET = 500

    /** GPS outdoors is good to about ±15 ft; below this a close could come from jitter at the door. */
    const val CLOSE_MIN_FEET = 60
    const val CLOSE_MAX_FEET = 1000
    const val DEFAULT_OPEN_FEET = 60
    const val DEFAULT_CLOSE_FEET = 100

    fun coerceOpen(feet: Int): Int = feet.coerceIn(OPEN_MIN_FEET, OPEN_MAX_FEET)

    fun coerceClose(feet: Int): Int = feet.coerceIn(CLOSE_MIN_FEET, CLOSE_MAX_FEET)
}

/** Editable, possibly incomplete geofence; the two radii are set independently. */
data class GeofenceDraft(
    val center: GeoPoint? = null,
    val openRadiusFeet: Int = GeofenceRadiusLimits.DEFAULT_OPEN_FEET,
    val closeRadiusFeet: Int = GeofenceRadiusLimits.DEFAULT_CLOSE_FEET,
) {
    val openRadiusMeters: Double get() = feetToMeters(openRadiusFeet)
    val closeRadiusMeters: Double get() = feetToMeters(closeRadiusFeet)

    fun withCenter(point: GeoPoint): GeofenceDraft = copy(center = point)

    val outerRadiusMeters: Double get() = maxOf(openRadiusMeters, closeRadiusMeters)

    fun withOpenRadius(feet: Int): GeofenceDraft = copy(openRadiusFeet = GeofenceRadiusLimits.coerceOpen(feet))

    fun withCloseRadius(feet: Int): GeofenceDraft = copy(closeRadiusFeet = GeofenceRadiusLimits.coerceClose(feet))

    fun toGeofence(): GarageGeofence? = center?.let { GarageGeofence(it, openRadiusFeet, closeRadiusFeet) }

    companion object {
        fun from(geofence: GarageGeofence) =
            GeofenceDraft(geofence.center, geofence.openRadiusFeet, geofence.closeRadiusFeet)
    }
}

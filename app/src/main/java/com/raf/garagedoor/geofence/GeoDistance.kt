package com.raf.garagedoor.geofence

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object GeoDistance {
    private const val EARTH_MEAN_RADIUS_METERS = 6_371_008.8

    /** Great-circle (haversine) distance; sub-centimeter accurate at driveway scale. */
    fun meters(from: GeoPoint, to: GeoPoint): Double {
        val fromLatitude = Math.toRadians(from.latitude)
        val toLatitude = Math.toRadians(to.latitude)
        val latitudeDelta = toLatitude - fromLatitude
        val longitudeDelta = Math.toRadians(to.longitude - from.longitude)
        val haversine = sin(latitudeDelta / 2).pow(2) +
            cos(fromLatitude) * cos(toLatitude) * sin(longitudeDelta / 2).pow(2)
        return 2 * EARTH_MEAN_RADIUS_METERS * asin(min(1.0, sqrt(haversine)))
    }
}

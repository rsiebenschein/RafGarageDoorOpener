package com.raf.garagedoor.geofence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GeofenceDraftTest {

    private val garage = GeoPoint(40.0, -75.0)

    @Test
    fun defaults_matchRequestedSixtyFootOpenRadius() {
        val draft = GeofenceDraft()

        assertEquals(60, draft.openRadiusFeet)
        assertEquals(100, draft.closeRadiusFeet)
    }

    @Test
    fun radii_areIndependentSoTheOpenRadiusCanBeTheLargerOne() {
        val draft = GeofenceDraft().withOpenRadius(300).withCloseRadius(100)

        assertEquals(300, draft.openRadiusFeet)
        assertEquals(100, draft.closeRadiusFeet)
        assertEquals(91.44, draft.outerRadiusMeters, 1e-9)
    }

    @Test
    fun withOpenRadius_clampsToAllowedRange() {
        assertEquals(GeofenceRadiusLimits.OPEN_MIN_FEET, GeofenceDraft().withOpenRadius(1).openRadiusFeet)
        assertEquals(GeofenceRadiusLimits.OPEN_MAX_FEET, GeofenceDraft().withOpenRadius(9_999).openRadiusFeet)
    }

    @Test
    fun withCloseRadius_clampsToAllowedRange() {
        assertEquals(GeofenceRadiusLimits.CLOSE_MIN_FEET, GeofenceDraft().withCloseRadius(10).closeRadiusFeet)
        assertEquals(GeofenceRadiusLimits.CLOSE_MAX_FEET, GeofenceDraft().withCloseRadius(9_999).closeRadiusFeet)
    }

    @Test
    fun toGeofence_isNullUntilGarageIsPlaced() {
        assertNull(GeofenceDraft().toGeofence())
        assertEquals(GarageGeofence(garage, 60, 100), GeofenceDraft().withCenter(garage).toGeofence())
    }

    @Test
    fun radiusMeters_convertsFromFeet() {
        val geofence = GarageGeofence(garage, openRadiusFeet = 100, closeRadiusFeet = 200)

        assertEquals(30.48, geofence.openRadiusMeters, 1e-9)
        assertEquals(60.96, geofence.closeRadiusMeters, 1e-9)
    }
}

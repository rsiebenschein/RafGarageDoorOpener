package com.raf.garagedoor.automation

import com.raf.garagedoor.geofence.GarageGeofence
import com.raf.garagedoor.geofence.GeoDistance
import com.raf.garagedoor.geofence.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PresenceEngineTest {

    private val garage = GeoPoint(40.0, -75.0)

    /** 80 ft open (24.4 m) and 150 ft close (45.7 m); arrival arms beyond ~95.7 m. */
    private val geofence = GarageGeofence(garage, openRadiusFeet = 80, closeRadiusFeet = 150)
    private val now = Instant.parse("2026-10-10T18:00:00Z")

    @Test
    fun geoDistance_matchesKnownOffsets() {
        assertEquals(100.0, GeoDistance.meters(garage, pointNorth(100.0)), 0.5)
        assertEquals(0.0, GeoDistance.meters(garage, garage), 1e-9)
    }

    @Test
    fun unknownZone_insideCloseRadiusBecomesHomeWithoutOpening() {
        val evaluation = evaluate(PresenceState(), metersNorth = 10.0)

        assertEquals(PresenceZone.HOME, evaluation.state.zone)
        assertNull(evaluation.decision)
    }

    @Test
    fun arrivingFromFarAway_opensOnceInsideOpenRadius() {
        var state = evaluate(PresenceState(), metersNorth = 400.0).state
        assertFalse(state.isArrivalArmed)
        state = evaluate(state, metersNorth = 380.0).state
        assertTrue(state.isArrivalArmed)

        state = evaluate(state, metersNorth = 60.0).also { assertNull(it.decision) }.state
        val arrival = evaluate(state, metersNorth = 20.0)

        assertEquals(DoorDecision.OPEN, arrival.decision)
        assertEquals(PresenceZone.HOME, arrival.state.zone)
    }

    @Test
    fun firstFixAfterEnabling_neverArmsEvenWhenFar() {
        val evaluation = evaluate(PresenceState(), metersNorth = 900.0)

        assertEquals(PresenceZone.AWAY, evaluation.state.zone)
        assertFalse(evaluation.state.isArrivalArmed)
    }

    @Test
    fun fixWithMediocreAccuracy_doesNotArmArrival() {
        val away = PresenceState(zone = PresenceZone.AWAY)

        val evaluation = evaluate(away, metersNorth = 400.0, accuracy = 30f)

        assertFalse(evaluation.state.isArrivalArmed)
    }

    @Test
    fun fastDeparture_closesOnFirstConfidentFixOutside() {
        val home = PresenceState(zone = PresenceZone.HOME)

        val evaluation = evaluate(home, metersNorth = 60.0, accuracy = 8f, speed = 6f)

        assertEquals(DoorDecision.CLOSE, evaluation.decision)
    }

    @Test
    fun shortWalkBeyondCloseRadius_doesNotArmArrival() {
        val away = PresenceState(zone = PresenceZone.AWAY, isArrivalArmed = false)

        val back = evaluate(away, metersNorth = 15.0)

        assertNull(back.decision)
        assertEquals(PresenceZone.HOME, back.state.zone)
    }

    @Test
    fun gpsDriftWhileHome_neverOpensDoor() {
        val home = PresenceState(zone = PresenceZone.HOME)

        val drifted = evaluate(home, metersNorth = 70.0, accuracy = 35f)

        assertNull(drifted.decision)
        assertEquals(PresenceZone.HOME, drifted.state.zone)
    }

    @Test
    fun leaving_closesOnlyAfterTwoConfidentFixesOutside() {
        val home = PresenceState(zone = PresenceZone.HOME)

        val first = evaluate(home, metersNorth = 60.0, accuracy = 8f)
        assertNull(first.decision)
        val second = evaluate(first.state, metersNorth = 65.0, accuracy = 8f)

        assertEquals(DoorDecision.CLOSE, second.decision)
        assertEquals(PresenceZone.AWAY, second.state.zone)
        assertFalse(second.state.isArrivalArmed)
    }

    @Test
    fun leaving_fixBackInsideResetsConsecutiveCount() {
        val home = PresenceState(zone = PresenceZone.HOME)

        val outside = evaluate(home, metersNorth = 60.0, accuracy = 8f).state
        val inside = evaluate(outside, metersNorth = 20.0, accuracy = 8f).state

        assertEquals(0, inside.consecutiveFixesOutside)
        assertNull(evaluate(inside, metersNorth = 60.0, accuracy = 8f).decision)
    }

    @Test
    fun inaccurateFix_isRecordedButIgnoredForDecisions() {
        val home = PresenceState(zone = PresenceZone.HOME)

        val evaluation = evaluate(home, metersNorth = 500.0, accuracy = 80f)

        assertNull(evaluation.decision)
        assertEquals(PresenceZone.HOME, evaluation.state.zone)
        assertEquals(500.0, evaluation.state.lastDistanceMeters!!, 1.0)
    }

    @Test
    fun openCountdown_triggersEarlyBySpeedTimesCountdown() {
        val armedAway = PresenceState(zone = PresenceZone.AWAY, isArrivalArmed = true, lastFixAt = now)

        val withoutLead = evaluate(armedAway, metersNorth = 70.0, speed = 10f, leadSeconds = 0)
        val withLead = evaluate(armedAway, metersNorth = 70.0, speed = 10f, leadSeconds = 5)

        assertNull(withoutLead.decision)
        assertEquals(DoorDecision.OPEN, withLead.decision)
    }

    @Test
    fun arrivalSeenOnlyAfterLongGap_marksHomeWithoutOpening() {
        val armedAwayLongAgo = PresenceState(zone = PresenceZone.AWAY, isArrivalArmed = true, lastFixAt = now.minusSeconds(3_600))

        val evaluation = evaluate(armedAwayLongAgo, metersNorth = 10.0)

        assertEquals(PresenceZone.HOME, evaluation.state.zone)
        assertNull(evaluation.decision)
    }

    private fun evaluate(
        state: PresenceState,
        metersNorth: Double,
        accuracy: Float = 5f,
        speed: Float? = null,
        leadSeconds: Int = 0,
    ): PresenceEvaluation {
        val fix = LocationFix(pointNorth(metersNorth), accuracy, speed)
        return PresenceEngine.evaluate(state, fix, geofence, leadSeconds, now)
    }

    private fun pointNorth(meters: Double) = GeoPoint(garage.latitude + meters / 111_195.0, garage.longitude)
}

package com.raf.garagedoor.automation

import com.raf.garagedoor.geofence.GarageGeofence
import com.raf.garagedoor.geofence.GeoDistance
import com.raf.garagedoor.geofence.GeoPoint
import java.time.Duration
import java.time.Instant
import kotlin.math.min

data class LocationFix(val point: GeoPoint, val accuracyMeters: Float, val speedMetersPerSecond: Float?)

enum class DoorDecision { OPEN, CLOSE }

data class PresenceEvaluation(val state: PresenceState, val decision: DoorDecision?, val distanceMeters: Double)

/**
 * Pure home/away state machine fed by precise location fixes.
 *
 * - The two radii are independent: the open radius may be the larger one (open early, close soon
 *   after leaving the driveway).
 * - Opening requires a real trip: arrival is only armed by a later, accurate fix well beyond the
 *   larger radius. The first fix after enabling never arms, so a bad indoor fix cannot open the
 *   door, and a road that bends back toward the house cannot open it while leaving.
 * - Closing requires two consecutive fixes confidently outside the close radius, or one while
 *   moving at cycling/driving speed, where waiting for a second fix costs too much distance.
 * - Close to the house (within [DRIFT_PRONE_DISTANCE_METERS], or inside the open radius) a fix
 *   only counts toward closing while moving away at cycling/driving speed, and two are needed:
 *   GPS indoors or in the yard drifts that far, and someone riding in after the door opened is
 *   moving fast too, but toward the house.
 * - With an open countdown, the trigger moves outward by speed x countdown so the door still
 *   starts moving at the configured radius.
 * - Opening also requires the approach to have been watched: if the previous reading is older than
 *   [MAX_READING_GAP_TO_OPEN], the user may have been home for a while (e.g. the first check after
 *   an hour-long gap), so they are marked home without opening the door behind their back.
 */
object PresenceEngine {
    const val MAX_USABLE_ACCURACY_METERS = 40f
    const val REQUIRED_CONSECUTIVE_OUTSIDE_FIXES = 2
    const val ARRIVAL_ARMING_MARGIN_METERS = 50.0
    const val MAX_ARMING_ACCURACY_METERS = 25.0
    const val FAST_DEPARTURE_SPEED_METERS_PER_SECOND = 3f

    /** 200 ft: the close radius used through field tests 3 to 5 without a single false close. */
    const val DRIFT_PRONE_DISTANCE_METERS = 61.0
    private const val MAX_OPEN_LEAD_METERS = 300.0
    val MAX_READING_GAP_TO_OPEN: Duration = Duration.ofMinutes(5)

    fun evaluate(state: PresenceState, fix: LocationFix, geofence: GarageGeofence, openLeadSeconds: Int, now: Instant): PresenceEvaluation {
        val distance = GeoDistance.meters(fix.point, geofence.center)
        val observed = state.copy(lastDistanceMeters = distance, lastFixAt = now)
        if (fix.accuracyMeters > MAX_USABLE_ACCURACY_METERS) return PresenceEvaluation(observed, null, distance)
        val isMovingFast = (fix.speedMetersPerSecond ?: 0f) >= FAST_DEPARTURE_SPEED_METERS_PER_SECOND
        val isMovingAway = state.lastDistanceMeters?.let { distance > it } == true
        val motion = Motion(isMovingFast, isMovingAway)
        val sample = Sample(distance, fix.accuracyMeters.toDouble(), openLeadMeters(fix, openLeadSeconds), motion, isApproachWatched(state, now))
        return when (state.zone) {
            PresenceZone.UNKNOWN -> PresenceEvaluation(classifyInitialZone(observed, sample, geofence), null, distance)
            PresenceZone.AWAY -> evaluateWhileAway(observed, sample, geofence)
            PresenceZone.HOME -> evaluateWhileHome(observed, sample, geofence)
        }
    }

    fun arrivalArmingDistanceMeters(geofence: GarageGeofence): Double =
        geofence.outerRadiusMeters + ARRIVAL_ARMING_MARGIN_METERS

    private data class Motion(val isFast: Boolean, val isAway: Boolean)

    private data class Sample(
        val distance: Double,
        val accuracy: Double,
        val openLead: Double,
        val motion: Motion,
        val isApproachWatched: Boolean,
    ) {
        val confidentMinimumDistance: Double get() = distance - accuracy
        val isMovingFast: Boolean get() = motion.isFast
    }

    private fun classifyInitialZone(state: PresenceState, sample: Sample, geofence: GarageGeofence): PresenceState =
        if (sample.distance <= geofence.closeRadiusMeters) {
            state.copy(zone = PresenceZone.HOME, consecutiveFixesOutside = 0, isArrivalArmed = false)
        } else {
            state.copy(zone = PresenceZone.AWAY, isArrivalArmed = false)
        }

    private fun evaluateWhileAway(state: PresenceState, sample: Sample, geofence: GarageGeofence): PresenceEvaluation {
        val armed = state.isArrivalArmed || isFarEnoughToArm(sample, geofence)
        val arrived = sample.distance - sample.openLead <= geofence.openRadiusMeters
        return when {
            armed && arrived -> PresenceEvaluation(atHome(state), DoorDecision.OPEN.takeIf { sample.isApproachWatched }, sample.distance)
            !armed && sample.distance <= geofence.closeRadiusMeters -> PresenceEvaluation(atHome(state), null, sample.distance)
            else -> PresenceEvaluation(state.copy(isArrivalArmed = armed), null, sample.distance)
        }
    }

    private fun evaluateWhileHome(state: PresenceState, sample: Sample, geofence: GarageGeofence): PresenceEvaluation {
        if (!countsTowardClosing(sample, geofence)) {
            return PresenceEvaluation(state.copy(consecutiveFixesOutside = 0), null, sample.distance)
        }
        val outsideFixes = state.consecutiveFixesOutside + 1
        if (outsideFixes < requiredFixesToClose(sample, geofence)) {
            return PresenceEvaluation(state.copy(consecutiveFixesOutside = outsideFixes), null, sample.distance)
        }
        val departed = state.copy(zone = PresenceZone.AWAY, consecutiveFixesOutside = 0, isArrivalArmed = isFarEnoughToArm(sample, geofence))
        return PresenceEvaluation(departed, DoorDecision.CLOSE, sample.distance)
    }

    /** Outside the close radius; near the house, only while riding or driving away (see the class comment). */
    private fun countsTowardClosing(sample: Sample, geofence: GarageGeofence): Boolean {
        if (sample.confidentMinimumDistance <= geofence.closeRadiusMeters) return false
        return !isNearHouse(sample, geofence) || (sample.motion.isFast && sample.motion.isAway)
    }

    private fun requiredFixesToClose(sample: Sample, geofence: GarageGeofence): Int =
        if (sample.isMovingFast && !isNearHouse(sample, geofence)) 1 else REQUIRED_CONSECUTIVE_OUTSIDE_FIXES

    private fun isNearHouse(sample: Sample, geofence: GarageGeofence): Boolean =
        sample.confidentMinimumDistance <= maxOf(DRIFT_PRONE_DISTANCE_METERS, geofence.openRadiusMeters)

    private fun isApproachWatched(previous: PresenceState, now: Instant): Boolean =
        previous.lastFixAt?.let { Duration.between(it, now) <= MAX_READING_GAP_TO_OPEN } ?: false

    private fun atHome(state: PresenceState) =
        state.copy(zone = PresenceZone.HOME, consecutiveFixesOutside = 0, isArrivalArmed = false)

    private fun isFarEnoughToArm(sample: Sample, geofence: GarageGeofence): Boolean =
        sample.accuracy <= MAX_ARMING_ACCURACY_METERS && sample.confidentMinimumDistance > arrivalArmingDistanceMeters(geofence)

    private fun openLeadMeters(fix: LocationFix, openLeadSeconds: Int): Double {
        val speed = fix.speedMetersPerSecond?.toDouble() ?: 0.0
        return min(speed * openLeadSeconds, MAX_OPEN_LEAD_METERS)
    }
}

package com.raf.garagedoor.automation

import com.raf.garagedoor.location.LocationCadence
import java.time.Duration

/**
 * How often the away watch checks location. The next check comes after about half the time the
 * user would need to get home at highway speed, so an approach is always seen several times before
 * arrival: 20 mi away means a check every 10 minutes, 40 mi every 20, and an hour at most however
 * far away (an hour still catches the drive home after a flight). Within ~1,000 ft it switches to
 * GPS every 2 seconds.
 */
object ArrivalCadence {
    const val PRECISE_WITHIN_METERS = 300.0
    private const val ASSUMED_TOP_SPEED_METERS_PER_SECOND = 26.8 // 60 mph
    private const val SHARE_OF_TIME_TO_HOME = 0.5

    /** Fixed steps, so a small change in distance does not restart the location request every time. */
    private val STEPS: List<Duration> = listOf(
        Duration.ofSeconds(10),
        Duration.ofSeconds(30),
        Duration.ofMinutes(1),
        Duration.ofMinutes(2),
        Duration.ofMinutes(5),
        Duration.ofMinutes(10),
        Duration.ofMinutes(20),
        Duration.ofMinutes(30),
        Duration.ofMinutes(45),
        Duration.ofMinutes(60),
    )

    /** Uses the nearest the user could be given the fix's accuracy, so a vague fix never delays the next check. */
    fun forDistance(distanceMeters: Double?, accuracyMeters: Double): LocationCadence {
        val nearestPossible = distanceMeters?.minus(accuracyMeters) ?: return LocationCadence.PRECISE
        if (nearestPossible < PRECISE_WITHIN_METERS) return LocationCadence.PRECISE
        return LocationCadence.lowPower(stepFor(idealWaitSeconds(nearestPossible)))
    }

    private fun idealWaitSeconds(distanceMeters: Double): Double =
        distanceMeters / ASSUMED_TOP_SPEED_METERS_PER_SECOND * SHARE_OF_TIME_TO_HOME

    private fun stepFor(idealSeconds: Double): Duration =
        STEPS.lastOrNull { it.seconds <= idealSeconds } ?: STEPS.first()
}

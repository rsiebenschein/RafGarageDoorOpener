package com.raf.garagedoor.automation

import com.raf.garagedoor.location.LocationCadence
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

class ArrivalCadenceTest {

    @Test
    fun twentyMilesAway_checksEveryTenMinutes() {
        assertEquals(Duration.ofMinutes(10), intervalAt(miles = 20.0))
    }

    @Test
    fun fortyMilesAway_checksEveryTwentyMinutes() {
        assertEquals(Duration.ofMinutes(20), intervalAt(miles = 40.0))
    }

    @Test
    fun thousandsOfMilesAway_checksOnlyHourly() {
        assertEquals(Duration.ofHours(1), intervalAt(miles = 2_000.0))
    }

    @Test
    fun closeToHome_usesGps() {
        assertEquals(LocationCadence.PRECISE, ArrivalCadence.forDistance(250.0, accuracyMeters = 10.0))
    }

    @Test
    fun vagueFix_isTreatedAsNearestPossiblePoint() {
        val vague = ArrivalCadence.forDistance(METERS_PER_MILE * 20, accuracyMeters = METERS_PER_MILE * 10)

        assertEquals(Duration.ofMinutes(5), vague.interval)
    }

    @Test
    fun unknownDistance_usesGps() {
        assertEquals(LocationCadence.PRECISE, ArrivalCadence.forDistance(null, accuracyMeters = 0.0))
    }

    private fun intervalAt(miles: Double): Duration = ArrivalCadence.forDistance(miles * METERS_PER_MILE, accuracyMeters = 0.0).interval

    private companion object {
        const val METERS_PER_MILE = 1_609.34
    }
}

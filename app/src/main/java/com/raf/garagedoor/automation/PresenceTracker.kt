package com.raf.garagedoor.automation

import com.raf.garagedoor.geofence.GeofenceSettingsRepository
import com.raf.garagedoor.geofence.metersToFeet
import com.raf.garagedoor.location.LocationException
import com.raf.garagedoor.location.LocationCadence
import com.raf.garagedoor.location.PreciseLocationUpdates
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Clock
import java.time.Duration

enum class TrackingPurpose(val timeout: Duration) {
    /**
     * Runs from departure (or any wake-up while away) until the user is home. Android's geofences
     * are checked only every 30-40 minutes with the screen off, so they cannot be relied on to
     * notice an arrival; this watch checks at an [ArrivalCadence] that scales with distance. The
     * timeout is only a safety net, so a forgotten watch cannot run forever.
     */
    ARRIVAL(Duration.ofDays(30)),

    /** Woken by the door opening or the user starting to move while home; ends once they have left. */
    DEPARTURE(Duration.ofMinutes(5)),
    ;

    /** Once the user has left, keep watching for their return in the same foreground service. */
    fun followUpAfter(outcome: SessionOutcome): TrackingPurpose? =
        ARRIVAL.takeIf { this == DEPARTURE && outcome == SessionOutcome.GOAL_REACHED }
}

enum class SessionOutcome { GOAL_REACHED, TIMED_OUT, DISABLED, LOCATION_LOST }

data class LiveReading(val distanceMeters: Double?, val accuracyMeters: Float)

/**
 * Bounded location sessions that feed [PresenceEngine]. Fix processing is serialized so the
 * background session and the visible app can never both act on the same arrival.
 */
class PresenceTracker(
    private val locationUpdates: PreciseLocationUpdates,
    private val geofences: GeofenceSettingsRepository,
    private val settings: AutomationSettingsRepository,
    private val presence: PresenceRepository,
    private val executor: DoorAutomationExecutor,
    private val diagnostics: DiagnosticsLog,
    private val clock: Clock,
    private val isPowerSavingLimitingLocation: () -> Boolean = { false },
) {
    private val fixMutex = Mutex()

    suspend fun runSession(purpose: TrackingPurpose): SessionOutcome {
        diagnostics.record("$purpose session started" + powerSavingNote())
        val stats = SessionStats()
        val outcome = runBounded(purpose, stats)
        diagnostics.record("$purpose session ended: $outcome after ${stats.fixCount} fixes, best accuracy ${stats.bestAccuracyLabel()}")
        return outcome
    }

    private fun powerSavingNote(): String =
        if (isPowerSavingLimitingLocation()) " (Power saving is on: no GPS while the screen is off, arrivals will be late)" else ""

    /** Precise readings while the hands-free screen is visible; they count toward automation too. */
    fun liveReadings(): Flow<LiveReading> = locationUpdates.fixes(LocationCadence.PRECISE)
        .map { fix -> LiveReading(processFix(fix)?.distanceMeters, fix.accuracyMeters) }

    private suspend fun runBounded(purpose: TrackingPurpose, stats: SessionStats): SessionOutcome = try {
        withTimeoutOrNull(purpose.timeout.toMillis()) { collectUntilDone(purpose, stats) } ?: SessionOutcome.TIMED_OUT
    } catch (locationLost: LocationException) {
        SessionOutcome.LOCATION_LOST
    }

    /** Re-subscribes whenever the needed cadence changes, e.g. checking more often as the user nears home. */
    private suspend fun collectUntilDone(purpose: TrackingPurpose, stats: SessionStats): SessionOutcome {
        var cadence = cadenceFor(purpose, presence.current().lastDistanceMeters, accuracyMeters = 0.0)
        while (true) {
            var outcome: SessionOutcome? = null
            var nextCadence = cadence
            locationUpdates.fixes(cadence).first { fix ->
                stats.record(fix)
                val evaluation = processFix(fix)
                outcome = outcomeAfter(purpose, evaluation)
                nextCadence = cadenceFor(purpose, evaluation?.distanceMeters, fix.accuracyMeters.toDouble())
                outcome != null || nextCadence != cadence
            }
            outcome?.let { return it }
            cadence = nextCadence
        }
    }

    /** Non-cancellable: once a decision is saved, ending the session must not drop its door command. */
    private suspend fun processFix(fix: LocationFix): PresenceEvaluation? = fixMutex.withLock {
        withContext(NonCancellable) { evaluateAndAct(fix) }
    }

    private suspend fun evaluateAndAct(fix: LocationFix): PresenceEvaluation? {
        val geofence = geofences.geofence.first() ?: return null
        val previous = presence.current()
        val evaluation = PresenceEngine.evaluate(previous, fix, geofence, settings.current().openCountdownSeconds, clock.instant())
        presence.save(evaluation.state)
        recordTransition(previous, evaluation, fix)
        evaluation.decision?.let { executor.execute(it, evaluation.distanceMeters) }
        return evaluation
    }

    private suspend fun recordTransition(previous: PresenceState, evaluation: PresenceEvaluation, fix: LocationFix) {
        val zoneChanged = previous.zone != evaluation.state.zone
        val armedChanged = previous.isArrivalArmed != evaluation.state.isArrivalArmed
        if (!zoneChanged && !armedChanged && evaluation.decision == null) return
        val distanceFeet = metersToFeet(evaluation.distanceMeters)
        val accuracyFeet = metersToFeet(fix.accuracyMeters.toDouble())
        diagnostics.record(
            "Zone ${previous.zone} -> ${evaluation.state.zone}, armed=${evaluation.state.isArrivalArmed}, " +
                "decision=${evaluation.decision ?: "none"} at $distanceFeet ft (±$accuracyFeet ft, speed ${fix.speedMetersPerSecond ?: "n/a"} m/s)" +
                unwatchedArrivalNote(previous, evaluation),
        )
    }

    private fun unwatchedArrivalNote(previous: PresenceState, evaluation: PresenceEvaluation): String {
        val arrivedWithoutOpening = previous.isArrivalArmed && evaluation.state.zone == PresenceZone.HOME && evaluation.decision == null
        return if (arrivedWithoutOpening) " (not opened: previous reading too old, approach not watched)" else ""
    }

    private suspend fun outcomeAfter(purpose: TrackingPurpose, evaluation: PresenceEvaluation?): SessionOutcome? {
        if (!settings.current().isEnabled || geofences.geofence.first() == null) return SessionOutcome.DISABLED
        val zone = evaluation?.state?.zone ?: return null
        val goal = if (purpose == TrackingPurpose.DEPARTURE) PresenceZone.AWAY else PresenceZone.HOME
        return if (zone == goal) SessionOutcome.GOAL_REACHED else null
    }

    private fun cadenceFor(purpose: TrackingPurpose, distanceMeters: Double?, accuracyMeters: Double): LocationCadence =
        if (purpose == TrackingPurpose.DEPARTURE) LocationCadence.PRECISE else ArrivalCadence.forDistance(distanceMeters, accuracyMeters)

    private class SessionStats {
        var fixCount = 0
            private set
        private var bestAccuracyMeters: Float? = null

        fun record(fix: LocationFix) {
            fixCount++
            bestAccuracyMeters = minOf(bestAccuracyMeters ?: fix.accuracyMeters, fix.accuracyMeters)
        }

        fun bestAccuracyLabel(): String = bestAccuracyMeters?.let { "±${metersToFeet(it.toDouble())} ft" } ?: "n/a"
    }
}

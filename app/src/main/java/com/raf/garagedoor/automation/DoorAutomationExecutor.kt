package com.raf.garagedoor.automation

import com.raf.garagedoor.bluetooth.CarConnectionRepository
import com.raf.garagedoor.door.DoorCommand
import com.raf.garagedoor.door.DoorState
import com.raf.garagedoor.door.GarageDoor
import com.raf.garagedoor.door.GarageDoorControlException
import com.raf.garagedoor.door.GarageDoorController
import com.raf.garagedoor.door.TransientRetry
import com.raf.garagedoor.geofence.metersToFeet
import java.time.Clock
import java.time.Duration

/** User-visible side effects of automation, kept behind an interface so the executor is testable. */
interface AutomationAlerts {
    fun showCountdown(decision: DoorDecision, seconds: Int)

    fun dismissCountdown()

    fun showResult(event: AutomationEvent)

    /** A failed command, with a button to try [retryCommand] again from the notification. */
    fun showFailure(event: AutomationEvent, retryCommand: DoorCommand)
}

/** Turns a presence decision into a door command after cooldown, car, door-state, and countdown checks. */
class DoorAutomationExecutor(
    private val controller: GarageDoorController,
    private val settings: AutomationSettingsRepository,
    private val presence: PresenceRepository,
    private val carConnection: CarConnectionRepository,
    private val activityLog: ActivityLogRepository,
    private val alerts: AutomationAlerts,
    private val countdown: CommandCountdown,
    private val retry: TransientRetry,
    private val diagnostics: DiagnosticsLog,
    private val clock: Clock,
    private val onDoorOpened: suspend () -> Unit = {},
) {
    suspend fun execute(decision: DoorDecision, distanceMeters: Double) {
        val distanceFeet = metersToFeet(distanceMeters)
        val current = settings.current()
        if (!current.isEnabled) return
        val skipReason = skipReasonFor(decision, current)
        if (skipReason != null) return record(skipReason, distanceFeet)
        try {
            moveDoorIfNeeded(decision, current, distanceFeet)
        } catch (error: GarageDoorControlException) {
            alerts.dismissCountdown()
            diagnostics.record("$decision failed after retries at $distanceFeet ft: ${error.message}")
            recordFailure(decision, distanceFeet, error.message)
        }
    }

    private suspend fun skipReasonFor(decision: DoorDecision, current: AutomationSettings): AutomationEventType? {
        return when {
            decision == DoorDecision.CLOSE && isCoolingDown() -> AutomationEventType.SKIPPED_COOLDOWN
            decision == DoorDecision.OPEN && current.requireCarBluetooth && !isCarConnected(current) ->
                AutomationEventType.SKIPPED_CAR_NOT_CONNECTED
            else -> null
        }
    }

    /**
     * Only closing is held back, so GPS jitter in the driveway cannot close the door that just opened.
     * Opening again needs a genuine trip away first, which the presence engine already enforces.
     */
    private suspend fun isCoolingDown(): Boolean {
        val lastAction = presence.current().lastActionAt ?: return false
        return Duration.between(lastAction, clock.instant()) < ACTION_COOLDOWN
    }

    private suspend fun isCarConnected(current: AutomationSettings): Boolean =
        current.car?.let { carConnection.isConnected(it.address) } ?: false

    private suspend fun moveDoorIfNeeded(decision: DoorDecision, current: AutomationSettings, distanceFeet: Int) {
        val door = retry.run { controller.listDoors() }.firstOrNull()
            ?: return recordFailure(decision, distanceFeet, "No garage door found on the myQ account")
        val command = commandNeeded(decision, door) ?: return record(alreadyThereEvent(decision), distanceFeet)
        if (!countdownCompletes(decision, current)) return record(AutomationEventType.CANCELLED, distanceFeet)
        retry.run { controller.execute(door.id, command) }
        presence.recordAction(clock.instant())
        if (command == DoorCommand.OPEN) onDoorOpened()
        record(if (command == DoorCommand.OPEN) AutomationEventType.OPENED else AutomationEventType.CLOSED, distanceFeet)
    }

    private suspend fun countdownCompletes(decision: DoorDecision, current: AutomationSettings): Boolean {
        val seconds = if (decision == DoorDecision.OPEN) current.openCountdownSeconds else current.closeCountdownSeconds
        if (seconds > 0) alerts.showCountdown(decision, seconds)
        val completed = countdown.completesWithoutCancel(seconds)
        alerts.dismissCountdown()
        return completed
    }

    private suspend fun record(type: AutomationEventType, distanceFeet: Int, detail: String? = null) {
        val event = AutomationEvent(type, clock.millis(), distanceFeet, detail)
        activityLog.record(event)
        if (type in NOTIFIED_EVENTS) alerts.showResult(event)
    }

    private suspend fun recordFailure(decision: DoorDecision, distanceFeet: Int, detail: String?) {
        val event = AutomationEvent(AutomationEventType.FAILED, clock.millis(), distanceFeet, detail)
        activityLog.record(event)
        alerts.showFailure(event, retryCommand = if (decision == DoorDecision.OPEN) DoorCommand.OPEN else DoorCommand.CLOSE)
    }

    companion object {
        val ACTION_COOLDOWN: Duration = Duration.ofSeconds(90)

        private val NOTIFIED_EVENTS = setOf(
            AutomationEventType.OPENED,
            AutomationEventType.CLOSED,
            AutomationEventType.CANCELLED,
        )

        fun commandNeeded(decision: DoorDecision, door: GarageDoor): DoorCommand? = when (decision) {
            DoorDecision.OPEN -> DoorCommand.OPEN.takeIf { door.state == DoorState.CLOSED }
            DoorDecision.CLOSE -> DoorCommand.CLOSE.takeIf { door.state == DoorState.OPEN || door.state == DoorState.STOPPED }
        }

        private fun alreadyThereEvent(decision: DoorDecision) =
            if (decision == DoorDecision.OPEN) AutomationEventType.ALREADY_OPEN else AutomationEventType.ALREADY_CLOSED
    }
}

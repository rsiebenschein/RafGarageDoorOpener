package com.raf.garagedoor.automation.leftopen

import com.raf.garagedoor.automation.ActivityLogRepository
import com.raf.garagedoor.automation.AutomationEvent
import com.raf.garagedoor.automation.AutomationEventType
import com.raf.garagedoor.automation.AutomationSettingsRepository
import com.raf.garagedoor.automation.DiagnosticsLog
import com.raf.garagedoor.automation.LeftOpenAction
import com.raf.garagedoor.door.DoorCommand
import com.raf.garagedoor.door.DoorState
import com.raf.garagedoor.door.GarageDoor
import com.raf.garagedoor.door.GarageDoorControlException
import com.raf.garagedoor.door.GarageDoorController
import com.raf.garagedoor.door.TransientRetry
import java.time.Clock
import java.time.Duration
import java.time.Instant

/** What the left-open monitor wants the host to show the user. */
sealed interface LeftOpenOutcome {
    data object None : LeftOpenOutcome
    data class Remind(val openMinutes: Long) : LeftOpenOutcome
    data object AutoClosed : LeftOpenOutcome

    /** The door is open but nothing is due yet; check again exactly when it is. */
    data class CheckAgainIn(val delay: Duration) : LeftOpenOutcome
}

/**
 * Checks whether the door has been open too long and decides what to do, independent of location.
 * The open time comes from myQ's last state change, so a check that runs late still sees how long
 * the door has really been open, and it asks to be run again exactly when the next action is due.
 */
class LeftOpenMonitor(
    private val controller: GarageDoorController,
    private val settings: AutomationSettingsRepository,
    private val stateRepository: LeftOpenStateRepository,
    private val activityLog: ActivityLogRepository,
    private val diagnostics: DiagnosticsLog,
    private val retry: TransientRetry,
    private val clock: Clock,
) {
    suspend fun check(): LeftOpenOutcome {
        val current = settings.current()
        if (current.leftOpenAction == LeftOpenAction.OFF) return LeftOpenOutcome.None
        val door = try {
            retry.run { controller.listDoors() }.firstOrNull()
        } catch (error: GarageDoorControlException) {
            diagnostics.record("Left-open check could not reach myQ: ${error.message}")
            return LeftOpenOutcome.None
        }
        if (door == null || !isOpen(door)) {
            stateRepository.clear()
            return LeftOpenOutcome.None
        }
        return evaluateOpenDoor(door, current.leftOpenAction, Duration.ofMinutes(current.leftOpenMinutes.toLong()))
    }

    private suspend fun evaluateOpenDoor(door: GarageDoor, action: LeftOpenAction, threshold: Duration): LeftOpenOutcome {
        val now = clock.instant()
        val state = startedOrExistingSession(door, now)
        val openFor = Duration.between(state.firstSeenOpenAt, now)
        if (openFor < threshold) return LeftOpenOutcome.CheckAgainIn(threshold.minus(openFor))
        if (state.isCurrentSessionIgnored) return LeftOpenOutcome.None
        return if (action == LeftOpenAction.AUTO_CLOSE) autoClose(door, openFor) else remind(state, openFor, now)
    }

    /** Starts the session at myQ's last state change, never later than now, so a late first check is not counted from itself. */
    private suspend fun startedOrExistingSession(door: GarageDoor, now: Instant): LeftOpenState {
        val existing = stateRepository.current()
        if (existing.firstSeenOpenAt != null) return existing
        val openSince = door.lastChanged?.takeIf { it.isBefore(now) } ?: now
        val started = LeftOpenState(firstSeenOpenAt = openSince)
        stateRepository.save(started)
        diagnostics.record("Door seen open (open for ${Duration.between(openSince, now).toMinutes()} min); left-open timer started")
        return started
    }

    private suspend fun remind(state: LeftOpenState, openFor: Duration, now: Instant): LeftOpenOutcome {
        val sinceLastReminder = state.lastReminderAt?.let { Duration.between(it, now) }
        if (sinceLastReminder != null && sinceLastReminder < REMINDER_INTERVAL) {
            return LeftOpenOutcome.CheckAgainIn(REMINDER_INTERVAL.minus(sinceLastReminder))
        }
        stateRepository.save(state.copy(lastReminderAt = now))
        activityLog.record(AutomationEvent(AutomationEventType.LEFT_OPEN_REMINDED, clock.millis(), null, "Open ${openFor.toMinutes()} min"))
        return LeftOpenOutcome.Remind(openFor.toMinutes())
    }

    private suspend fun autoClose(door: GarageDoor, openFor: Duration): LeftOpenOutcome {
        return try {
            retry.run { controller.execute(door.id, DoorCommand.CLOSE) }
            stateRepository.clear()
            activityLog.record(AutomationEvent(AutomationEventType.CLOSED, clock.millis(), null, "Left open ${openFor.toMinutes()} min"))
            LeftOpenOutcome.AutoClosed
        } catch (error: GarageDoorControlException) {
            activityLog.record(AutomationEvent(AutomationEventType.FAILED, clock.millis(), null, error.message))
            LeftOpenOutcome.None
        }
    }

    private fun isOpen(door: GarageDoor): Boolean = door.state == DoorState.OPEN || door.state == DoorState.STOPPED

    private companion object {
        val REMINDER_INTERVAL: Duration = Duration.ofMinutes(30)
    }
}

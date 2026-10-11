package com.raf.garagedoor.automation

import com.raf.garagedoor.door.DoorCommand
import com.raf.garagedoor.door.DoorState
import com.raf.garagedoor.door.GarageDoor
import com.raf.garagedoor.door.GarageDoorControlException
import com.raf.garagedoor.door.GarageDoorController
import com.raf.garagedoor.door.TransientRetry
import java.time.Clock

/**
 * Door commands issued without the app on screen: notification buttons, the home-screen widget and
 * the voice command. [onOpened] starts the left-open timer for every one of them.
 */
class BackgroundDoorCommands(
    private val controller: GarageDoorController,
    private val retry: TransientRetry,
    private val activityLog: ActivityLogRepository,
    private val alerts: AutomationAlerts,
    private val clock: Clock,
    private val onOpened: suspend () -> Unit = {},
) {
    suspend fun run(command: DoorCommand, reason: String, showsResult: Boolean = true) {
        try {
            val door = retry.run { controller.listDoors() }.firstOrNull()
                ?: return recordFailure(command, "No garage door found on the myQ account")
            if (!isNeeded(command, door)) return record(alreadyThere(command), reason, showsResult)
            retry.run { controller.execute(door.id, command) }
            if (command == DoorCommand.OPEN) onOpened()
            record(if (command == DoorCommand.OPEN) AutomationEventType.OPENED else AutomationEventType.CLOSED, reason, showsResult)
        } catch (error: GarageDoorControlException) {
            recordFailure(command, error.message)
        }
    }

    private suspend fun record(type: AutomationEventType, reason: String, showsResult: Boolean) {
        val event = AutomationEvent(type, clock.millis(), distanceFeet = null, detail = reason)
        activityLog.record(event)
        val isResult = type == AutomationEventType.OPENED || type == AutomationEventType.CLOSED
        if (showsResult && isResult) alerts.showResult(event)
    }

    private suspend fun recordFailure(command: DoorCommand, detail: String?) {
        val event = AutomationEvent(AutomationEventType.FAILED, clock.millis(), distanceFeet = null, detail = detail)
        activityLog.record(event)
        alerts.showFailure(event, retryCommand = command)
    }

    private fun isNeeded(command: DoorCommand, door: GarageDoor): Boolean = when (command) {
        DoorCommand.OPEN -> door.state == DoorState.CLOSED
        DoorCommand.CLOSE -> door.state == DoorState.OPEN || door.state == DoorState.STOPPED
    }

    private fun alreadyThere(command: DoorCommand) =
        if (command == DoorCommand.OPEN) AutomationEventType.ALREADY_OPEN else AutomationEventType.ALREADY_CLOSED
}

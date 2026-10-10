package com.raf.garagedoor.widget

import com.raf.garagedoor.door.DoorCommand
import com.raf.garagedoor.door.DoorState
import com.raf.garagedoor.door.GarageDoor
import java.time.Duration
import java.time.Instant

/**
 * What the home-screen widget shows: the last door state seen from myQ, plus two short-lived
 * overlays. A movement overlay shows "Closing…" from the tap until myQ catches up (myQ keeps
 * reporting the old state for a few seconds), and a confirmation overlay asks for a second tap.
 */
data class WidgetDoorStatus(
    val state: DoorState? = null,
    val lastChanged: Instant? = null,
    val movement: DoorCommand? = null,
    val movementUntil: Instant? = null,
    val pendingConfirm: DoorCommand? = null,
    val confirmUntil: Instant? = null,
) {
    fun displayState(now: Instant): DoorState? = movingStateAt(now) ?: state

    fun isAwaitingConfirm(command: DoorCommand, now: Instant): Boolean =
        pendingConfirm == command && confirmUntil?.isAfter(now) == true

    fun awaitingConfirm(command: DoorCommand, now: Instant): WidgetDoorStatus =
        copy(pendingConfirm = command, confirmUntil = now.plus(CONFIRM_WINDOW))

    fun withoutConfirm(): WidgetDoorStatus = copy(pendingConfirm = null, confirmUntil = null)

    fun moving(command: DoorCommand, now: Instant): WidgetDoorStatus =
        withoutConfirm().copy(movement = command, movementUntil = now.plus(MOVEMENT_WINDOW))

    /** Keeps the movement overlay while myQ still reports the starting state; drops it once the door is seen moving or done. */
    fun withReported(door: GarageDoor, now: Instant): WidgetDoorStatus {
        val reported = copy(state = door.state, lastChanged = door.lastChanged)
        val isStaleReport = movingStateAt(now) != null && door.state.toggleCommand == movement
        return if (isStaleReport) reported else reported.copy(movement = null, movementUntil = null)
    }

    private fun movingStateAt(now: Instant): DoorState? {
        if (movement == null || movementUntil?.isAfter(now) != true) return null
        return if (movement == DoorCommand.OPEN) DoorState.OPENING else DoorState.CLOSING
    }

    companion object {
        val CONFIRM_WINDOW: Duration = Duration.ofSeconds(4)
        val MOVEMENT_WINDOW: Duration = Duration.ofSeconds(90)
    }
}

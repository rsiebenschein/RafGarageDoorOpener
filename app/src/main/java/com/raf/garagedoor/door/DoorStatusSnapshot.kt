package com.raf.garagedoor.door

import java.time.Duration
import java.time.Instant

/**
 * The door status shared by the app screen and the home-screen widget: the last state seen from
 * myQ, plus two short-lived overlays. The movement overlay shows "Closing…" from the moment any
 * command is accepted until myQ reports the result (this door's myQ status stays "open" until it is
 * fully closed, with no "closing" in between). The confirmation overlay is the widget's second tap.
 */
data class DoorStatusSnapshot(
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

    fun awaitingConfirm(command: DoorCommand, now: Instant): DoorStatusSnapshot =
        copy(pendingConfirm = command, confirmUntil = now.plus(CONFIRM_WINDOW))

    fun withoutConfirm(): DoorStatusSnapshot = copy(pendingConfirm = null, confirmUntil = null)

    fun moving(command: DoorCommand, now: Instant): DoorStatusSnapshot =
        withoutConfirm().copy(movement = command, movementUntil = now.plus(MOVEMENT_WINDOW))

    /** Keeps the movement overlay while myQ still reports the starting state; drops it once the door is seen moving or done. */
    fun withReported(door: GarageDoor, now: Instant): DoorStatusSnapshot {
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

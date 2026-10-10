package com.raf.garagedoor.door

import java.time.Instant

enum class DoorState {
    OPEN,
    CLOSED,
    OPENING,
    CLOSING,
    STOPPED,
    MOVING,
    UNKNOWN;

    val isMoving: Boolean get() = this == OPENING || this == CLOSING || this == MOVING

    /** What one tap on a door control should do in this state; `null` while moving or unknown. */
    val toggleCommand: DoorCommand?
        get() = when (this) {
            CLOSED -> DoorCommand.OPEN
            OPEN, STOPPED -> DoorCommand.CLOSE
            else -> null
        }
}

enum class DoorCommand {
    OPEN,
    CLOSE,
}

data class DoorId(val accountId: String, val serialNumber: String)

data class GarageDoor(
    val id: DoorId,
    val name: String,
    val model: String?,
    val state: DoorState,
    val isOnline: Boolean,
    val lastChanged: Instant?,
)

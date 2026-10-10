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

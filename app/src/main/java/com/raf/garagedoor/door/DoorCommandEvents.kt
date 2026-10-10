package com.raf.garagedoor.door

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class DoorCommandEvent(val doorId: DoorId, val command: DoorCommand)

/**
 * In-process announcement of every door command myQ accepted, whoever sent it (screen, notification
 * button, hands-free, left-open auto-close), so a visible door screen shows the movement at once.
 */
class DoorCommandEvents {
    private val mutableEvents = MutableSharedFlow<DoorCommandEvent>(extraBufferCapacity = BUFFER_SIZE)
    val events: SharedFlow<DoorCommandEvent> = mutableEvents.asSharedFlow()

    fun announce(doorId: DoorId, command: DoorCommand) {
        mutableEvents.tryEmit(DoorCommandEvent(doorId, command))
    }

    private companion object {
        const val BUFFER_SIZE = 8
    }
}

/** Wraps a controller so every accepted command is announced through [events]. */
class AnnouncingDoorController(
    private val delegate: GarageDoorController,
    private val events: DoorCommandEvents,
) : GarageDoorController by delegate {

    override suspend fun execute(door: DoorId, command: DoorCommand) {
        delegate.execute(door, command)
        events.announce(door, command)
    }
}

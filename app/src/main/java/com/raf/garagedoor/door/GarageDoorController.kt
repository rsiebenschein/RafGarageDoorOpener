package com.raf.garagedoor.door

/**
 * Backend-agnostic door control. The myQ cloud is the first implementation; a local controller
 * such as ratgdo can replace it without touching the UI or automation layers.
 */
interface GarageDoorController {
    suspend fun listDoors(): List<GarageDoor>

    suspend fun execute(door: DoorId, command: DoorCommand)
}

abstract class GarageDoorControlException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {
    /** True for failures worth retrying shortly, such as a network drop while switching Wi-Fi to cellular. */
    open val isTransient: Boolean get() = false
}

/** Marks failures that can only be resolved by the user signing in again. */
interface RequiresSignIn

package com.raf.garagedoor.ui.door

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.raf.garagedoor.door.DoorCommand
import com.raf.garagedoor.door.DoorId
import com.raf.garagedoor.door.DoorState
import com.raf.garagedoor.door.GarageDoor
import com.raf.garagedoor.door.GarageDoorControlException
import com.raf.garagedoor.door.GarageDoorController
import com.raf.garagedoor.ui.appContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.Instant

data class DoorUiState(
    val doors: List<GarageDoor> = emptyList(),
    val isLoading: Boolean = true,
    val pendingCommandDoor: DoorId? = null,
    val errorMessage: String? = null,
)

/** Polls door state while the screen is visible: slowly when idle, quickly while a door moves. */
class DoorViewModel(
    private val controller: GarageDoorController,
    private val clock: Clock,
) : ViewModel() {

    private val mutableState = MutableStateFlow(DoorUiState())
    val state: StateFlow<DoorUiState> = mutableState.asStateFlow()

    private var pollingJob: Job? = null
    private var fastPollUntil: Instant = Instant.MIN

    fun startPolling() {
        if (pollingJob?.isActive == true) return
        restartPolling(initialDelay = Duration.ZERO)
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun refreshNow() = restartPolling(initialDelay = Duration.ZERO)

    fun dismissError() = mutableState.update { it.copy(errorMessage = null) }

    fun toggle(door: GarageDoor) {
        val command = commandFor(door) ?: return
        viewModelScope.launch { sendCommand(door.id, command) }
    }

    private fun restartPolling(initialDelay: Duration) {
        stopPolling()
        pollingJob = viewModelScope.launch { pollRepeatedly(initialDelay) }
    }

    private suspend fun pollRepeatedly(initialDelay: Duration) {
        delay(initialDelay.toMillis())
        while (true) {
            refreshDoors()
            delay(nextPollDelay().toMillis())
        }
    }

    private fun nextPollDelay(): Duration {
        val isAnyDoorMoving = mutableState.value.doors.any { it.state.isMoving }
        val isFastPolling = isAnyDoorMoving || clock.instant().isBefore(fastPollUntil)
        return if (isFastPolling) FAST_POLL_INTERVAL else IDLE_POLL_INTERVAL
    }

    private suspend fun refreshDoors() {
        try {
            val doors = controller.listDoors()
            mutableState.update { it.copy(doors = doors, isLoading = false) }
        } catch (error: GarageDoorControlException) {
            showError(error)
        }
    }

    private suspend fun sendCommand(doorId: DoorId, command: DoorCommand) {
        mutableState.update { it.copy(pendingCommandDoor = doorId, errorMessage = null) }
        try {
            controller.execute(doorId, command)
            onCommandAccepted(doorId, command)
        } catch (error: GarageDoorControlException) {
            showError(error)
        } finally {
            mutableState.update { it.copy(pendingCommandDoor = null) }
        }
    }

    private fun onCommandAccepted(doorId: DoorId, command: DoorCommand) {
        val movingState = if (command == DoorCommand.OPEN) DoorState.OPENING else DoorState.CLOSING
        mutableState.update { state ->
            state.copy(doors = state.doors.map { if (it.id == doorId) it.copy(state = movingState) else it })
        }
        fastPollUntil = clock.instant().plus(FAST_POLL_WINDOW)
        restartPolling(initialDelay = FAST_POLL_INTERVAL)
    }

    private fun showError(error: GarageDoorControlException) =
        mutableState.update { it.copy(isLoading = false, errorMessage = error.message) }

    companion object {
        private val IDLE_POLL_INTERVAL: Duration = Duration.ofSeconds(30)
        private val FAST_POLL_INTERVAL: Duration = Duration.ofSeconds(3)
        private val FAST_POLL_WINDOW: Duration = Duration.ofSeconds(45)

        fun commandFor(door: GarageDoor): DoorCommand? = when {
            !door.isOnline -> null
            door.state == DoorState.CLOSED -> DoorCommand.OPEN
            door.state == DoorState.OPEN || door.state == DoorState.STOPPED -> DoorCommand.CLOSE
            else -> null
        }

        val Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                DoorViewModel(container.garageDoorController, container.clock)
            }
        }
    }
}

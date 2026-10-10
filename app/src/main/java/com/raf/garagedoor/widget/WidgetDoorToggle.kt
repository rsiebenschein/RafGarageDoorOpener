package com.raf.garagedoor.widget

import android.content.Context
import com.raf.garagedoor.automation.DepartureOnDoorOpen
import com.raf.garagedoor.automation.DoorCommandWorker
import com.raf.garagedoor.automation.leftopen.LeftOpenCheckScheduler
import com.raf.garagedoor.door.DoorCommand
import com.raf.garagedoor.door.DoorStatusSnapshot
import com.raf.garagedoor.door.DoorStatusSnapshotStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant

/**
 * A widget tap: the first tap asks for confirmation, a second tap within a few seconds sends the
 * command. Unknown or moving doors just get a status refresh. Opening from the widget starts the
 * departure check and left-open timer exactly like the app's button.
 */
class WidgetDoorToggle(
    private val context: Context,
    private val store: DoorStatusSnapshotStore,
    private val updater: WidgetUpdater,
    private val departureOnDoorOpen: DepartureOnDoorOpen,
    private val leftOpenChecks: LeftOpenCheckScheduler,
    private val clock: Clock,
    private val scope: CoroutineScope,
) {
    suspend fun onTap() {
        val now = clock.instant()
        val status = store.current()
        val command = status.displayState(now)?.toggleCommand ?: return updater.requestRefresh()
        if (status.isAwaitingConfirm(command, now)) send(command, now) else askForConfirmation(command, now)
    }

    private suspend fun send(command: DoorCommand, now: Instant) {
        store.update { it.moving(command, now) }
        updater.redraw()
        DoorCommandWorker.enqueue(context, command, REASON, showsResult = false)
        departureOnDoorOpen.onCommandAccepted(command)
        leftOpenChecks.onCommandAccepted(command)
    }

    private suspend fun askForConfirmation(command: DoorCommand, now: Instant) {
        val asking = store.current().awaitingConfirm(command, now)
        store.update { asking }
        updater.redraw()
        scope.launch { clearConfirmationWhenExpired(asking) }
    }

    /**
     * Runs outside the tap: Android delivers a widget's taps one at a time, so waiting inside the
     * tap would hold back the confirming second tap until the window had already closed.
     */
    private suspend fun clearConfirmationWhenExpired(asking: DoorStatusSnapshot) {
        delay(DoorStatusSnapshot.CONFIRM_WINDOW.toMillis())
        val expired = store.update { if (it.confirmUntil == asking.confirmUntil) it.withoutConfirm() else it }
        if (expired) updater.redraw()
    }

    private companion object {
        const val REASON = "From the widget"
    }
}

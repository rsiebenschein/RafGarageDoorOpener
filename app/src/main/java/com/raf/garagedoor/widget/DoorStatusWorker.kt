package com.raf.garagedoor.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.raf.garagedoor.GarageApplication
import com.raf.garagedoor.door.DoorCommand
import com.raf.garagedoor.door.DoorState
import com.raf.garagedoor.door.GarageDoorControlException
import com.raf.garagedoor.door.GarageDoorController
import com.raf.garagedoor.network.RoutineStatusCheck
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration

/**
 * Reads the door state from myQ for the widget: once, or every few seconds after a command until
 * the door reaches its new state. Reads go through the app's controller, which passes every result
 * on to the widget.
 */
class DoorStatusWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val controller = (applicationContext as GarageApplication).container.garageDoorController
        val follow = inputData.getString(KEY_FOLLOW)?.let { name -> DoorCommand.entries.firstOrNull { it.name == name } }
        if (follow == null) readState(controller) else followUntilSettled(controller, follow)
        return Result.success()
    }

    private suspend fun followUntilSettled(controller: GarageDoorController, command: DoorCommand) {
        val target = if (command == DoorCommand.OPEN) DoorState.OPEN else DoorState.CLOSED
        withTimeoutOrNull(FOLLOW_WINDOW.toMillis()) {
            do {
                delay(FOLLOW_INTERVAL.toMillis())
            } while (readState(controller) != target)
        }
    }

    /** A failed read is skipped; the next one (or the next left-open check) catches up. */
    private suspend fun readState(controller: GarageDoorController): DoorState? = try {
        RoutineStatusCheck.run { controller.listDoors() }.firstOrNull()?.state
    } catch (unreachable: GarageDoorControlException) {
        null
    }

    companion object {
        private const val KEY_FOLLOW = "follow"
        private const val UNIQUE_NAME = "door-status"
        private val FOLLOW_INTERVAL: Duration = Duration.ofSeconds(3)
        private val FOLLOW_WINDOW: Duration = Duration.ofSeconds(90)

        fun enqueue(context: Context, follow: DoorCommand?) {
            val request = OneTimeWorkRequestBuilder<DoorStatusWorker>()
                .setInputData(workDataOf(KEY_FOLLOW to follow?.name))
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}

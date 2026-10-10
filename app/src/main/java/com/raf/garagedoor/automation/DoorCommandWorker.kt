package com.raf.garagedoor.automation

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.raf.garagedoor.GarageApplication
import com.raf.garagedoor.door.DoorCommand

/** Runs a door command from a notification button, surviving the app being closed and waiting for network. */
class DoorCommandWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val command = inputData.getString(KEY_COMMAND)?.let { name -> DoorCommand.entries.firstOrNull { it.name == name } }
            ?: return Result.failure()
        val container = (applicationContext as GarageApplication).container
        val showsResult = inputData.getBoolean(KEY_SHOWS_RESULT, true)
        container.backgroundDoorCommands.run(command, inputData.getString(KEY_REASON) ?: DEFAULT_REASON, showsResult)
        return Result.success()
    }

    companion object {
        private const val KEY_COMMAND = "command"
        private const val KEY_REASON = "reason"
        private const val KEY_SHOWS_RESULT = "shows_result"
        private const val UNIQUE_NAME = "door-command"
        private const val DEFAULT_REASON = "From a notification"

        /** [showsResult] is off for the widget, which shows the outcome itself; failures always notify. */
        fun enqueue(context: Context, command: DoorCommand, reason: String, showsResult: Boolean = true) {
            val request = OneTimeWorkRequestBuilder<DoorCommandWorker>()
                .setInputData(workDataOf(KEY_COMMAND to command.name, KEY_REASON to reason, KEY_SHOWS_RESULT to showsResult))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}

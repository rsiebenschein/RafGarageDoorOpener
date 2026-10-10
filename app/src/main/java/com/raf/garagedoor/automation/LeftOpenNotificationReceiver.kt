package com.raf.garagedoor.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.raf.garagedoor.GarageApplication
import com.raf.garagedoor.door.DoorCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Handles the Close and Ignore buttons on the "door left open" reminder. */
class LeftOpenNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as GarageApplication).container
        when (intent.action) {
            ACTION_CLOSE -> DoorCommandWorker.enqueue(context, DoorCommand.CLOSE, "From the left-open reminder")
            ACTION_IGNORE -> ignoreUntilDoorCloses(container.leftOpenState::ignoreCurrentSession)
            else -> return
        }
        container.automationNotifications.dismissLeftOpenReminder()
    }

    private fun ignoreUntilDoorCloses(ignore: suspend () -> Unit) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                ignore()
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_CLOSE = "com.raf.garagedoor.action.LEFT_OPEN_CLOSE"
        const val ACTION_IGNORE = "com.raf.garagedoor.action.LEFT_OPEN_IGNORE"
    }
}

package com.raf.garagedoor.automation

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.raf.garagedoor.door.DoorCommand

/** "Open now" / "Close now" buttons on failure notifications hand the command to [DoorCommandWorker]. */
class DoorCommandActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val command = intent.getStringExtra(EXTRA_COMMAND)?.let { name -> DoorCommand.entries.firstOrNull { it.name == name } } ?: return
        DoorCommandWorker.enqueue(context, command, "Retried from a notification")
        NotificationManagerCompat.from(context).cancel(intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0))
    }

    companion object {
        private const val EXTRA_COMMAND = "command"
        private const val EXTRA_NOTIFICATION_ID = "notification_id"

        fun pendingIntent(context: Context, command: DoorCommand, notificationId: Int): PendingIntent = PendingIntent.getBroadcast(
            context,
            command.ordinal,
            Intent(context, DoorCommandActionReceiver::class.java)
                .putExtra(EXTRA_COMMAND, command.name)
                .putExtra(EXTRA_NOTIFICATION_ID, notificationId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

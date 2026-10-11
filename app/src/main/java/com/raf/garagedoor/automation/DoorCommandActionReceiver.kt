package com.raf.garagedoor.automation

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.raf.garagedoor.door.DoorCommand

/**
 * Door buttons on notifications ("Open now" / "Close now" after a failure, and "Open garage" on the
 * away watch, which also works from the lock screen) hand the command to [DoorCommandWorker].
 */
class DoorCommandActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val command = intent.getStringExtra(EXTRA_COMMAND)?.let { name -> DoorCommand.entries.firstOrNull { it.name == name } } ?: return
        DoorCommandWorker.enqueue(context, command, intent.getStringExtra(EXTRA_REASON) ?: DEFAULT_REASON)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, KEEP_NOTIFICATION)
        if (notificationId != KEEP_NOTIFICATION) NotificationManagerCompat.from(context).cancel(notificationId)
    }

    companion object {
        private const val EXTRA_COMMAND = "command"
        private const val EXTRA_NOTIFICATION_ID = "notification_id"
        private const val EXTRA_REASON = "reason"
        private const val DEFAULT_REASON = "Retried from a notification"
        private const val KEEP_NOTIFICATION = 0
        private const val AWAY_OPEN_REQUEST_CODE = 40

        /** Retry button: dismisses its notification once tapped. */
        fun pendingIntent(context: Context, command: DoorCommand, notificationId: Int): PendingIntent =
            broadcast(context, command.ordinal, intentFor(context, command, DEFAULT_REASON).putExtra(EXTRA_NOTIFICATION_ID, notificationId))

        /** "Open garage" on the ongoing away-watch notification, which must stay put. */
        fun awayOpenIntent(context: Context): PendingIntent =
            broadcast(context, AWAY_OPEN_REQUEST_CODE, intentFor(context, DoorCommand.OPEN, "From the away notification"))

        private fun intentFor(context: Context, command: DoorCommand, reason: String): Intent =
            Intent(context, DoorCommandActionReceiver::class.java)
                .putExtra(EXTRA_COMMAND, command.name)
                .putExtra(EXTRA_REASON, reason)

        private fun broadcast(context: Context, requestCode: Int, intent: Intent): PendingIntent =
            PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}

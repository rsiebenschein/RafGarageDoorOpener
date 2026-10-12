package com.raf.garagedoor.automation

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.raf.garagedoor.MainActivity
import com.raf.garagedoor.R
import com.raf.garagedoor.door.DoorCommand

class AutomationNotifications(private val context: Context) : AutomationAlerts {

    private val manager = NotificationManagerCompat.from(context)

    fun createChannels() {
        val systemManager = context.getSystemService(NotificationManager::class.java) ?: return
        systemManager.createNotificationChannel(
            NotificationChannel(TRACKING_CHANNEL, context.getString(R.string.channel_tracking), NotificationManager.IMPORTANCE_LOW),
        )
        systemManager.createNotificationChannel(
            NotificationChannel(ACTIONS_CHANNEL, context.getString(R.string.channel_actions), NotificationManager.IMPORTANCE_HIGH),
        )
        systemManager.createNotificationChannel(
            NotificationChannel(LEFT_OPEN_CHANNEL, context.getString(R.string.channel_left_open), NotificationManager.IMPORTANCE_HIGH),
        )
    }

    /** The away watch also offers "Open garage", shown on the lock screen so it works without unlocking. */
    fun trackingNotification(purpose: TrackingPurpose): Notification {
        val builder = NotificationCompat.Builder(context, TRACKING_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_garage)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(trackingTextFor(purpose)))
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setSilent(true)
        if (purpose == TrackingPurpose.ARRIVAL) {
            builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(0, context.getString(R.string.away_open_action), DoorCommandActionReceiver.awayOpenIntent(context))
        }
        return builder.build()
    }

    /** Ongoing notification of the voice service; says whether the microphone is open right now. */
    fun voiceNotification(isListening: Boolean): Notification {
        val text = context.getString(if (isListening) R.string.voice_listening else R.string.voice_waiting)
        return NotificationCompat.Builder(context, TRACKING_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_garage)
            .setContentTitle(context.getString(if (isListening) R.string.voice_listening_title else R.string.voice_waiting_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openAppIntent())
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    fun updateVoiceNotification(isListening: Boolean) = notifyIfAllowed(VOICE_NOTIFICATION_ID, voiceNotification(isListening))

    /** Android only allows the microphone service to start from the app, so after a restart a tap is needed. */
    fun showVoicePaused() {
        val notification = actionNotificationBuilder(context.getString(R.string.voice_paused_title))
            .setContentText(context.getString(R.string.voice_paused_body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        notifyIfAllowed(VOICE_PAUSED_NOTIFICATION_ID, notification)
    }

    fun dismissVoicePaused() = manager.cancel(VOICE_PAUSED_NOTIFICATION_ID)

    override fun showCountdown(decision: DoorDecision, seconds: Int) {
        val title = context.getString(if (decision == DoorDecision.OPEN) R.string.countdown_opening else R.string.countdown_closing, seconds)
        val notification = actionNotificationBuilder(title)
            .addAction(0, context.getString(R.string.countdown_cancel), cancelCountdownIntent())
            .setTimeoutAfter(seconds * MILLIS_PER_SECOND)
            .build()
        notifyIfAllowed(COUNTDOWN_NOTIFICATION_ID, notification)
    }

    override fun dismissCountdown() = manager.cancel(COUNTDOWN_NOTIFICATION_ID)

    /** High-importance reminder that the door is still open, with Close and Ignore actions. */
    fun showLeftOpenReminder(openMinutes: Long) {
        val notification = actionNotificationBuilder(context.getString(R.string.left_open_title), LEFT_OPEN_CHANNEL)
            .setContentText(context.getString(R.string.left_open_body, openMinutes))
            .addAction(0, context.getString(R.string.left_open_close), leftOpenActionIntent(LeftOpenNotificationReceiver.ACTION_CLOSE, CLOSE_REQUEST_CODE))
            .addAction(0, context.getString(R.string.left_open_ignore), leftOpenActionIntent(LeftOpenNotificationReceiver.ACTION_IGNORE, IGNORE_REQUEST_CODE))
            .setOngoing(false)
            .build()
        notifyIfAllowed(LEFT_OPEN_NOTIFICATION_ID, notification)
    }

    fun showLeftOpenAutoClosed() {
        val notification = actionNotificationBuilder(context.getString(R.string.left_open_autoclosed_title))
            .setContentText(context.getString(R.string.left_open_autoclosed_body))
            .build()
        notifyIfAllowed(LEFT_OPEN_NOTIFICATION_ID, notification)
    }

    fun dismissLeftOpenReminder() = manager.cancel(LEFT_OPEN_NOTIFICATION_ID)

    override fun showFailure(event: AutomationEvent, retryCommand: DoorCommand) {
        val label = context.getString(if (retryCommand == DoorCommand.OPEN) R.string.retry_open_now else R.string.retry_close_now)
        val notification = actionNotificationBuilder(context.getString(R.string.result_failed))
            .setContentText(event.detail)
            .addAction(0, label, DoorCommandActionReceiver.pendingIntent(context, retryCommand, RESULT_NOTIFICATION_ID))
            .build()
        notifyIfAllowed(RESULT_NOTIFICATION_ID, notification)
    }

    override fun showResult(event: AutomationEvent) {
        val title = context.getString(resultTitleFor(event.type))
        val body = event.detail ?: event.distanceFeet?.let { context.getString(R.string.result_distance, it) }
        notifyIfAllowed(RESULT_NOTIFICATION_ID, actionNotificationBuilder(title).setContentText(body).build())
    }

    private fun actionNotificationBuilder(title: String, channel: String = ACTIONS_CHANNEL): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_garage)
            .setContentTitle(title)
            .setContentIntent(openAppIntent())
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

    private fun notifyIfAllowed(id: Int, notification: Notification) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (granted) manager.notify(id, notification)
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        OPEN_APP_REQUEST_CODE,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun cancelCountdownIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        CANCEL_REQUEST_CODE,
        Intent(context, CountdownCancelReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun leftOpenActionIntent(action: String, requestCode: Int): PendingIntent = PendingIntent.getBroadcast(
        context,
        requestCode,
        Intent(context, LeftOpenNotificationReceiver::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE,
    )

    @StringRes
    private fun trackingTextFor(purpose: TrackingPurpose): Int = when (purpose) {
        TrackingPurpose.ARRIVAL -> R.string.tracking_arrival
        TrackingPurpose.DEPARTURE -> R.string.tracking_departure
    }

    @StringRes
    private fun resultTitleFor(type: AutomationEventType): Int = when (type) {
        AutomationEventType.OPENED -> R.string.result_opened
        AutomationEventType.CLOSED -> R.string.result_closed
        AutomationEventType.CANCELLED -> R.string.result_cancelled
        else -> R.string.result_failed
    }

    companion object {
        const val VOICE_NOTIFICATION_ID = 1005
        private const val VOICE_PAUSED_NOTIFICATION_ID = 1006
        private const val TRACKING_CHANNEL = "presence_tracking"
        private const val ACTIONS_CHANNEL = "door_actions"
        private const val COUNTDOWN_NOTIFICATION_ID = 1002
        private const val RESULT_NOTIFICATION_ID = 1003
        private const val OPEN_APP_REQUEST_CODE = 20
        private const val CANCEL_REQUEST_CODE = 21
        private const val CLOSE_REQUEST_CODE = 22
        private const val IGNORE_REQUEST_CODE = 23
        private const val LEFT_OPEN_NOTIFICATION_ID = 1004
        private const val LEFT_OPEN_CHANNEL = "left_open"
        private const val MILLIS_PER_SECOND = 1_000L
    }
}

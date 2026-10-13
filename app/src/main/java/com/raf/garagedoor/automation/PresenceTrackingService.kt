package com.raf.garagedoor.automation

import android.app.ForegroundServiceStartNotAllowedException
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.raf.garagedoor.GarageApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Short-lived location foreground service; Android requires one for precise GPS while the app is closed. */
class PresenceTrackingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: Job? = null
    private var activePurpose: TrackingPurpose? = null
    private val container get() = (application as GarageApplication).container

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val purpose = intent?.getStringExtra(EXTRA_PURPOSE)?.let(TrackingPurpose::valueOf)
        val isRunning = session?.isActive == true
        val shownPurpose = if (isRunning) activePurpose else purpose
        if (purpose == null || shownPurpose == null || !enterForeground(shownPurpose)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (isRunning) recordIgnoredRequest(purpose) else startSession(purpose)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isSessionRunning = false
        scope.cancel()
        super.onDestroy()
    }

    private fun enterForeground(purpose: TrackingPurpose): Boolean = try {
        val notification = container.automationNotifications.trackingNotification(purpose)
        ServiceCompat.startForeground(this, TRACKING_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        true
    } catch (notAllowed: ForegroundServiceStartNotAllowedException) {
        false
    } catch (locationPermissionMissing: SecurityException) {
        false
    }

    /**
     * Never replace a running session: cancelling it could drop a door command mid-flight, and the
     * running session already watches the zone the new trigger cares about.
     */
    private fun recordIgnoredRequest(purpose: TrackingPurpose) {
        scope.launch { container.diagnosticsLog.record("$purpose request ignored: $activePurpose session already running") }
    }

    private fun startSession(purpose: TrackingPurpose) {
        activePurpose = purpose
        isSessionRunning = true
        session = scope.launch {
            val next = purpose.followUpAfter(container.presenceTracker.runSession(purpose))
            if (next != null && enterForeground(next)) startSession(next) else stopSelf()
        }
    }

    companion object {
        const val TRACKING_NOTIFICATION_ID = 1001

        /** Lives in the app process, so it is false again whenever the process has been restarted. */
        @Volatile
        var isSessionRunning = false
            private set
        private const val EXTRA_PURPOSE = "purpose"

        /** Returns `false` when Android refuses a background start (e.g. no qualifying wake-up event). */
        fun start(context: Context, purpose: TrackingPurpose): Boolean = try {
            val intent = Intent(context, PresenceTrackingService::class.java).putExtra(EXTRA_PURPOSE, purpose.name)
            ContextCompat.startForegroundService(context, intent)
            true
        } catch (notAllowed: ForegroundServiceStartNotAllowedException) {
            false
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PresenceTrackingService::class.java))
        }
    }
}

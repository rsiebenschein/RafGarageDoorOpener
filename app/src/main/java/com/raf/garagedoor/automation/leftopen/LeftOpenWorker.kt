package com.raf.garagedoor.automation.leftopen

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.raf.garagedoor.AppContainer
import com.raf.garagedoor.GarageApplication
import com.raf.garagedoor.automation.AutomationSettings
import com.raf.garagedoor.automation.AutomationSettingsRepository
import com.raf.garagedoor.automation.LeftOpenAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.launch
import java.time.Duration

/**
 * Background check for a door left open. myQ never notifies this app, so it asks myQ at least as
 * often as the left-open limit (and at least every 15 minutes). Because some check always falls
 * inside the limit after the door opens, and the open time comes from myQ, the reminder lands on
 * time even for doors opened with the wall button. A 15-minute periodic job restarts the chain of
 * one-off checks if Android ever drops it.
 */
class LeftOpenWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as GarageApplication).container
        val outcome = container.leftOpenMonitor.check()
        showOutcome(container, outcome)
        scheduleNextCheck(container.automationSettings.current(), outcome)
        return Result.success()
    }

    private fun showOutcome(container: AppContainer, outcome: LeftOpenOutcome) {
        when (outcome) {
            is LeftOpenOutcome.Remind -> container.automationNotifications.showLeftOpenReminder(outcome.openMinutes)
            LeftOpenOutcome.AutoClosed -> container.automationNotifications.showLeftOpenAutoClosed()
            is LeftOpenOutcome.CheckAgainIn, LeftOpenOutcome.None -> Unit
        }
    }

    /** Last step, because replacing the queued check also cancels this run if it is that check. */
    private fun scheduleNextCheck(settings: AutomationSettings, outcome: LeftOpenOutcome) {
        if (settings.leftOpenAction == LeftOpenAction.OFF) return
        val poll = pollIntervalFor(settings)
        val dueSooner = (outcome as? LeftOpenOutcome.CheckAgainIn)?.delay?.takeIf { it < poll }
        checkAfter(applicationContext, dueSooner ?: poll)
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "left-open-monitor"
        private const val NEXT_CHECK_WORK_NAME = "left-open-deadline"
        private val BACKSTOP_INTERVAL: Duration = Duration.ofMinutes(15)

        /** Lands just after a deadline rather than a hair before it. */
        private val DEADLINE_SLACK: Duration = Duration.ofSeconds(5)

        /** Queues one check at [delay] from now, replacing any earlier one-off check. */
        fun checkAfter(context: Context, delay: Duration, policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
            val request = OneTimeWorkRequestBuilder<LeftOpenWorker>().setInitialDelay(delay.plus(DEADLINE_SLACK)).build()
            WorkManager.getInstance(context).enqueueUniqueWork(NEXT_CHECK_WORK_NAME, policy, request)
        }

        /** Never longer than the limit, so a check always falls between the door opening and the deadline. */
        fun pollIntervalFor(settings: AutomationSettings): Duration =
            minOf(Duration.ofMinutes(settings.leftOpenMinutes.toLong()), BACKSTOP_INTERVAL)

        /**
         * Schedules or cancels checks whenever the left-open setting changes. The first value comes
         * from app start-up, which is often the worker itself waking the app, so it must keep any
         * queued or running check instead of replacing (and cancelling) it.
         */
        fun observe(context: Context, settings: AutomationSettingsRepository, scope: CoroutineScope) {
            scope.launch {
                settings.settings.map { it.leftOpenAction to it.leftOpenMinutes }.distinctUntilChanged().withIndex().collect { (index, _) ->
                    sync(context, settings.current(), isStartup = index == 0)
                }
            }
        }

        private fun sync(context: Context, settings: AutomationSettings, isStartup: Boolean) {
            val workManager = WorkManager.getInstance(context)
            if (settings.leftOpenAction == LeftOpenAction.OFF) {
                workManager.cancelUniqueWork(PERIODIC_WORK_NAME)
                workManager.cancelUniqueWork(NEXT_CHECK_WORK_NAME)
                return
            }
            val backstop = PeriodicWorkRequestBuilder<LeftOpenWorker>(BACKSTOP_INTERVAL).build()
            workManager.enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, backstop)
            checkAfter(context, pollIntervalFor(settings), if (isStartup) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.REPLACE)
        }
    }
}

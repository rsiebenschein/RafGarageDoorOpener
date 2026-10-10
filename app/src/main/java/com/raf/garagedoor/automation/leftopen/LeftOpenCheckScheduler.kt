package com.raf.garagedoor.automation.leftopen

import android.content.Context
import com.raf.garagedoor.automation.AutomationSettingsRepository
import com.raf.garagedoor.automation.LeftOpenAction
import com.raf.garagedoor.door.DoorCommand
import java.time.Duration

/**
 * When the app or hands-free opens the door, the left-open limit starts now, so a check is queued
 * for exactly that moment instead of waiting for the periodic check to notice the open door.
 */
class LeftOpenCheckScheduler(private val context: Context, private val settings: AutomationSettingsRepository) {

    suspend fun onCommandAccepted(command: DoorCommand) {
        if (command == DoorCommand.OPEN) onDoorOpened()
    }

    suspend fun onDoorOpened() {
        val current = settings.current()
        if (current.leftOpenAction == LeftOpenAction.OFF) return
        LeftOpenWorker.checkAfter(context, Duration.ofMinutes(current.leftOpenMinutes.toLong()))
    }
}

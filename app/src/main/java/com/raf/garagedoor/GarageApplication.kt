package com.raf.garagedoor

import android.app.Application
import com.raf.garagedoor.automation.leftopen.LeftOpenWorker

class GarageApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        container.automationNotifications.createChannels()
        container.automationCoordinator.observe(container.applicationScope)
        LeftOpenWorker.observe(this, container.automationSettings, container.applicationScope)
    }
}

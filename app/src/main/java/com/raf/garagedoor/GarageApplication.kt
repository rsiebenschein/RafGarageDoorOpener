package com.raf.garagedoor

import android.app.Application
import com.raf.garagedoor.automation.leftopen.LeftOpenWorker
import kotlinx.coroutines.launch

class GarageApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        container.automationNotifications.createChannels()
        container.automationCoordinator.observe(container.applicationScope)
        LeftOpenWorker.observe(this, container.automationSettings, container.applicationScope)
        container.applicationScope.launch { container.doorCommandEvents.events.collect(container.widgetUpdater::onCommandAccepted) }
    }
}

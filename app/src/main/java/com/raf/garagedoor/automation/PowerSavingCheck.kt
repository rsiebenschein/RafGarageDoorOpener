package com.raf.garagedoor.automation

import android.content.Context
import android.os.PowerManager

/**
 * Field test 4: the phone's Power saving mode turned GPS off whenever the screen was off, so the
 * return was only seen from Wi-Fi positions and the door opened at 45 ft instead of ~135 ft. No
 * app can override that, so the app can only tell the user.
 */
class PowerSavingCheck(context: Context) {

    private val powerManager = context.getSystemService(PowerManager::class.java)

    /** True while Power saving is on and limits location with the screen off. */
    fun isLimitingLocation(): Boolean {
        val manager = powerManager ?: return false
        return manager.isPowerSaveMode && manager.locationPowerSaveMode in LIMITING_MODES
    }

    private companion object {
        val LIMITING_MODES = setOf(
            PowerManager.LOCATION_MODE_GPS_DISABLED_WHEN_SCREEN_OFF,
            PowerManager.LOCATION_MODE_ALL_DISABLED_WHEN_SCREEN_OFF,
            PowerManager.LOCATION_MODE_THROTTLE_REQUESTS_WHEN_SCREEN_OFF,
        )
    }
}

package com.raf.garagedoor.automation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import androidx.core.content.ContextCompat

data class AutomationPermissionStatus(
    val preciseLocation: Boolean,
    val backgroundLocation: Boolean,
    val activityRecognition: Boolean,
    val notifications: Boolean,
    val batteryUnrestricted: Boolean,
    val bluetoothConnect: Boolean,
) {
    /** The minimum for system geofences to wake the app while it is closed. */
    val canTrackInBackground: Boolean get() = preciseLocation && backgroundLocation
}

class AutomationPermissions(private val context: Context) {

    private val powerManager = context.getSystemService(PowerManager::class.java)

    fun status() = AutomationPermissionStatus(
        preciseLocation = isGranted(Manifest.permission.ACCESS_FINE_LOCATION),
        backgroundLocation = isGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
        activityRecognition = isGranted(Manifest.permission.ACTIVITY_RECOGNITION),
        notifications = isGranted(Manifest.permission.POST_NOTIFICATIONS),
        batteryUnrestricted = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true,
        bluetoothConnect = isGranted(Manifest.permission.BLUETOOTH_CONNECT),
    )

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

package com.raf.garagedoor.ui.automation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri

/** One launcher per permission the hands-free checklist can ask for. */
class PermissionRequests(
    val preciseLocation: () -> Unit,
    val backgroundLocation: () -> Unit,
    val activityRecognition: () -> Unit,
    val notifications: () -> Unit,
    val bluetooth: () -> Unit,
    val batteryUnrestricted: () -> Unit,
)

@Composable
fun rememberPermissionRequests(onResult: () -> Unit): PermissionRequests {
    val context = LocalContext.current
    return PermissionRequests(
        preciseLocation = rememberPermissionRequest(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            onResult,
        ),
        backgroundLocation = rememberPermissionRequest(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), onResult),
        activityRecognition = rememberPermissionRequest(arrayOf(Manifest.permission.ACTIVITY_RECOGNITION), onResult),
        notifications = rememberPermissionRequest(arrayOf(Manifest.permission.POST_NOTIFICATIONS), onResult),
        bluetooth = rememberPermissionRequest(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), onResult),
        batteryUnrestricted = { requestBatteryUnrestricted(context) },
    )
}

@Composable
private fun rememberPermissionRequest(permissions: Array<String>, onResult: () -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { onResult() }
    return { launcher.launch(permissions) }
}

/** Samsung's "Unrestricted" battery mode; the result is picked up when the screen resumes. */
private fun requestBatteryUnrestricted(context: Context) {
    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri())
    context.startActivity(intent)
}

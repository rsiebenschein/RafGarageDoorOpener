package com.raf.garagedoor.ui.geofence

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

/** Foreground location permission state plus an action that asks for it when needed, then locates. */
class LocationAccess(val isGranted: Boolean, val requestAndLocate: () -> Unit)

@Composable
fun rememberLocationAccess(onGranted: () -> Unit, onDenied: () -> Unit): LocationAccess {
    val context = LocalContext.current
    var isGranted by remember { mutableStateOf(hasLocationPermission(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        isGranted = results.values.any { it }
        if (isGranted) onGranted() else onDenied()
    }
    return LocationAccess(isGranted) {
        if (isGranted) onGranted() else launcher.launch(LOCATION_PERMISSIONS)
    }
}

private fun hasLocationPermission(context: Context): Boolean = LOCATION_PERMISSIONS.any {
    ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
}

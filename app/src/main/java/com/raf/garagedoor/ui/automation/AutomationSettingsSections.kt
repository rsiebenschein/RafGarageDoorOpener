package com.raf.garagedoor.ui.automation

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.raf.garagedoor.R
import com.raf.garagedoor.automation.AutomationPermissionStatus
import com.raf.garagedoor.automation.AutomationSettings
import com.raf.garagedoor.automation.CountdownLimits
import com.raf.garagedoor.automation.PresenceZone
import com.raf.garagedoor.geofence.metersToFeet
import kotlin.math.roundToInt

private const val FEET_PER_MILE = 5_280

@Composable
fun presenceSummary(state: AutomationUiState): String {
    if (state.geofence == null) return stringResource(R.string.automation_needs_location)
    if (!state.settings.isEnabled) return stringResource(R.string.automation_off)
    val zone = stringResource(
        when (state.presence.zone) {
            PresenceZone.HOME -> R.string.presence_home
            PresenceZone.AWAY -> R.string.presence_away
            PresenceZone.UNKNOWN -> R.string.presence_unknown
        },
    )
    val distance = state.presence.lastDistanceMeters?.let { formatDistance(metersToFeet(it)) }
    return distance?.let { stringResource(R.string.presence_with_distance, zone, it) } ?: zone
}

@Composable
private fun formatDistance(feet: Int): String =
    if (feet < FEET_PER_MILE) {
        stringResource(R.string.distance_feet, feet)
    } else {
        stringResource(R.string.distance_miles, feet.toDouble() / FEET_PER_MILE)
    }

/** Field test 4: with Power saving on, GPS is off while the screen is off, so arrivals open late. */
@Composable
fun PowerSavingWarningCard() {
    val context = LocalContext.current
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.power_saving_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(stringResource(R.string.power_saving_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)) }) {
                Text(stringResource(R.string.power_saving_open_settings))
            }
        }
    }
}

@Composable
fun SetupChecklistCard(permissions: AutomationPermissionStatus, requests: PermissionRequests) {
    SettingsCard {
        Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.titleMedium)
        SetupRow(R.string.setup_precise_location, R.string.setup_precise_location_detail, permissions.preciseLocation, onAllow = requests.preciseLocation)
        SetupRow(
            R.string.setup_background_location,
            R.string.setup_background_location_detail,
            permissions.backgroundLocation,
            isAllowEnabled = permissions.preciseLocation,
            onAllow = requests.backgroundLocation,
        )
        SetupRow(R.string.setup_activity, R.string.setup_activity_detail, permissions.activityRecognition, onAllow = requests.activityRecognition)
        SetupRow(R.string.setup_notifications, R.string.setup_notifications_detail, permissions.notifications, onAllow = requests.notifications)
        SetupRow(R.string.setup_battery, R.string.setup_battery_detail, permissions.batteryUnrestricted, onAllow = requests.batteryUnrestricted)
    }
}

@Composable
private fun SetupRow(title: Int, detail: Int, isDone: Boolean, isAllowEnabled: Boolean = true, onAllow: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (isDone) {
            Icon(Icons.Filled.CheckCircle, contentDescription = stringResource(R.string.setup_done), tint = MaterialTheme.colorScheme.primary)
        } else {
            OutlinedButton(onClick = onAllow, enabled = isAllowEnabled) { Text(stringResource(R.string.setup_allow)) }
        }
    }
}

@Composable
fun CountdownCard(settings: AutomationSettings, onOpenChange: (Int) -> Unit, onCloseChange: (Int) -> Unit) {
    SettingsCard {
        Text(stringResource(R.string.countdown_title), style = MaterialTheme.typography.titleMedium)
        CountdownSlider(R.string.countdown_open_label, settings.openCountdownSeconds, onOpenChange)
        CountdownSlider(R.string.countdown_close_label, settings.closeCountdownSeconds, onCloseChange)
        Text(
            text = stringResource(R.string.countdown_explanation),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CountdownSlider(label: Int, seconds: Int, onChange: (Int) -> Unit) {
    val valueText = if (seconds == 0) stringResource(R.string.countdown_none) else stringResource(R.string.countdown_seconds, seconds)
    Text(stringResource(label, valueText), style = MaterialTheme.typography.bodyLarge)
    Slider(
        value = seconds.toFloat(),
        onValueChange = { onChange(it.roundToInt()) },
        valueRange = 0f..CountdownLimits.MAX_SECONDS.toFloat(),
        steps = CountdownLimits.MAX_SECONDS - 1,
    )
}

@Composable
fun CarBluetoothCard(state: AutomationUiState, actions: AutomationScreenActions) {
    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.car_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.car_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = state.settings.requireCarBluetooth, onCheckedChange = actions.onRequireCarChange)
        }
        if (state.settings.requireCarBluetooth) CarPicker(state, actions)
    }
}

@Composable
fun VoiceCommandCard(state: AutomationUiState, actions: AutomationScreenActions) {
    val hasMicrophone = state.capabilities.permissions.microphone
    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.voice_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.voice_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = state.settings.voiceCommandsEnabled, onCheckedChange = actions.onVoiceCommandsChange, enabled = hasMicrophone)
        }
        if (!hasMicrophone) OutlinedButton(onClick = actions.permissionRequests.microphone) { Text(stringResource(R.string.voice_allow_microphone)) }
    }
}

/** Applies to every myQ request in the app, not just hands-free, so the left-open and widget checks benefit too. */
@Composable
fun MobileDataCard(useMobileData: Boolean, onChange: (Boolean) -> Unit) {
    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.mobile_data_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.mobile_data_detail), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = useMobileData, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun CarPicker(state: AutomationUiState, actions: AutomationScreenActions) {
    if (!state.capabilities.permissions.bluetoothConnect) {
        OutlinedButton(onClick = actions.permissionRequests.bluetooth) { Text(stringResource(R.string.car_allow_bluetooth)) }
        return
    }
    var isMenuOpen by remember { mutableStateOf(false) }
    val selected = state.settings.car
    Box {
        OutlinedButton(onClick = { isMenuOpen = true }) {
            Text(selected?.name ?: stringResource(R.string.car_choose))
        }
        DropdownMenu(expanded = isMenuOpen, onDismissRequest = { isMenuOpen = false }) {
            state.capabilities.pairedDevices.forEach { device ->
                DropdownMenuItem(text = { Text(device.name) }, onClick = { isMenuOpen = false; actions.onCarSelected(device) })
            }
        }
    }
    selected?.let { CarConnectionStatus(state.isCarConnected) }
}

@Composable
private fun CarConnectionStatus(isConnected: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (isConnected) Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Text(
            text = stringResource(if (isConnected) R.string.car_connected else R.string.car_not_connected),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

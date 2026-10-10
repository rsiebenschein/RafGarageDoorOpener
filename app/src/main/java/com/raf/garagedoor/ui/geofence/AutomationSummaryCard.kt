package com.raf.garagedoor.ui.geofence

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.raf.garagedoor.R
import com.raf.garagedoor.geofence.GarageGeofence

@Composable
fun AutomationSummaryCard(
    geofence: GarageGeofence?,
    isAutomationEnabled: Boolean,
    onEditLocation: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.automation_card_title), style = MaterialTheme.typography.titleMedium)
            if (geofence == null) {
                AutomationNotSetUp(onEditLocation)
            } else {
                AutomationConfigured(geofence, isAutomationEnabled, onEditLocation, onOpenSettings)
            }
        }
    }
}

@Composable
private fun AutomationNotSetUp(onSetUp: () -> Unit) {
    Text(
        text = stringResource(R.string.automation_card_not_set_up),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Button(onClick = onSetUp) { Text(stringResource(R.string.automation_card_set_up)) }
}

@Composable
private fun AutomationConfigured(
    geofence: GarageGeofence,
    isAutomationEnabled: Boolean,
    onEditLocation: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Text(
        text = stringResource(if (isAutomationEnabled) R.string.automation_status_on else R.string.automation_status_off),
        style = MaterialTheme.typography.bodyLarge,
        color = if (isAutomationEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        text = stringResource(R.string.automation_card_summary, geofence.openRadiusFeet, geofence.closeRadiusFeet),
        style = MaterialTheme.typography.bodyMedium,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onOpenSettings) { Text(stringResource(R.string.automation_card_settings)) }
        OutlinedButton(onClick = onEditLocation) { Text(stringResource(R.string.automation_card_edit)) }
    }
}

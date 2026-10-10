package com.raf.garagedoor.ui.geofence

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
fun AutomationSummaryCard(geofence: GarageGeofence?, onEdit: () -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.automation_card_title), style = MaterialTheme.typography.titleMedium)
            if (geofence == null) {
                AutomationNotSetUp(onEdit)
            } else {
                AutomationConfigured(geofence, onEdit)
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
private fun AutomationConfigured(geofence: GarageGeofence, onEdit: () -> Unit) {
    Text(
        text = stringResource(R.string.automation_card_summary, geofence.openRadiusFeet, geofence.closeRadiusFeet),
        style = MaterialTheme.typography.bodyLarge,
    )
    Text(
        text = stringResource(R.string.automation_card_next_step),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedButton(onClick = onEdit) { Text(stringResource(R.string.automation_card_edit)) }
}

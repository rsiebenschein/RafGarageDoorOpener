package com.raf.garagedoor.ui.automation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.raf.garagedoor.R
import com.raf.garagedoor.automation.AutomationSettings
import com.raf.garagedoor.automation.LeftOpenAction
import com.raf.garagedoor.automation.LeftOpenLimits
import com.raf.garagedoor.automation.LiveReading
import com.raf.garagedoor.geofence.metersToFeet
import kotlin.math.roundToInt

private const val LEFT_OPEN_STEP_MINUTES = 5

@Composable
fun LiveDistanceCard(reading: LiveReading?) {
    SettingsCard {
        Text(stringResource(R.string.live_title), style = MaterialTheme.typography.titleMedium)
        val distance = reading?.distanceMeters
        val text = when {
            reading == null -> stringResource(R.string.live_waiting)
            distance == null -> stringResource(R.string.automation_needs_location)
            else -> stringResource(R.string.live_distance, metersToFeet(distance), metersToFeet(reading.accuracyMeters.toDouble()))
        }
        Text(text, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = stringResource(R.string.live_explanation),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun LeftOpenCard(settings: AutomationSettings, onActionChange: (LeftOpenAction) -> Unit, onMinutesChange: (Int) -> Unit) {
    SettingsCard {
        Text(stringResource(R.string.left_open_settings_title), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.left_open_settings_detail),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LeftOpenActionChips(settings.leftOpenAction, onActionChange)
        if (settings.leftOpenAction != LeftOpenAction.OFF) LeftOpenMinutesSlider(settings.leftOpenMinutes, onMinutesChange)
    }
}

@Composable
private fun LeftOpenActionChips(selected: LeftOpenAction, onActionChange: (LeftOpenAction) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LeftOpenAction.entries.forEach { action ->
            FilterChip(
                selected = action == selected,
                onClick = { onActionChange(action) },
                label = { Text(stringResource(labelFor(action))) },
            )
        }
    }
}

@Composable
private fun LeftOpenMinutesSlider(minutes: Int, onMinutesChange: (Int) -> Unit) {
    Text(stringResource(R.string.left_open_after_minutes, minutes), style = MaterialTheme.typography.bodyLarge)
    Slider(
        value = minutes.toFloat(),
        onValueChange = { onMinutesChange((it / LEFT_OPEN_STEP_MINUTES).roundToInt() * LEFT_OPEN_STEP_MINUTES) },
        valueRange = LeftOpenLimits.MIN_MINUTES.toFloat()..LeftOpenLimits.MAX_MINUTES.toFloat(),
    )
    Text(
        text = stringResource(R.string.left_open_timing_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun labelFor(action: LeftOpenAction): Int = when (action) {
    LeftOpenAction.OFF -> R.string.left_open_off
    LeftOpenAction.REMIND -> R.string.left_open_remind
    LeftOpenAction.AUTO_CLOSE -> R.string.left_open_auto_close
}

package com.raf.garagedoor.ui.automation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raf.garagedoor.R
import com.raf.garagedoor.automation.AutomationEvent
import com.raf.garagedoor.automation.AutomationEventType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

@Composable
fun ActivityLogRoute(viewModel: AutomationViewModel, onBack: () -> Unit) {
    val events by viewModel.events.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    ActivityLogScreen(events, onBack, onClear = viewModel::clearLogs)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityLogScreen(events: List<AutomationEvent>, onBack: () -> Unit, onClear: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.activity_log_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = { ClearLogsAction(onClear) },
            )
        },
    ) { padding ->
        if (events.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.activity_log_empty))
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(events) { event ->
                    ActivityLogRow(event)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ActivityLogRow(event: AutomationEvent) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(stringResource(eventLabelFor(event.type)), style = MaterialTheme.typography.bodyLarge)
        val timestamp = TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(event.atEpochMillis).atZone(ZoneId.systemDefault()))
        val distance = event.distanceFeet?.let { stringResource(R.string.distance_feet, it) }
        Text(
            text = listOfNotNull(timestamp, distance, event.detail).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun eventLabelFor(type: AutomationEventType): Int = when (type) {
    AutomationEventType.OPENED -> R.string.event_opened
    AutomationEventType.CLOSED -> R.string.event_closed
    AutomationEventType.ALREADY_OPEN -> R.string.event_already_open
    AutomationEventType.ALREADY_CLOSED -> R.string.event_already_closed
    AutomationEventType.SKIPPED_CAR_NOT_CONNECTED -> R.string.event_car_not_connected
    AutomationEventType.SKIPPED_COOLDOWN -> R.string.event_cooldown
    AutomationEventType.CANCELLED -> R.string.event_cancelled
    AutomationEventType.FAILED -> R.string.event_failed
    AutomationEventType.LEFT_OPEN_REMINDED -> R.string.event_left_open_reminded
}

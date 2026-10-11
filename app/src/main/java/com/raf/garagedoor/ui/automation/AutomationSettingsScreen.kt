package com.raf.garagedoor.ui.automation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raf.garagedoor.R
import com.raf.garagedoor.automation.CarBluetoothDevice
import com.raf.garagedoor.automation.LeftOpenAction
import com.raf.garagedoor.automation.LiveReading

class AutomationScreenActions(
    val onBack: () -> Unit,
    val onEditLocation: () -> Unit,
    val onOpenActivityLog: () -> Unit,
    val onEnabledChange: (Boolean) -> Unit,
    val onOpenCountdownChange: (Int) -> Unit,
    val onCloseCountdownChange: (Int) -> Unit,
    val onRequireCarChange: (Boolean) -> Unit,
    val onCarSelected: (CarBluetoothDevice) -> Unit,
    val onLeftOpenActionChange: (LeftOpenAction) -> Unit,
    val onLeftOpenMinutesChange: (Int) -> Unit,
    val onUseMobileDataChange: (Boolean) -> Unit,
    val onOpenDiagnostics: () -> Unit,
    val permissionRequests: PermissionRequests,
)

@Composable
fun AutomationSettingsRoute(
    viewModel: AutomationViewModel,
    onBack: () -> Unit,
    onEditLocation: () -> Unit,
    onOpenActivityLog: () -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val liveReading by viewModel.liveReading.collectAsStateWithLifecycle()
    BackHandler(onBack = onBack)
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshCapabilities()
        viewModel.startLiveDistance()
        onPauseOrDispose { viewModel.stopLiveDistance() }
    }
    val actions = AutomationScreenActions(
        onBack = onBack,
        onEditLocation = onEditLocation,
        onOpenActivityLog = onOpenActivityLog,
        onEnabledChange = viewModel::setEnabled,
        onOpenCountdownChange = viewModel::setOpenCountdown,
        onCloseCountdownChange = viewModel::setCloseCountdown,
        onRequireCarChange = viewModel::setRequireCar,
        onCarSelected = viewModel::selectCar,
        onLeftOpenActionChange = viewModel::setLeftOpenAction,
        onLeftOpenMinutesChange = viewModel::setLeftOpenMinutes,
        onUseMobileDataChange = viewModel::setUseMobileData,
        onOpenDiagnostics = onOpenDiagnostics,
        permissionRequests = rememberPermissionRequests(onResult = viewModel::refreshCapabilities),
    )
    AutomationSettingsScreen(state, liveReading, actions)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationSettingsScreen(state: AutomationUiState?, liveReading: LiveReading?, actions: AutomationScreenActions) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.automation_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        if (state == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            AutomationSettingsContent(state, liveReading, actions, Modifier.padding(padding))
        }
    }
}

@Composable
private fun AutomationSettingsContent(state: AutomationUiState, liveReading: LiveReading?, actions: AutomationScreenActions, modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MasterSwitchCard(state, actions)
        LiveDistanceCard(liveReading)
        SetupChecklistCard(state.capabilities.permissions, actions.permissionRequests)
        CountdownCard(state.settings, actions.onOpenCountdownChange, actions.onCloseCountdownChange)
        CarBluetoothCard(state, actions)
        LeftOpenCard(state.settings, actions.onLeftOpenActionChange, actions.onLeftOpenMinutesChange)
        MobileDataCard(state.settings.useMobileData, actions.onUseMobileDataChange)
        OutlinedButton(onClick = actions.onOpenActivityLog, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.activity_log_title))
        }
        OutlinedButton(onClick = actions.onOpenDiagnostics, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.diagnostics_title))
        }
    }
}

@Composable
private fun MasterSwitchCard(state: AutomationUiState, actions: AutomationScreenActions) {
    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.automation_master_switch), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = presenceSummary(state),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.settings.isEnabled,
                onCheckedChange = actions.onEnabledChange,
                enabled = state.canEnable || state.settings.isEnabled,
            )
        }
        OutlinedButton(onClick = actions.onEditLocation) { Text(stringResource(R.string.automation_card_edit)) }
    }
}

@Composable
fun SettingsCard(content: @Composable () -> Unit) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

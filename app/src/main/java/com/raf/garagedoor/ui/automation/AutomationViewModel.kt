package com.raf.garagedoor.ui.automation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.raf.garagedoor.automation.ActivityLogRepository
import com.raf.garagedoor.automation.AutomationCoordinator
import com.raf.garagedoor.automation.AutomationEvent
import com.raf.garagedoor.automation.AutomationPermissionStatus
import com.raf.garagedoor.automation.AutomationPermissions
import com.raf.garagedoor.automation.AutomationSettings
import com.raf.garagedoor.automation.AutomationSettingsRepository
import com.raf.garagedoor.automation.CarBluetoothDevice
import com.raf.garagedoor.automation.DiagnosticEntry
import com.raf.garagedoor.automation.DiagnosticsLog
import com.raf.garagedoor.automation.LeftOpenAction
import com.raf.garagedoor.automation.LiveReading
import com.raf.garagedoor.automation.PresenceTracker
import com.raf.garagedoor.automation.PresenceRepository
import com.raf.garagedoor.automation.PresenceState
import com.raf.garagedoor.automation.voice.VoiceServiceControl
import com.raf.garagedoor.bluetooth.CarConnectionRepository
import com.raf.garagedoor.bluetooth.PairedBluetoothDevices
import com.raf.garagedoor.geofence.GarageGeofence
import com.raf.garagedoor.geofence.GeofenceSettingsRepository
import com.raf.garagedoor.location.LocationException
import com.raf.garagedoor.ui.appContainer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class DeviceCapabilities(
    val permissions: AutomationPermissionStatus,
    val pairedDevices: List<CarBluetoothDevice>,
)

data class AutomationUiState(
    val settings: AutomationSettings,
    val capabilities: DeviceCapabilities,
    val geofence: GarageGeofence?,
    val presence: PresenceState,
    val isCarConnected: Boolean,
) {
    val canEnable: Boolean get() = geofence != null && capabilities.permissions.canTrackInBackground
}

class AutomationViewModel(
    private val settingsRepository: AutomationSettingsRepository,
    geofences: GeofenceSettingsRepository,
    presence: PresenceRepository,
    carConnection: CarConnectionRepository,
    private val activityLog: ActivityLogRepository,
    private val permissions: AutomationPermissions,
    private val pairedDevices: PairedBluetoothDevices,
    private val coordinator: AutomationCoordinator,
    private val tracker: PresenceTracker,
    private val diagnostics: DiagnosticsLog,
    private val installedAt: Instant,
    private val voiceService: VoiceServiceControl,
) : ViewModel() {

    private val mutableLiveReading = MutableStateFlow<LiveReading?>(null)

    /** Latest precise reading while the hands-free screen is visible; `null` when not measuring. */
    val liveReading: StateFlow<LiveReading?> = mutableLiveReading.asStateFlow()
    private var liveJob: Job? = null

    private val capabilities = MutableStateFlow(readCapabilities())

    val state: StateFlow<AutomationUiState?> = combine(
        settingsRepository.settings,
        capabilities,
        geofences.geofence,
        presence.state,
        carConnection.connectedAddresses,
    ) { settings, capabilities, geofence, presenceState, connected ->
        AutomationUiState(settings, capabilities, geofence, presenceState, settings.car?.address in connected)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    val events: StateFlow<List<AutomationEvent>> =
        activityLog.events.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    val diagnosticEntries: StateFlow<List<DiagnosticEntry>> =
        diagnostics.entries.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    fun startLiveDistance() {
        if (liveJob?.isActive == true || !capabilities.value.permissions.preciseLocation) return
        liveJob = viewModelScope.launch {
            try {
                tracker.liveReadings().collect { mutableLiveReading.value = it }
            } catch (permissionLost: LocationException) {
                mutableLiveReading.value = null
            }
        }
    }

    fun stopLiveDistance() {
        liveJob?.cancel()
        liveJob = null
        mutableLiveReading.value = null
    }

    fun setLeftOpenAction(action: LeftOpenAction) = updateSettings { it.copy(leftOpenAction = action) }

    fun setLeftOpenMinutes(minutes: Int) = updateSettings { it.copy(leftOpenMinutes = minutes) }

    fun setUseMobileData(enabled: Boolean) = updateSettings { it.copy(useMobileData = enabled) }

    /** Starts the voice service right away: this screen is on display, which Android requires for the microphone. */
    fun setVoiceCommands(enabled: Boolean) {
        val allowed = enabled && capabilities.value.permissions.microphone
        viewModelScope.launch {
            settingsRepository.update { it.copy(voiceCommandsEnabled = allowed) }
            if (allowed) voiceService.start() else voiceService.stop()
        }
    }

    /** Starts the next field test from a clean slate, labelled with the build it runs on. */
    fun clearLogs() {
        viewModelScope.launch {
            activityLog.clear()
            diagnostics.clear()
            diagnostics.record("Logs cleared. This build was installed ${INSTALL_TIME_FORMAT.format(installedAt.atZone(ZoneId.systemDefault()))}")
        }
    }

    /** Permissions change in system dialogs and Settings, so they are re-read whenever the screen resumes. */
    fun refreshCapabilities() {
        capabilities.value = readCapabilities()
        viewModelScope.launch { coordinator.syncIfChanged() }
    }

    fun setEnabled(enabled: Boolean) {
        val allowed = enabled && state.value?.canEnable == true
        updateSettings { it.copy(isEnabled = allowed) }
    }

    fun setOpenCountdown(seconds: Int) = updateSettings { it.copy(openCountdownSeconds = seconds) }

    fun setCloseCountdown(seconds: Int) = updateSettings { it.copy(closeCountdownSeconds = seconds) }

    fun setRequireCar(required: Boolean) = updateSettings { it.copy(requireCarBluetooth = required) }

    fun selectCar(device: CarBluetoothDevice) = updateSettings { it.copy(car = device) }

    private fun updateSettings(transform: (AutomationSettings) -> AutomationSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }

    private fun readCapabilities() = DeviceCapabilities(permissions.status(), pairedDevices.list())

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L
        private val INSTALL_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d HH:mm")

        val Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                AutomationViewModel(
                    settingsRepository = container.automationSettings,
                    geofences = container.geofenceSettings,
                    presence = container.presenceRepository,
                    carConnection = container.carConnection,
                    activityLog = container.activityLog,
                    permissions = container.automationPermissions,
                    pairedDevices = container.pairedBluetoothDevices,
                    coordinator = container.automationCoordinator,
                    tracker = container.presenceTracker,
                    diagnostics = container.diagnosticsLog,
                    installedAt = container.installedAt,
                    voiceService = container.voiceServiceControl,
                )
            }
        }
    }
}

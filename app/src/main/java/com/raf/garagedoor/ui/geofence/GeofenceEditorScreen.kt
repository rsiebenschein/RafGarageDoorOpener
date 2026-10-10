package com.raf.garagedoor.ui.geofence

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raf.garagedoor.R
import com.raf.garagedoor.geofence.GeoPoint
import com.raf.garagedoor.geofence.GeofenceRadiusLimits
import com.raf.garagedoor.geofence.feetToMeters
import kotlin.math.roundToInt

private const val RADIUS_STEP_FEET = 5

class GeofenceEditorActions(
    val onBack: () -> Unit,
    val onMapTap: (GeoPoint) -> Unit,
    val onOpenRadiusChange: (Int) -> Unit,
    val onCloseRadiusChange: (Int) -> Unit,
    val onRadiusChangeFinished: () -> Unit,
    val onUseMyLocation: () -> Unit,
    val onSave: () -> Unit,
    val onMessageShown: () -> Unit,
)

@Composable
fun GeofenceEditorRoute(viewModel: GeofenceViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val locationAccess = rememberLocationAccess(viewModel::useCurrentLocation, viewModel::onLocationPermissionDenied)
    val leaveEditor = { viewModel.discardDraft(); onBack() }
    BackHandler(onBack = leaveEditor)
    LaunchedEffect(state.isLoaded) {
        if (state.isLoaded && state.saved == null && state.draft.center == null) locationAccess.requestAndLocate()
    }
    val actions = GeofenceEditorActions(
        onBack = leaveEditor,
        onMapTap = viewModel::placeGarage,
        onOpenRadiusChange = viewModel::setOpenRadius,
        onCloseRadiusChange = viewModel::setCloseRadius,
        onRadiusChangeFinished = viewModel::onRadiusChangeFinished,
        onUseMyLocation = locationAccess.requestAndLocate,
        onSave = viewModel::save,
        onMessageShown = viewModel::consumeMessage,
    )
    GeofenceEditorScreen(state, showMyLocation = locationAccess.isGranted, actions = actions)
}

@Composable
fun GeofenceEditorScreen(state: GeofenceUiState, showMyLocation: Boolean, actions: GeofenceEditorActions) {
    val snackbarHostState = remember { SnackbarHostState() }
    GeofenceMessageEffect(state.message, snackbarHostState, actions.onMessageShown)
    var isSatellite by rememberSaveable { mutableStateOf(true) }
    Scaffold(
        topBar = { GeofenceTopBar(isSatellite, onToggleSatellite = { isSatellite = !isSatellite }, onBack = actions.onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                GarageZoneMap(state.draft, isSatellite, showMyLocation, state.cameraFitRequest, actions.onMapTap)
                if (state.draft.center == null) MapPlacementHint(Modifier.align(Alignment.TopCenter))
            }
            GeofenceControls(state, actions)
        }
    }
}

@Composable
private fun GeofenceMessageEffect(message: GeofenceMessage?, snackbarHostState: SnackbarHostState, onShown: () -> Unit) {
    val text = message?.let { stringResource(messageTextFor(it)) }
    LaunchedEffect(text) {
        if (text == null) return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        onShown()
    }
}

private fun messageTextFor(message: GeofenceMessage): Int = when (message) {
    GeofenceMessage.SAVED -> R.string.geofence_saved
    GeofenceMessage.LOCATION_UNAVAILABLE -> R.string.geofence_location_unavailable
    GeofenceMessage.LOCATION_PERMISSION_DENIED -> R.string.geofence_location_permission_denied
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeofenceTopBar(isSatellite: Boolean, onToggleSatellite: () -> Unit, onBack: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(R.string.geofence_title)) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
        },
        actions = {
            FilterChip(
                selected = isSatellite,
                onClick = onToggleSatellite,
                label = { Text(stringResource(R.string.geofence_satellite)) },
                modifier = Modifier.padding(end = 8.dp),
            )
        },
    )
}

@Composable
private fun MapPlacementHint(modifier: Modifier) {
    Surface(modifier = modifier.padding(12.dp), shape = MaterialTheme.shapes.medium, tonalElevation = 6.dp) {
        Text(
            text = stringResource(R.string.geofence_tap_hint),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun GeofenceControls(state: GeofenceUiState, actions: GeofenceEditorActions) {
    val draft = state.draft
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            RadiusSlider(
                title = stringResource(R.string.geofence_open_radius, draft.openRadiusFeet),
                feet = draft.openRadiusFeet,
                range = GeofenceRadiusLimits.OPEN_MIN_FEET..GeofenceRadiusLimits.OPEN_MAX_FEET,
                color = OpenZoneColor,
                onChange = actions.onOpenRadiusChange,
                onChangeFinished = actions.onRadiusChangeFinished,
            )
            RadiusSlider(
                title = stringResource(R.string.geofence_close_radius, draft.closeRadiusFeet),
                feet = draft.closeRadiusFeet,
                range = GeofenceRadiusLimits.closeMinimumFor(draft.openRadiusFeet)..GeofenceRadiusLimits.CLOSE_MAX_FEET,
                color = CloseZoneColor,
                onChange = actions.onCloseRadiusChange,
                onChangeFinished = actions.onRadiusChangeFinished,
            )
            Text(
                text = stringResource(R.string.geofence_latency_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(8.dp))
            GeofenceButtons(state, actions)
        }
    }
}

@Composable
private fun RadiusSlider(
    title: String,
    feet: Int,
    range: IntRange,
    color: Color,
    onChange: (Int) -> Unit,
    onChangeFinished: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        Text(
            text = stringResource(R.string.geofence_radius_detail, feet * 2, feetToMeters(feet).roundToInt()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Slider(
        value = feet.toFloat(),
        onValueChange = { onChange(roundToRadiusStep(it)) },
        valueRange = range.first.toFloat()..range.last.toFloat(),
        onValueChangeFinished = onChangeFinished,
        colors = SliderDefaults.colors(thumbColor = color, activeTrackColor = color),
    )
}

@Composable
private fun GeofenceButtons(state: GeofenceUiState, actions: GeofenceEditorActions) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = actions.onUseMyLocation, enabled = !state.isLocating, modifier = Modifier.weight(1f)) {
            if (state.isLocating) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.geofence_use_my_location))
        }
        Button(onClick = actions.onSave, enabled = state.hasUnsavedChanges, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.geofence_save))
        }
    }
}

private fun roundToRadiusStep(value: Float): Int = (value / RADIUS_STEP_FEET).roundToInt() * RADIUS_STEP_FEET

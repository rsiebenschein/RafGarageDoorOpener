package com.raf.garagedoor.ui.geofence

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.raf.garagedoor.geofence.GarageGeofence
import com.raf.garagedoor.geofence.GeoPoint
import com.raf.garagedoor.geofence.GeofenceDraft
import com.raf.garagedoor.geofence.GeofenceSettingsRepository
import com.raf.garagedoor.location.CurrentLocationProvider
import com.raf.garagedoor.location.LocationException
import com.raf.garagedoor.location.LocationPermissionMissingException
import com.raf.garagedoor.ui.appContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class GeofenceMessage { SAVED, LOCATION_UNAVAILABLE, LOCATION_PERMISSION_DENIED }

data class GeofenceUiState(
    val isLoaded: Boolean = false,
    val saved: GarageGeofence? = null,
    val draft: GeofenceDraft = GeofenceDraft(),
    val isLocating: Boolean = false,
    val message: GeofenceMessage? = null,
    /** Incremented whenever the map should re-frame the garage zone. */
    val cameraFitRequest: Int = 0,
) {
    val hasUnsavedChanges: Boolean get() = draft.toGeofence() != null && draft.toGeofence() != saved
}

class GeofenceViewModel(
    private val repository: GeofenceSettingsRepository,
    private val locationProvider: CurrentLocationProvider,
) : ViewModel() {

    private val mutableState = MutableStateFlow(GeofenceUiState())
    val state: StateFlow<GeofenceUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch { repository.geofence.collect(::onSavedGeofenceChanged) }
    }

    fun placeGarage(point: GeoPoint) = mutableState.update { it.copy(draft = it.draft.withCenter(point)) }

    fun setOpenRadius(feet: Int) = mutableState.update { it.copy(draft = it.draft.withOpenRadius(feet)) }

    fun setCloseRadius(feet: Int) = mutableState.update { it.copy(draft = it.draft.withCloseRadius(feet)) }

    fun onRadiusChangeFinished() = requestCameraFit()

    fun discardDraft() = mutableState.update { state ->
        state.copy(draft = state.saved?.let(GeofenceDraft::from) ?: GeofenceDraft(), message = null)
    }

    fun consumeMessage() = mutableState.update { it.copy(message = null) }

    fun onLocationPermissionDenied() =
        mutableState.update { it.copy(message = GeofenceMessage.LOCATION_PERMISSION_DENIED) }

    fun useCurrentLocation() {
        if (mutableState.value.isLocating) return
        viewModelScope.launch { locateGarageAtCurrentPosition() }
    }

    fun save() {
        val geofence = mutableState.value.draft.toGeofence() ?: return
        viewModelScope.launch {
            repository.save(geofence)
            mutableState.update { it.copy(message = GeofenceMessage.SAVED) }
        }
    }

    private fun onSavedGeofenceChanged(saved: GarageGeofence?) {
        val isFirstLoad = !mutableState.value.isLoaded
        mutableState.update { state ->
            val draft = if (isFirstLoad && saved != null) GeofenceDraft.from(saved) else state.draft
            state.copy(isLoaded = true, saved = saved, draft = draft)
        }
        if (isFirstLoad && saved != null) requestCameraFit()
    }

    private suspend fun locateGarageAtCurrentPosition() {
        mutableState.update { it.copy(isLocating = true, message = null) }
        try {
            val position = locationProvider.currentLocation()
            mutableState.update { it.copy(draft = it.draft.withCenter(position)) }
            requestCameraFit()
        } catch (error: LocationException) {
            mutableState.update { it.copy(message = messageFor(error)) }
        } finally {
            mutableState.update { it.copy(isLocating = false) }
        }
    }

    private fun messageFor(error: LocationException): GeofenceMessage = when (error) {
        is LocationPermissionMissingException -> GeofenceMessage.LOCATION_PERMISSION_DENIED
        else -> GeofenceMessage.LOCATION_UNAVAILABLE
    }

    private fun requestCameraFit() = mutableState.update { it.copy(cameraFitRequest = it.cameraFitRequest + 1) }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                GeofenceViewModel(container.geofenceSettings, container.currentLocationProvider)
            }
        }
    }
}

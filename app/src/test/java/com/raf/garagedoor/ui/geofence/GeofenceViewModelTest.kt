package com.raf.garagedoor.ui.geofence

import com.raf.garagedoor.geofence.GarageGeofence
import com.raf.garagedoor.geofence.GeoPoint
import com.raf.garagedoor.geofence.GeofenceSettingsRepository
import com.raf.garagedoor.location.CurrentLocationProvider
import com.raf.garagedoor.location.LocationPermissionMissingException
import com.raf.garagedoor.location.LocationUnavailableException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GeofenceViewModelTest {

    private val garage = GeoPoint(40.0, -75.0)
    private val repository = FakeGeofenceRepository()
    private val locationProvider = FakeLocationProvider()

    @Before
    fun useTestMainDispatcher() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun restoreMainDispatcher() = Dispatchers.resetMain()

    @Test
    fun init_loadsSavedGeofenceIntoDraftAndRequestsCameraFit() {
        repository.geofence.value = GarageGeofence(garage, 120, 200)

        val state = GeofenceViewModel(repository, locationProvider).state.value

        assertTrue(state.isLoaded)
        assertEquals(garage, state.draft.center)
        assertEquals(120, state.draft.openRadiusFeet)
        assertEquals(1, state.cameraFitRequest)
        assertFalse(state.hasUnsavedChanges)
    }

    @Test
    fun useCurrentLocation_placesGarageAtPhonePosition() {
        locationProvider.result = { garage }
        val viewModel = GeofenceViewModel(repository, locationProvider)

        viewModel.useCurrentLocation()

        assertEquals(garage, viewModel.state.value.draft.center)
        assertFalse(viewModel.state.value.isLocating)
        assertTrue(viewModel.state.value.hasUnsavedChanges)
    }

    @Test
    fun useCurrentLocation_reportsMissingPermissionAndUnavailableFixSeparately() {
        val viewModel = GeofenceViewModel(repository, locationProvider)

        locationProvider.result = { throw LocationPermissionMissingException() }
        viewModel.useCurrentLocation()
        assertEquals(GeofenceMessage.LOCATION_PERMISSION_DENIED, viewModel.state.value.message)

        locationProvider.result = { throw LocationUnavailableException() }
        viewModel.useCurrentLocation()
        assertEquals(GeofenceMessage.LOCATION_UNAVAILABLE, viewModel.state.value.message)
    }

    @Test
    fun save_persistsDraftAndClearsUnsavedFlag() {
        val viewModel = GeofenceViewModel(repository, locationProvider)
        viewModel.placeGarage(garage)
        viewModel.setOpenRadius(150)
        viewModel.setCloseRadius(80)

        viewModel.save()

        assertEquals(GarageGeofence(garage, 150, 80), repository.geofence.value)
        assertEquals(GeofenceMessage.SAVED, viewModel.state.value.message)
        assertFalse(viewModel.state.value.hasUnsavedChanges)
    }

    @Test
    fun discardDraft_revertsToSavedGeofence() {
        repository.geofence.value = GarageGeofence(garage, 60, 100)
        val viewModel = GeofenceViewModel(repository, locationProvider)
        viewModel.placeGarage(GeoPoint(41.0, -76.0))

        viewModel.discardDraft()

        assertEquals(garage, viewModel.state.value.draft.center)
    }

    private class FakeGeofenceRepository : GeofenceSettingsRepository {
        override val geofence = MutableStateFlow<GarageGeofence?>(null)

        override suspend fun save(geofence: GarageGeofence) {
            this.geofence.value = geofence
        }
    }

    private class FakeLocationProvider : CurrentLocationProvider {
        var result: () -> GeoPoint = { throw LocationUnavailableException() }

        override suspend fun currentLocation(): GeoPoint = result()
    }
}

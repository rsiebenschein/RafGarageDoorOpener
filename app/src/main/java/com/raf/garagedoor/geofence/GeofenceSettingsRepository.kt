package com.raf.garagedoor.geofence

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

interface GeofenceSettingsRepository {
    /** The saved garage geofence, or `null` until the user places the garage on the map. */
    val geofence: Flow<GarageGeofence?>

    suspend fun save(geofence: GarageGeofence)
}

private val Context.geofenceDataStore: DataStore<Preferences> by preferencesDataStore(name = "geofence_settings")

class DataStoreGeofenceSettingsRepository(context: Context) : GeofenceSettingsRepository {

    private val dataStore = context.applicationContext.geofenceDataStore

    override val geofence: Flow<GarageGeofence?> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map(::toGeofence)

    override suspend fun save(geofence: GarageGeofence) {
        dataStore.edit { preferences ->
            preferences[LATITUDE] = geofence.center.latitude
            preferences[LONGITUDE] = geofence.center.longitude
            preferences[OPEN_RADIUS_FEET] = geofence.openRadiusFeet
            preferences[CLOSE_RADIUS_FEET] = geofence.closeRadiusFeet
        }
    }

    private fun toGeofence(preferences: Preferences): GarageGeofence? {
        val latitude = preferences[LATITUDE] ?: return null
        val longitude = preferences[LONGITUDE] ?: return null
        return GarageGeofence(
            center = GeoPoint(latitude, longitude),
            openRadiusFeet = preferences[OPEN_RADIUS_FEET] ?: GeofenceRadiusLimits.DEFAULT_OPEN_FEET,
            closeRadiusFeet = preferences[CLOSE_RADIUS_FEET] ?: GeofenceRadiusLimits.DEFAULT_CLOSE_FEET,
        )
    }

    private companion object {
        val LATITUDE = doublePreferencesKey("garage_latitude")
        val LONGITUDE = doublePreferencesKey("garage_longitude")
        val OPEN_RADIUS_FEET = intPreferencesKey("open_radius_feet")
        val CLOSE_RADIUS_FEET = intPreferencesKey("close_radius_feet")
    }
}

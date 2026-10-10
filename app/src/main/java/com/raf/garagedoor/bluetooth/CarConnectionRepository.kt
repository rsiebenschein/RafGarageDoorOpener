package com.raf.garagedoor.bluetooth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Tracks which Bluetooth devices are connected right now, by hardware address, from the system's
 * ACL connect/disconnect broadcasts. Matching by address means headphones or a watch with a
 * similar name can never be mistaken for the car.
 */
interface CarConnectionRepository {
    val connectedAddresses: Flow<Set<String>>

    suspend fun isConnected(address: String): Boolean

    suspend fun onConnectionChanged(address: String, isConnected: Boolean)

    /** Connections do not survive a reboot, so stale entries are cleared at boot. */
    suspend fun clear()
}

private val Context.bluetoothDataStore: DataStore<Preferences> by preferencesDataStore(name = "bluetooth_connections")

class DataStoreCarConnectionRepository(context: Context) : CarConnectionRepository {

    private val dataStore = context.applicationContext.bluetoothDataStore

    override val connectedAddresses: Flow<Set<String>> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { it[CONNECTED_ADDRESSES].orEmpty() }

    override suspend fun isConnected(address: String): Boolean = address in connectedAddresses.first()

    override suspend fun onConnectionChanged(address: String, isConnected: Boolean) {
        dataStore.edit { preferences ->
            val current = preferences[CONNECTED_ADDRESSES].orEmpty()
            preferences[CONNECTED_ADDRESSES] = if (isConnected) current + address else current - address
        }
    }

    override suspend fun clear() {
        dataStore.edit { it.remove(CONNECTED_ADDRESSES) }
    }

    private companion object {
        val CONNECTED_ADDRESSES = stringSetPreferencesKey("connected_addresses")
    }
}

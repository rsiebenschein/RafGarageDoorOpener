package com.raf.garagedoor.automation

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.Instant

enum class PresenceZone { UNKNOWN, AWAY, HOME }

data class PresenceState(
    val zone: PresenceZone = PresenceZone.UNKNOWN,
    val isArrivalArmed: Boolean = false,
    val consecutiveFixesOutside: Int = 0,
    val lastDistanceMeters: Double? = null,
    val lastFixAt: Instant? = null,
    val lastActionAt: Instant? = null,
)

/**
 * Runtime automation state that must survive process death: where the user is relative to the
 * garage, plus a fingerprint of what is currently registered with the system geofencing service.
 */
interface PresenceRepository {
    val state: Flow<PresenceState>

    suspend fun current(): PresenceState

    suspend fun save(state: PresenceState)

    suspend fun recordAction(at: Instant)

    suspend fun registeredFingerprint(): String?

    suspend fun saveRegisteredFingerprint(fingerprint: String?)
}

private val Context.presenceDataStore: DataStore<Preferences> by preferencesDataStore(name = "automation_presence")

class DataStorePresenceRepository(context: Context) : PresenceRepository {

    private val dataStore = context.applicationContext.presenceDataStore

    private val preferences: Flow<Preferences> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }

    override val state: Flow<PresenceState> = preferences.map(::toState)

    override suspend fun current(): PresenceState = state.first()

    override suspend fun save(state: PresenceState) {
        dataStore.edit { writeState(it, state) }
    }

    override suspend fun recordAction(at: Instant) {
        dataStore.edit { it[LAST_ACTION_AT] = at.toEpochMilli() }
    }

    override suspend fun registeredFingerprint(): String? = preferences.first()[REGISTERED_FINGERPRINT]

    override suspend fun saveRegisteredFingerprint(fingerprint: String?) {
        dataStore.edit { if (fingerprint == null) it.remove(REGISTERED_FINGERPRINT) else it[REGISTERED_FINGERPRINT] = fingerprint }
    }

    private fun toState(preferences: Preferences) = PresenceState(
        zone = preferences[ZONE]?.let { name -> PresenceZone.entries.firstOrNull { it.name == name } } ?: PresenceZone.UNKNOWN,
        isArrivalArmed = preferences[ARRIVAL_ARMED] ?: false,
        consecutiveFixesOutside = preferences[CONSECUTIVE_OUTSIDE] ?: 0,
        lastDistanceMeters = preferences[LAST_DISTANCE_METERS],
        lastFixAt = preferences[LAST_FIX_AT]?.let(Instant::ofEpochMilli),
        lastActionAt = preferences[LAST_ACTION_AT]?.let(Instant::ofEpochMilli),
    )

    private fun writeState(preferences: MutablePreferences, state: PresenceState) {
        preferences[ZONE] = state.zone.name
        preferences[ARRIVAL_ARMED] = state.isArrivalArmed
        preferences[CONSECUTIVE_OUTSIDE] = state.consecutiveFixesOutside
        state.lastDistanceMeters?.let { preferences[LAST_DISTANCE_METERS] = it } ?: preferences.remove(LAST_DISTANCE_METERS)
        state.lastFixAt?.let { preferences[LAST_FIX_AT] = it.toEpochMilli() } ?: preferences.remove(LAST_FIX_AT)
        state.lastActionAt?.let { preferences[LAST_ACTION_AT] = it.toEpochMilli() } ?: preferences.remove(LAST_ACTION_AT)
    }

    private companion object {
        val ZONE = stringPreferencesKey("zone")
        val ARRIVAL_ARMED = booleanPreferencesKey("arrival_armed")
        val CONSECUTIVE_OUTSIDE = intPreferencesKey("consecutive_outside")
        val LAST_DISTANCE_METERS = doublePreferencesKey("last_distance_meters")
        val LAST_FIX_AT = longPreferencesKey("last_fix_at")
        val LAST_ACTION_AT = longPreferencesKey("last_action_at")
        val REGISTERED_FINGERPRINT = stringPreferencesKey("registered_fingerprint")
    }
}

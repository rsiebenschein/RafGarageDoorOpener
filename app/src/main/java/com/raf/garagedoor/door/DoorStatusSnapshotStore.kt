package com.raf.garagedoor.door

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.Instant

private val Context.doorStatusDataStore: DataStore<Preferences> by preferencesDataStore(name = "door_status_snapshot")

/** The shared [DoorStatusSnapshot], kept on disk because the widget is drawn while the app is not running. */
class DoorStatusSnapshotStore(context: Context) {

    private val dataStore = context.applicationContext.doorStatusDataStore

    val status: Flow<DoorStatusSnapshot> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map(::toStatus)

    suspend fun current(): DoorStatusSnapshot = status.first()

    /** Applies [transform] atomically; returns whether anything changed. */
    suspend fun update(transform: (DoorStatusSnapshot) -> DoorStatusSnapshot): Boolean {
        var changed = false
        dataStore.edit { preferences ->
            val before = toStatus(preferences)
            val after = transform(before)
            changed = after != before
            if (changed) write(preferences, after)
        }
        return changed
    }

    private fun toStatus(preferences: Preferences) = DoorStatusSnapshot(
        state = preferences[STATE]?.let { name -> DoorState.entries.firstOrNull { it.name == name } },
        lastChanged = preferences[LAST_CHANGED]?.let(Instant::ofEpochMilli),
        movement = preferences[MOVEMENT]?.let(::commandNamed),
        movementUntil = preferences[MOVEMENT_UNTIL]?.let(Instant::ofEpochMilli),
        pendingConfirm = preferences[PENDING_CONFIRM]?.let(::commandNamed),
        confirmUntil = preferences[CONFIRM_UNTIL]?.let(Instant::ofEpochMilli),
    )

    private fun write(preferences: MutablePreferences, status: DoorStatusSnapshot) {
        preferences.putOrRemove(STATE, status.state?.name)
        preferences.putOrRemove(LAST_CHANGED, status.lastChanged?.toEpochMilli())
        preferences.putOrRemove(MOVEMENT, status.movement?.name)
        preferences.putOrRemove(MOVEMENT_UNTIL, status.movementUntil?.toEpochMilli())
        preferences.putOrRemove(PENDING_CONFIRM, status.pendingConfirm?.name)
        preferences.putOrRemove(CONFIRM_UNTIL, status.confirmUntil?.toEpochMilli())
    }

    private fun <T> MutablePreferences.putOrRemove(key: Preferences.Key<T>, value: T?) {
        if (value == null) remove(key) else set(key, value)
    }

    private fun commandNamed(name: String): DoorCommand? = DoorCommand.entries.firstOrNull { it.name == name }

    private companion object {
        val STATE = stringPreferencesKey("state")
        val LAST_CHANGED = longPreferencesKey("last_changed")
        val MOVEMENT = stringPreferencesKey("movement")
        val MOVEMENT_UNTIL = longPreferencesKey("movement_until")
        val PENDING_CONFIRM = stringPreferencesKey("pending_confirm")
        val CONFIRM_UNTIL = longPreferencesKey("confirm_until")
    }
}

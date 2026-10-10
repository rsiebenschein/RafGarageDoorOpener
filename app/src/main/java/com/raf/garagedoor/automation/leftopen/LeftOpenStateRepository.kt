package com.raf.garagedoor.automation.leftopen

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.time.Instant

/**
 * One "open session" of the door, keyed by when this app first saw it open, so Ignore silences
 * reminders only until the door closes and opens again.
 */
data class LeftOpenState(
    val firstSeenOpenAt: Instant? = null,
    val lastReminderAt: Instant? = null,
    val ignoredSessionStartedAt: Instant? = null,
) {
    val isCurrentSessionIgnored: Boolean get() = firstSeenOpenAt != null && firstSeenOpenAt == ignoredSessionStartedAt
}

interface LeftOpenStateRepository {
    suspend fun current(): LeftOpenState

    suspend fun save(state: LeftOpenState)

    suspend fun ignoreCurrentSession()

    suspend fun clear()
}

private val Context.leftOpenDataStore: DataStore<Preferences> by preferencesDataStore(name = "left_open_state")

class DataStoreLeftOpenStateRepository(context: Context) : LeftOpenStateRepository {

    private val dataStore = context.applicationContext.leftOpenDataStore

    override suspend fun current(): LeftOpenState {
        val preferences = dataStore.data
            .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
            .first()
        return LeftOpenState(
            firstSeenOpenAt = preferences[FIRST_SEEN_OPEN_AT]?.let(Instant::ofEpochMilli),
            lastReminderAt = preferences[LAST_REMINDER_AT]?.let(Instant::ofEpochMilli),
            ignoredSessionStartedAt = preferences[IGNORED_SESSION]?.let(Instant::ofEpochMilli),
        )
    }

    override suspend fun save(state: LeftOpenState) {
        dataStore.edit { preferences ->
            state.firstSeenOpenAt?.let { preferences[FIRST_SEEN_OPEN_AT] = it.toEpochMilli() } ?: preferences.remove(FIRST_SEEN_OPEN_AT)
            state.lastReminderAt?.let { preferences[LAST_REMINDER_AT] = it.toEpochMilli() } ?: preferences.remove(LAST_REMINDER_AT)
            state.ignoredSessionStartedAt?.let { preferences[IGNORED_SESSION] = it.toEpochMilli() } ?: preferences.remove(IGNORED_SESSION)
        }
    }

    override suspend fun ignoreCurrentSession() {
        dataStore.edit { preferences -> preferences[FIRST_SEEN_OPEN_AT]?.let { preferences[IGNORED_SESSION] = it } }
    }

    override suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    private companion object {
        val FIRST_SEEN_OPEN_AT = longPreferencesKey("first_seen_open_at")
        val LAST_REMINDER_AT = longPreferencesKey("last_reminder_at")
        val IGNORED_SESSION = longPreferencesKey("ignored_session_started_at")
    }
}

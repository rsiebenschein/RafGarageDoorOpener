package com.raf.garagedoor.automation

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.raf.garagedoor.myq.http.MyQJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.IOException

enum class AutomationEventType {
    OPENED,
    CLOSED,
    ALREADY_OPEN,
    ALREADY_CLOSED,
    SKIPPED_CAR_NOT_CONNECTED,
    SKIPPED_COOLDOWN,
    CANCELLED,
    FAILED,
    LEFT_OPEN_REMINDED,
}

@Serializable
data class AutomationEvent(
    val type: AutomationEventType,
    val atEpochMillis: Long,
    val distanceFeet: Int?,
    val detail: String? = null,
)

interface ActivityLogRepository {
    /** Newest first. */
    val events: Flow<List<AutomationEvent>>

    suspend fun record(event: AutomationEvent)

    suspend fun clear()
}

private val Context.activityLogDataStore: DataStore<Preferences> by preferencesDataStore(name = "automation_activity_log")

class DataStoreActivityLogRepository(context: Context) : ActivityLogRepository {

    private val dataStore = context.applicationContext.activityLogDataStore
    private val listSerializer = ListSerializer(AutomationEvent.serializer())

    override val events: Flow<List<AutomationEvent>> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { decodeEvents(it[EVENTS_KEY]) }

    override suspend fun record(event: AutomationEvent) {
        dataStore.edit { preferences ->
            val updated = (listOf(event) + decodeEvents(preferences[EVENTS_KEY])).take(MAX_EVENTS)
            preferences[EVENTS_KEY] = MyQJson.json.encodeToString(listSerializer, updated)
        }
    }

    override suspend fun clear() {
        dataStore.edit { it.remove(EVENTS_KEY) }
    }

    private fun decodeEvents(json: String?): List<AutomationEvent> {
        if (json == null) return emptyList()
        return try {
            MyQJson.json.decodeFromString(listSerializer, json)
        } catch (corrupt: IllegalArgumentException) {
            emptyList()
        }
    }

    private companion object {
        val EVENTS_KEY = stringPreferencesKey("events")
        const val MAX_EVENTS = 200
    }
}

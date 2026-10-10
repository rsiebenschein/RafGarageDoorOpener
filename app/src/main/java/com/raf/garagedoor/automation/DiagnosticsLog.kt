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
import java.time.Clock

@Serializable
data class DiagnosticEntry(val atEpochMillis: Long, val message: String)

/**
 * Technical trail of what the automation did and why (wake-ups, sessions, fix quality, retries),
 * so a failed real-world test can be explained afterwards. Never stores coordinates, only distances.
 */
interface DiagnosticsLog {
    /** Newest first. */
    val entries: Flow<List<DiagnosticEntry>>

    suspend fun record(message: String)

    suspend fun clear()
}

private val Context.diagnosticsDataStore: DataStore<Preferences> by preferencesDataStore(name = "automation_diagnostics")

class DataStoreDiagnosticsLog(context: Context, private val clock: Clock) : DiagnosticsLog {

    private val dataStore = context.applicationContext.diagnosticsDataStore
    private val listSerializer = ListSerializer(DiagnosticEntry.serializer())

    override val entries: Flow<List<DiagnosticEntry>> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { decode(it[ENTRIES_KEY]) }

    override suspend fun record(message: String) {
        val entry = DiagnosticEntry(clock.millis(), message)
        dataStore.edit { preferences ->
            val updated = (listOf(entry) + decode(preferences[ENTRIES_KEY])).take(MAX_ENTRIES)
            preferences[ENTRIES_KEY] = MyQJson.json.encodeToString(listSerializer, updated)
        }
    }

    override suspend fun clear() {
        dataStore.edit { it.remove(ENTRIES_KEY) }
    }

    private fun decode(json: String?): List<DiagnosticEntry> {
        if (json == null) return emptyList()
        return try {
            MyQJson.json.decodeFromString(listSerializer, json)
        } catch (corrupt: IllegalArgumentException) {
            emptyList()
        }
    }

    private companion object {
        val ENTRIES_KEY = stringPreferencesKey("entries")
        const val MAX_ENTRIES = 400
    }
}

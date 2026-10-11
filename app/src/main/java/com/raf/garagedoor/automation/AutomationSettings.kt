package com.raf.garagedoor.automation

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

data class CarBluetoothDevice(val address: String, val name: String)

/** What to do when the door has been open longer than [AutomationSettings.leftOpenMinutes]. */
enum class LeftOpenAction { OFF, REMIND, AUTO_CLOSE }

data class AutomationSettings(
    val isEnabled: Boolean = false,
    val openCountdownSeconds: Int = 0,
    val closeCountdownSeconds: Int = 0,
    val requireCarBluetooth: Boolean = false,
    val car: CarBluetoothDevice? = null,
    val leftOpenAction: LeftOpenAction = LeftOpenAction.OFF,
    val leftOpenMinutes: Int = LeftOpenLimits.DEFAULT_MINUTES,
    /** Send myQ requests over mobile data first (never while roaming), with Wi-Fi as the backup. */
    val useMobileData: Boolean = true,
    /** Listen for "Open garage door" / "Close garage door" while within 500 ft of the garage. */
    val voiceCommandsEnabled: Boolean = false,
)

object CountdownLimits {
    const val MAX_SECONDS = 15
}

object LeftOpenLimits {
    const val MIN_MINUTES = 5
    const val MAX_MINUTES = 240
    const val DEFAULT_MINUTES = 30
}

interface AutomationSettingsRepository {
    val settings: Flow<AutomationSettings>

    suspend fun current(): AutomationSettings

    suspend fun update(transform: (AutomationSettings) -> AutomationSettings)
}

private val Context.automationSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "automation_settings")

class DataStoreAutomationSettingsRepository(context: Context) : AutomationSettingsRepository {

    private val dataStore = context.applicationContext.automationSettingsDataStore

    override val settings: Flow<AutomationSettings> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map(::toSettings)

    override suspend fun current(): AutomationSettings = settings.first()

    override suspend fun update(transform: (AutomationSettings) -> AutomationSettings) {
        dataStore.edit { preferences -> writeSettings(preferences, transform(toSettings(preferences))) }
    }

    private fun toSettings(preferences: Preferences): AutomationSettings {
        val carAddress = preferences[CAR_ADDRESS]
        return AutomationSettings(
            isEnabled = preferences[ENABLED] ?: false,
            openCountdownSeconds = preferences[OPEN_COUNTDOWN] ?: 0,
            closeCountdownSeconds = preferences[CLOSE_COUNTDOWN] ?: 0,
            requireCarBluetooth = preferences[REQUIRE_CAR] ?: false,
            car = carAddress?.let { CarBluetoothDevice(it, preferences[CAR_NAME] ?: it) },
            leftOpenAction = preferences[LEFT_OPEN_ACTION]?.let { name -> LeftOpenAction.entries.firstOrNull { it.name == name } }
                ?: LeftOpenAction.OFF,
            leftOpenMinutes = preferences[LEFT_OPEN_MINUTES] ?: LeftOpenLimits.DEFAULT_MINUTES,
            useMobileData = preferences[USE_MOBILE_DATA] ?: true,
            voiceCommandsEnabled = preferences[VOICE_COMMANDS] ?: false,
        )
    }

    private fun writeSettings(preferences: MutablePreferences, settings: AutomationSettings) {
        preferences[ENABLED] = settings.isEnabled
        preferences[OPEN_COUNTDOWN] = settings.openCountdownSeconds.coerceIn(0, CountdownLimits.MAX_SECONDS)
        preferences[CLOSE_COUNTDOWN] = settings.closeCountdownSeconds.coerceIn(0, CountdownLimits.MAX_SECONDS)
        preferences[REQUIRE_CAR] = settings.requireCarBluetooth
        preferences[LEFT_OPEN_ACTION] = settings.leftOpenAction.name
        preferences[LEFT_OPEN_MINUTES] = settings.leftOpenMinutes.coerceIn(LeftOpenLimits.MIN_MINUTES, LeftOpenLimits.MAX_MINUTES)
        preferences[USE_MOBILE_DATA] = settings.useMobileData
        preferences[VOICE_COMMANDS] = settings.voiceCommandsEnabled
        settings.car?.let {
            preferences[CAR_ADDRESS] = it.address
            preferences[CAR_NAME] = it.name
        } ?: run {
            preferences.remove(CAR_ADDRESS)
            preferences.remove(CAR_NAME)
        }
    }

    private companion object {
        val ENABLED = booleanPreferencesKey("enabled")
        val OPEN_COUNTDOWN = intPreferencesKey("open_countdown_seconds")
        val CLOSE_COUNTDOWN = intPreferencesKey("close_countdown_seconds")
        val REQUIRE_CAR = booleanPreferencesKey("require_car_bluetooth")
        val CAR_ADDRESS = stringPreferencesKey("car_bluetooth_address")
        val CAR_NAME = stringPreferencesKey("car_bluetooth_name")
        val LEFT_OPEN_ACTION = stringPreferencesKey("left_open_action")
        val LEFT_OPEN_MINUTES = intPreferencesKey("left_open_minutes")
        val USE_MOBILE_DATA = booleanPreferencesKey("use_mobile_data")
        val VOICE_COMMANDS = booleanPreferencesKey("voice_open_enabled")
    }
}

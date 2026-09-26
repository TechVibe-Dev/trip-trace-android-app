package com.techvibedev.triptrace.data.session

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

// android#87 part 3 — lets the user turn off raw accelerometer/gyroscope
// recording (android#77), which otherwise always ran during a trip.
class SettingsDataStore(private val context: Context) {

    private val sensorRecordingEnabledKey = booleanPreferencesKey("sensor_recording_enabled")

    // Defaults to true (missing key) — matches the existing behavior before
    // this toggle existed, so nobody's recording silently stops just from
    // updating the app.
    val sensorRecordingEnabledFlow: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
        preferences[sensorRecordingEnabledKey] ?: true
    }

    suspend fun setSensorRecordingEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { preferences ->
            preferences[sensorRecordingEnabledKey] = enabled
        }
    }
}

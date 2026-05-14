package com.tuapp.fintrack.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        val KEY_REQUIRE_CATEGORY = booleanPreferencesKey("require_category")
        val KEY_LAST_CSV_EXPORT = longPreferencesKey("last_csv_export")
        val KEY_LAST_JSON_EXPORT = longPreferencesKey("last_json_export")
    }

    val requireCategory: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_REQUIRE_CATEGORY] ?: false
    }

    val lastCsvExportTime: Flow<Long?> = dataStore.data.map { prefs ->
        prefs[KEY_LAST_CSV_EXPORT]
    }

    val lastJsonExportTime: Flow<Long?> = dataStore.data.map { prefs ->
        prefs[KEY_LAST_JSON_EXPORT]
    }

    suspend fun setRequireCategory(value: Boolean) {
        dataStore.edit { it[KEY_REQUIRE_CATEGORY] = value }
    }

    suspend fun setLastCsvExportTime(time: Long) {
        dataStore.edit { it[KEY_LAST_CSV_EXPORT] = time }
    }

    suspend fun setLastJsonExportTime(time: Long) {
        dataStore.edit { it[KEY_LAST_JSON_EXPORT] = time }
    }
}

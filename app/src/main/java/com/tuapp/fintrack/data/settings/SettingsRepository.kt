package com.tuapp.fintrack.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
    }

    val requireCategory: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_REQUIRE_CATEGORY] ?: false
    }

    suspend fun setRequireCategory(value: Boolean) {
        dataStore.edit { it[KEY_REQUIRE_CATEGORY] = value }
    }
}

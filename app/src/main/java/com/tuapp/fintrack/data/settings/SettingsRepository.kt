package com.tuapp.fintrack.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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

        val KEY_SMS_AUTO_ENABLED = booleanPreferencesKey("sms_auto_enabled")
        val KEY_SMS_IDENTIFIER_TEXT = stringPreferencesKey("sms_identifier_text")
        val KEY_SMS_AMOUNT_PREFIX = stringPreferencesKey("sms_amount_prefix")
        val KEY_SMS_TRANSACTION_TYPE = stringPreferencesKey("sms_transaction_type")
        val KEY_SMS_DEFAULT_CATEGORY_ID = longPreferencesKey("sms_default_category_id")
        val KEY_SMS_DESCRIPTION_TEMPLATE = stringPreferencesKey("sms_description_template")

        val KEY_STARTING_BALANCE_CENTS = longPreferencesKey("starting_balance_cents")
        val KEY_STARTING_BALANCE_SET_AT = longPreferencesKey("starting_balance_set_at")
        val KEY_HAS_SEEN_STARTING_BALANCE_PROMPT = booleanPreferencesKey("has_seen_starting_balance_prompt")
        val KEY_CARRY_FORWARD_EPOCH = longPreferencesKey("carry_forward_epoch")
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

    val smsAutoEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_SMS_AUTO_ENABLED] ?: false
    }

    val smsIdentifierText: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_SMS_IDENTIFIER_TEXT] ?: ""
    }

    val smsAmountPrefix: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_SMS_AMOUNT_PREFIX] ?: ""
    }

    val smsTransactionType: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_SMS_TRANSACTION_TYPE] ?: "EXPENSE"
    }

    val smsDefaultCategoryId: Flow<Long?> = dataStore.data.map { prefs ->
        prefs[KEY_SMS_DEFAULT_CATEGORY_ID]?.takeIf { it > 0 }
    }

    val smsDescriptionTemplate: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_SMS_DESCRIPTION_TEMPLATE] ?: ""
    }

    val startingBalanceCents: Flow<Long> = dataStore.data.map { prefs ->
        prefs[KEY_STARTING_BALANCE_CENTS] ?: 0L
    }

    val startingBalanceSetAt: Flow<Long> = dataStore.data.map { prefs ->
        prefs[KEY_STARTING_BALANCE_SET_AT] ?: 0L
    }

    val hasSeenStartingBalancePrompt: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_HAS_SEEN_STARTING_BALANCE_PROMPT] ?: false
    }

    val carryForwardEpoch: Flow<Long> = dataStore.data.map { prefs ->
        prefs[KEY_CARRY_FORWARD_EPOCH] ?: 0L
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

    suspend fun setSmsAutoEnabled(value: Boolean) {
        dataStore.edit { it[KEY_SMS_AUTO_ENABLED] = value }
    }

    suspend fun setSmsIdentifierText(value: String) {
        dataStore.edit { it[KEY_SMS_IDENTIFIER_TEXT] = value }
    }

    suspend fun setSmsAmountPrefix(value: String) {
        dataStore.edit { it[KEY_SMS_AMOUNT_PREFIX] = value }
    }

    suspend fun setSmsTransactionType(value: String) {
        dataStore.edit { it[KEY_SMS_TRANSACTION_TYPE] = value }
    }

    suspend fun setSmsDefaultCategoryId(value: Long?) {
        dataStore.edit {
            if (value != null && value > 0) {
                it[KEY_SMS_DEFAULT_CATEGORY_ID] = value
            } else {
                it.remove(KEY_SMS_DEFAULT_CATEGORY_ID)
            }
        }
    }

    suspend fun setSmsDescriptionTemplate(value: String) {
        dataStore.edit { it[KEY_SMS_DESCRIPTION_TEMPLATE] = value }
    }

    suspend fun setStartingBalance(cents: Long) {
        dataStore.edit {
            it[KEY_STARTING_BALANCE_CENTS] = cents
            it[KEY_STARTING_BALANCE_SET_AT] = System.currentTimeMillis()
            it[KEY_HAS_SEEN_STARTING_BALANCE_PROMPT] = true
        }
    }

    suspend fun recordCarryForward(periodStartMs: Long, newStartingBalanceCents: Long) {
        dataStore.edit {
            it[KEY_CARRY_FORWARD_EPOCH] = periodStartMs
            it[KEY_STARTING_BALANCE_CENTS] = newStartingBalanceCents
            it[KEY_STARTING_BALANCE_SET_AT] = System.currentTimeMillis()
            it[KEY_HAS_SEEN_STARTING_BALANCE_PROMPT] = true
        }
    }

    suspend fun setPeriodStartDate(epochMs: Long) {
        dataStore.edit { it[KEY_CARRY_FORWARD_EPOCH] = epochMs }
    }

    suspend fun markStartingBalancePromptSeen() {
        dataStore.edit { it[KEY_HAS_SEEN_STARTING_BALANCE_PROMPT] = true }
    }
}

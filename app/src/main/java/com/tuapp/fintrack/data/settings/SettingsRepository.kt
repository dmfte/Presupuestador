package com.tuapp.fintrack.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.tuapp.fintrack.data.model.TransactionType
import com.tuapp.fintrack.sms.SmsRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
        val KEY_SMS_RULES = stringPreferencesKey("sms_rules_json")

        private val LEGACY_SMS_KEYS = listOf(
            KEY_SMS_IDENTIFIER_TEXT,
            KEY_SMS_AMOUNT_PREFIX,
            KEY_SMS_TRANSACTION_TYPE,
            KEY_SMS_DEFAULT_CATEGORY_ID,
            KEY_SMS_DESCRIPTION_TEMPLATE
        )

        private val json = Json { ignoreUnknownKeys = true }

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

    /**
     * SMS rules, in match order. Stored as JSON. If no JSON has been written yet, the
     * single rule from the old one-rule settings (if configured) is surfaced as rule #1;
     * the first write persists it and drops the legacy keys.
     */
    val smsRules: Flow<List<SmsRule>> = dataStore.data.map { prefs -> decodeSmsRules(prefs) }

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

    suspend fun updateSmsRules(transform: (List<SmsRule>) -> List<SmsRule>) {
        dataStore.edit { prefs ->
            val updated = transform(decodeSmsRules(prefs))
            prefs[KEY_SMS_RULES] = json.encodeToString<List<SmsRule>>(updated)
            LEGACY_SMS_KEYS.forEach { prefs.remove(it) }
        }
    }

    private fun decodeSmsRules(prefs: Preferences): List<SmsRule> {
        val raw = prefs[KEY_SMS_RULES]
        if (raw != null) {
            return runCatching { json.decodeFromString<List<SmsRule>>(raw) }.getOrDefault(emptyList())
        }
        return listOfNotNull(legacySmsRule(prefs))
    }

    private fun legacySmsRule(prefs: Preferences): SmsRule? {
        val identifier = prefs[KEY_SMS_IDENTIFIER_TEXT].orEmpty()
        val prefix = prefs[KEY_SMS_AMOUNT_PREFIX].orEmpty()
        if (identifier.isBlank() || prefix.isBlank()) return null
        val type = runCatching { TransactionType.valueOf(prefs[KEY_SMS_TRANSACTION_TYPE] ?: "EXPENSE") }
            .getOrDefault(TransactionType.EXPENSE)
        return SmsRule(
            id = 1L,
            identifierText = identifier,
            amountPrefix = prefix,
            type = type,
            categoryId = prefs[KEY_SMS_DEFAULT_CATEGORY_ID]?.takeIf { it > 0 },
            descriptionTemplate = prefs[KEY_SMS_DESCRIPTION_TEMPLATE].orEmpty()
        )
    }

    /** "Start over" keeping config: zero the starting balance, clear the period, re-show the prompt. */
    suspend fun resetBalanceAndPeriod() {
        dataStore.edit {
            it.remove(KEY_STARTING_BALANCE_CENTS)
            it.remove(KEY_STARTING_BALANCE_SET_AT)
            it.remove(KEY_CARRY_FORWARD_EPOCH)
            it[KEY_HAS_SEEN_STARTING_BALANCE_PROMPT] = false
        }
    }

    /** Factory reset of every preference (SMS rules, toggles, balance, period, export timestamps). */
    suspend fun clearAll() {
        dataStore.edit { it.clear() }
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

package com.tuapp.fintrack.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.widgetDataStore: DataStore<Preferences> by preferencesDataStore(name = "widget_state")

object WidgetStateKeys {
    val PERIOD_START_MS = longPreferencesKey("period_start_ms")
    val PERIOD_END_MS = longPreferencesKey("period_end_ms")
    val INCOME_CENTS = longPreferencesKey("income_cents")
    val EXPENSE_CENTS = longPreferencesKey("expense_cents")
    val RESERVED_CENTS = longPreferencesKey("reserved_cents")
}

data class WidgetPeriodSummary(
    val periodStartMs: Long = 0L,
    val periodEndMs: Long = 0L,
    val incomeCents: Long = 0L,
    val expenseCents: Long = 0L,
    val reservedCents: Long = 0L
)

suspend fun Context.readWidgetPeriodSummary(): WidgetPeriodSummary =
    widgetDataStore.data.map { prefs ->
        WidgetPeriodSummary(
            periodStartMs = prefs[WidgetStateKeys.PERIOD_START_MS] ?: 0L,
            periodEndMs = prefs[WidgetStateKeys.PERIOD_END_MS] ?: 0L,
            incomeCents = prefs[WidgetStateKeys.INCOME_CENTS] ?: 0L,
            expenseCents = prefs[WidgetStateKeys.EXPENSE_CENTS] ?: 0L,
            reservedCents = prefs[WidgetStateKeys.RESERVED_CENTS] ?: 0L
        )
    }.first()

suspend fun Context.writeWidgetPeriodSummary(summary: WidgetPeriodSummary) {
    widgetDataStore.edit { prefs ->
        prefs[WidgetStateKeys.PERIOD_START_MS] = summary.periodStartMs
        prefs[WidgetStateKeys.PERIOD_END_MS] = summary.periodEndMs
        prefs[WidgetStateKeys.INCOME_CENTS] = summary.incomeCents
        prefs[WidgetStateKeys.EXPENSE_CENTS] = summary.expenseCents
        prefs[WidgetStateKeys.RESERVED_CENTS] = summary.reservedCents
    }
}

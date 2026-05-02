package com.tuapp.fintrack.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives the amount entered in WidgetEntryActivity and updates widget state.
 * WidgetEntryActivity sends a broadcast with the entered amount_cents so we can
 * update the DataStore and trigger a widget recompose — Glance cannot receive
 * Activity results directly.
 */
class WidgetAmountReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_AMOUNT_ENTERED) return

        val amountCents = intent.getLongExtra(EXTRA_AMOUNT_CENTS, 0L)
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            val current = context.readWidgetEntryState()
            context.writeWidgetEntryState(current.copy(amountCents = amountCents))
            SmallEntryWidget().updateAll(context)
            MediumEntryWidget().updateAll(context)
        }
    }

    companion object {
        const val ACTION_AMOUNT_ENTERED = "com.tuapp.fintrack.WIDGET_AMOUNT_ENTERED"
        const val EXTRA_AMOUNT_CENTS = "amount_cents"
    }
}

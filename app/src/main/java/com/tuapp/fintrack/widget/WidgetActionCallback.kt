package com.tuapp.fintrack.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll

/**
 * Toggles the transaction type (INCOME / EXPENSE) in widget entry state.
 */
class ToggleTypeCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val current = context.readWidgetEntryState()
        val next = if (current.selectedType == "EXPENSE") "INCOME" else "EXPENSE"
        // Clear category when switching type since categories filter by type
        context.writeWidgetEntryState(current.copy(selectedType = next, selectedCategoryId = -1L))
        SmallEntryWidget().updateAll(context)
        MediumEntryWidget().updateAll(context)
    }
}

/**
 * Selects a specific category. Category ID is passed via ActionParameters.
 */
class SelectCategoryCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val categoryId = parameters[categoryIdKey] ?: -1L
        val current = context.readWidgetEntryState()
        context.writeWidgetEntryState(current.copy(selectedCategoryId = categoryId))
        SmallEntryWidget().updateAll(context)
        MediumEntryWidget().updateAll(context)
    }

    companion object {
        val categoryIdKey = ActionParameters.Key<Long>("category_id")
    }
}

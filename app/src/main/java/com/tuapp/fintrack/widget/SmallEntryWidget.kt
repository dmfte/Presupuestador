package com.tuapp.fintrack.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDefaults
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.tuapp.fintrack.data.model.Category

class SmallEntryWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryState = context.readWidgetEntryState()
        val categories = loadCategoriesForWidget(context, entryState.selectedType)

        provideContent {
            GlanceTheme {
                EntryWidgetContent(entryState = entryState, categories = categories, context = context)
            }
        }
    }
}

@Composable
fun EntryWidgetContent(
    entryState: WidgetEntryState,
    categories: List<Category>,
    context: Context
) {
    val isExpense = entryState.selectedType == "EXPENSE"
    val amountText = if (entryState.amountCents > 0L) {
        formatCurrency(entryState.amountCents)
    } else {
        "$0.00 (tap to enter)"
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .padding(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Type toggle
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            androidx.glance.Button(
                text = "Expense",
                onClick = actionRunCallback<ToggleTypeCallback>(),
                modifier = GlanceModifier.defaultWeight(),
                enabled = !isExpense || true
            )
            Spacer(modifier = GlanceModifier.width(4.dp))
            androidx.glance.Button(
                text = "Income",
                onClick = actionRunCallback<ToggleTypeCallback>(),
                modifier = GlanceModifier.defaultWeight(),
                enabled = isExpense || true
            )
        }

        Spacer(modifier = GlanceModifier.height(6.dp))

        // Amount tap target
        val amountIntent = widgetEntryActivityIntent(context, entryState.amountCents)
        androidx.glance.Button(
            text = amountText,
            onClick = actionStartActivity(amountIntent),
            modifier = GlanceModifier.fillMaxWidth()
        )

        Spacer(modifier = GlanceModifier.height(6.dp))

        // Category row (up to 4 shown)
        val displayCats = categories.take(4)
        if (displayCats.isNotEmpty()) {
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                // "None" option
                val noneSelected = entryState.selectedCategoryId <= 0L
                androidx.glance.Button(
                    text = if (noneSelected) "[None]" else "None",
                    onClick = actionRunCallback<SelectCategoryCallback>(
                        actionParametersOf(SelectCategoryCallback.categoryIdKey to -1L)
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )
                displayCats.take(3).forEach { cat ->
                    val isSelected = entryState.selectedCategoryId == cat.id
                    Spacer(modifier = GlanceModifier.width(2.dp))
                    androidx.glance.Button(
                        text = if (isSelected) "[${cat.name}]" else cat.name,
                        onClick = actionRunCallback<SelectCategoryCallback>(
                            actionParametersOf(SelectCategoryCallback.categoryIdKey to cat.id)
                        ),
                        modifier = GlanceModifier.defaultWeight()
                    )
                }
            }
        }

        Spacer(modifier = GlanceModifier.height(6.dp))

        // Save
        androidx.glance.Button(
            text = "Save",
            onClick = actionRunCallback<SaveTransactionCallback>(),
            modifier = GlanceModifier.fillMaxWidth()
        )
    }
}

private fun widgetEntryActivityIntent(context: Context, currentCents: Long): Intent =
    Intent(context, WidgetEntryActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(WidgetEntryActivity.EXTRA_AMOUNT_CENTS, currentCents)
    }

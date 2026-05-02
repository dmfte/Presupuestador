package com.tuapp.fintrack.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
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
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.tuapp.fintrack.data.model.Category

class MediumEntryWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryState = context.readWidgetEntryState()
        val categories = loadCategoriesForWidget(context, entryState.selectedType)
        val periodSummary = context.readWidgetPeriodSummary()

        provideContent {
            GlanceTheme {
                MediumWidgetContent(
                    entryState = entryState,
                    categories = categories,
                    periodSummary = periodSummary,
                    context = context
                )
            }
        }
    }
}

@Composable
fun MediumWidgetContent(
    entryState: WidgetEntryState,
    categories: List<Category>,
    periodSummary: WidgetPeriodSummary,
    context: Context
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .padding(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Period summary strip
        if (periodSummary.periodStartMs > 0L) {
            PeriodSummaryStrip(periodSummary = periodSummary)
            Spacer(modifier = GlanceModifier.height(8.dp))
        }

        // Entry form (reuse small widget content)
        EntryWidgetContent(entryState = entryState, categories = categories, context = context)
    }
}

@Composable
fun PeriodSummaryStrip(periodSummary: WidgetPeriodSummary) {
    val netCents = periodSummary.incomeCents - periodSummary.expenseCents
    val netColor = if (netCents >= 0) ColorProvider(Color(0xFF2E7D32)) else ColorProvider(Color(0xFFC62828))

    val periodRange = if (periodSummary.periodStartMs > 0L && periodSummary.periodEndMs > 0L) {
        "${formatDateShort(periodSummary.periodStartMs)} – ${formatDateShort(periodSummary.periodEndMs)}"
    } else {
        "Current Period"
    }

    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(GlanceTheme.colors.primaryContainer)
            .padding(8.dp)
    ) {
        Text(
            text = periodRange,
            style = TextStyle(
                color = GlanceTheme.colors.onPrimaryContainer,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        )
        Spacer(modifier = GlanceModifier.height(4.dp))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = "Income",
                    style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = 10.sp)
                )
                Text(
                    text = formatCurrency(periodSummary.incomeCents),
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF2E7D32)),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            Spacer(modifier = GlanceModifier.width(8.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = "Expenses",
                    style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = 10.sp)
                )
                Text(
                    text = formatCurrency(periodSummary.expenseCents),
                    style = TextStyle(
                        color = ColorProvider(Color(0xFFC62828)),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            Spacer(modifier = GlanceModifier.width(8.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = "Net",
                    style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = 10.sp)
                )
                Text(
                    text = formatCurrency(netCents),
                    style = TextStyle(
                        color = netColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}

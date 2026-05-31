package com.tuapp.fintrack.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.action.clickable
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.tuapp.fintrack.MainActivity

class SmallEntryWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                QuickLaunchContent(context)
            }
        }
    }
}

@Composable
fun QuickLaunchContent(context: Context) {
    val expenseIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(MainActivity.EXTRA_TRANSACTION_TYPE, "EXPENSE")
    }
    val incomeIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(MainActivity.EXTRA_TRANSACTION_TYPE, "INCOME")
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .defaultWeight()
                .cornerRadius(22.dp)
                .background(Color(0xFFFF937E))
                .clickable(actionStartActivity(expenseIntent)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "−",
                style = TextStyle(
                    color = ColorProvider(Color(0xFF1581BF)),
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        Spacer(modifier = GlanceModifier.height(4.dp))

        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .defaultWeight()
                .cornerRadius(22.dp)
                .background(Color(0xFFC1E59F))
                .clickable(actionStartActivity(incomeIntent)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+",
                style = TextStyle(
                    color = ColorProvider(Color(0xFF1581BF)),
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

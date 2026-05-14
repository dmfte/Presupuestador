package com.tuapp.fintrack.ui.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    onNavigateBack: () -> Unit,
    viewModel: ReportViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val currency = remember { NumberFormat.getCurrencyInstance(Locale.US) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.exportError) {
        val err = state.exportError
        if (err != null) {
            snackbarHostState.showSnackbar(err, duration = SnackbarDuration.Short)
            viewModel.clearExportError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Monthly Report") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.isExporting) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(end = 4.dp))
                    } else {
                        IconButton(onClick = { viewModel.exportPdf() }) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = "Export PDF")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Month picker
            item {
                MonthPicker(
                    year = state.selectedYear,
                    month = state.selectedMonth,
                    onPrev = {
                        val (y, m) = prevMonth(state.selectedYear, state.selectedMonth)
                        viewModel.onMonthChanged(y, m)
                    },
                    onNext = {
                        val (y, m) = nextMonth(state.selectedYear, state.selectedMonth)
                        viewModel.onMonthChanged(y, m)
                    }
                )
            }

            // Summary card
            item {
                SummaryCard(state = state, currency = currency)
            }

            // Empty state when no data for the selected month
            if (state.selectedYear > 0 && state.totalIncomeCents == 0L && state.totalExpenseCents == 0L) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(1.dp)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.BarChart,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Text(
                                text = "No data for this month",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Add transactions to see your monthly report.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // Pie chart
            if (state.pieSlices.isNotEmpty()) {
                item {
                    SectionCard(title = "Spending by Category") {
                        PieChartSection(slices = state.pieSlices, currency = currency)
                    }
                }
            }

            // Bar chart: daily spend
            if (state.dailyExpenses.any { it > 0f }) {
                item {
                    SectionCard(title = "Daily Expenses") {
                        DailyBarChart(dailyExpenses = state.dailyExpenses)
                    }
                }
            }

            // Line chart: 4-month trend
            if (state.monthlyTrend.size >= 2) {
                item {
                    SectionCard(title = "4-Month Expense Trend") {
                        TrendLineChart(trend = state.monthlyTrend)
                    }
                }
            }

            // Top categories
            if (state.topCategories.isNotEmpty()) {
                item {
                    SectionCard(title = "Top Spending Categories") {
                        state.topCategories.forEach { tc ->
                            TopCategoryRow(tc = tc, currency = currency)
                        }
                    }
                }
            }

            // Budget vs actual
            if (state.budgetComparisons.isNotEmpty()) {
                item {
                    SectionCard(title = "Budget vs Actual") {
                        state.budgetComparisons.forEach { bc ->
                            BudgetComparisonRow(bc = bc, currency = currency)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
            }

            // Suggestions
            if (state.suggestions.isNotEmpty()) {
                item {
                    SectionCard(title = "Insights") {
                        state.suggestions.forEach { suggestion ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.Start
                            ) {
                                Text("•  ", style = MaterialTheme.typography.bodyMedium)
                                Text(suggestion, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            // Export button
            item {
                Button(
                    onClick = { viewModel.exportPdf() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isExporting
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Export PDF")
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

// ── Month Picker ──────────────────────────────────────────────────────────────

@Composable
private fun MonthPicker(year: Int, month: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    val label = if (year > 0) {
        val cal = Calendar.getInstance().apply { set(Calendar.YEAR, year); set(Calendar.MONTH, month - 1) }
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time)
    } else "Loading…"

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous month")
        }
        Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        val now = Calendar.getInstance()
        val isCurrentMonth = year == now.get(Calendar.YEAR) && month == now.get(Calendar.MONTH) + 1
        IconButton(onClick = onNext, enabled = !isCurrentMonth) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next month")
        }
    }
}

// ── Summary Card ──────────────────────────────────────────────────────────────

@Composable
private fun SummaryCard(state: ReportUiState, currency: NumberFormat) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Income", style = MaterialTheme.typography.labelSmall)
                Text(
                    currency.format(state.totalIncomeCents / 100.0),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.Bold
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Expenses", style = MaterialTheme.typography.labelSmall)
                Text(
                    currency.format(state.totalExpenseCents / 100.0),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFFC62828),
                    fontWeight = FontWeight.Bold
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Net", style = MaterialTheme.typography.labelSmall)
                Text(
                    currency.format(state.netCents / 100.0),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (state.netCents >= 0) Color(0xFF2E7D32) else Color(0xFFC62828),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ── Pie Chart ─────────────────────────────────────────────────────────────────

@Composable
private fun PieChartSection(slices: List<PieSlice>, currency: NumberFormat) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Custom canvas pie chart
        val pieDescription = slices.joinToString(", ") {
            "${it.label} ${(it.fraction * 100).toInt()}%"
        }
        Canvas(modifier = Modifier
            .size(140.dp)
            .semantics { contentDescription = "Spending pie chart: $pieDescription" }
        ) {
            var startAngle = -90f
            slices.forEach { slice ->
                val sweep = slice.fraction * 360f
                drawArc(
                    color = parseComposeColor(slice.colorHex),
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = true,
                    topLeft = Offset(0f, 0f),
                    size = Size(size.width, size.height)
                )
                // White gap
                drawArc(
                    color = Color.White,
                    startAngle = startAngle,
                    sweepAngle = 1f,
                    useCenter = true,
                    topLeft = Offset(0f, 0f),
                    size = Size(size.width, size.height),
                    style = Stroke(width = 2f)
                )
                startAngle += sweep
            }
        }

        // Legend
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            slices.forEach { slice ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .background(parseComposeColor(slice.colorHex), CircleShape)
                    )
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text(
                            slice.label,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "${(slice.fraction * 100).toInt()}% · ${currency.format(slice.amountCents / 100.0)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun parseComposeColor(hex: String): Color = try {
    val cleaned = if (hex.startsWith("#")) hex else "#$hex"
    val androidColor = android.graphics.Color.parseColor(cleaned)
    Color(androidColor)
} catch (e: Exception) {
    Color.Gray
}

// ── Daily Bar Chart (Vico) ────────────────────────────────────────────────────

@Composable
private fun DailyBarChart(dailyExpenses: List<Float>) {
    val modelProducer = remember { CartesianChartModelProducer() }

    LaunchedEffect(dailyExpenses) {
        modelProducer.runTransaction {
            columnSeries { series(dailyExpenses) }
        }
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom()
        ),
        modelProducer = modelProducer,
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
    )
}

// ── Trend Line Chart (Vico) ───────────────────────────────────────────────────

@Composable
private fun TrendLineChart(trend: List<MonthSummary>) {
    val modelProducer = remember { CartesianChartModelProducer() }
    val expenseData = trend.reversed().map { it.totalExpenseCents.toFloat() / 100f }
    val incomeData = trend.reversed().map { it.totalIncomeCents.toFloat() / 100f }

    LaunchedEffect(trend) {
        modelProducer.runTransaction {
            lineSeries {
                series(expenseData)
                series(incomeData)
            }
        }
    }

    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberLineCartesianLayer(),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom()
        ),
        modelProducer = modelProducer,
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
    )
}

// ── Top Category Row ──────────────────────────────────────────────────────────

@Composable
private fun TopCategoryRow(tc: TopCategory, currency: NumberFormat) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (tc.category != null) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(parseComposeColor(tc.category.colorHex), CircleShape)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            tc.label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            "${tc.percentOfTotal}%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Text(
            currency.format(tc.amountCents / 100.0),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFC62828)
        )
    }
}

// ── Budget Comparison Row ─────────────────────────────────────────────────────

@Composable
private fun BudgetComparisonRow(bc: BudgetComparison, currency: NumberFormat) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(bc.category.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                "${bc.percentUsed}%",
                style = MaterialTheme.typography.bodySmall,
                color = if (bc.percentUsed > 100) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { (bc.percentUsed / 100f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = when {
                bc.percentUsed > 100 -> Color(0xFFC62828)
                bc.percentUsed > 80 -> Color(0xFFFF8F00)
                else -> Color(0xFF2E7D32)
            },
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Spent: ${currency.format(bc.spentCents / 100.0)}",
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                "Budget: ${currency.format(bc.budgetCents / 100.0)}",
                style = MaterialTheme.typography.labelSmall
            )
            val remColor = if (bc.remainingCents < 0) Color(0xFFC62828) else Color(0xFF2E7D32)
            Text(
                "Left: ${currency.format(bc.remainingCents / 100.0)}",
                style = MaterialTheme.typography.labelSmall,
                color = remColor
            )
        }
    }
}

// ── Section Card ──────────────────────────────────────────────────────────────

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

// ── Month navigation helpers ──────────────────────────────────────────────────

private fun prevMonth(year: Int, month: Int): Pair<Int, Int> =
    if (month == 1) year - 1 to 12 else year to month - 1

private fun nextMonth(year: Int, month: Int): Pair<Int, Int> =
    if (month == 12) year + 1 to 1 else year to month + 1

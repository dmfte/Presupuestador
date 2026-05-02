package com.tuapp.fintrack.ui.report

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.fintrack.data.model.Category
import com.tuapp.fintrack.data.model.TransactionType
import com.tuapp.fintrack.data.repository.FinTrackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val repository: FinTrackRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportUiState())
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()

    init {
        val cal = Calendar.getInstance()
        loadMonth(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
    }

    fun onMonthChanged(year: Int, month: Int) {
        loadMonth(year, month)
    }

    private fun loadMonth(year: Int, month: Int) {
        viewModelScope.launch {
            val transactions = repository.allTransactions.first()
            val categories = repository.allCategories.first()
            val budgets = repository.getAllActiveBudgets()

            val categoryMap: Map<Long, Category> = categories.associateBy { it.id }

            val (startMs, endMs) = monthBounds(year, month)
            val monthTxs = transactions.filter { it.occurredAt in startMs..endMs }

            val income = monthTxs.filter { it.type == TransactionType.INCOME }.sumOf { it.amountCents }
            val expense = monthTxs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountCents }

            // Pie chart: spending by category
            val expenseTxs = monthTxs.filter { it.type == TransactionType.EXPENSE }
            val spendByCategory = expenseTxs.groupBy { it.categoryId }
                .mapValues { (_, txs) -> txs.sumOf { it.amountCents } }

            val pieSlices = buildPieSlices(spendByCategory, categoryMap, expense)

            // Bar chart: daily expenses
            val daysInMonth = daysInMonth(year, month)
            val dailyExpenses = (1..daysInMonth).map { day ->
                expenseTxs.filter { dayOfMonth(it.occurredAt) == day }.sumOf { it.amountCents }.toFloat() / 100f
            }

            // 4-month trend (current + 3 prior)
            val trend = (0..3).map { offset ->
                val cal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month - 1)
                    add(Calendar.MONTH, -offset)
                }
                val y = cal.get(Calendar.YEAR)
                val m = cal.get(Calendar.MONTH) + 1
                val (s, e) = monthBounds(y, m)
                val txs = transactions.filter { it.occurredAt in s..e }
                MonthSummary(
                    year = y,
                    month = m,
                    totalIncomeCents = txs.filter { it.type == TransactionType.INCOME }.sumOf { it.amountCents },
                    totalExpenseCents = txs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountCents }
                )
            }

            // Budget vs actual
            val budgetComparisons = budgets.mapNotNull { budget ->
                val cat = categoryMap[budget.categoryId] ?: return@mapNotNull null
                val spent = spendByCategory[budget.categoryId] ?: 0L
                val remaining = budget.amountCents - spent
                val pct = if (budget.amountCents > 0) ((spent * 100) / budget.amountCents).toInt() else 0
                BudgetComparison(cat, budget.amountCents, spent, remaining, pct)
            }

            // Top categories (EXPENSE only, sorted desc)
            val topCategories = spendByCategory.entries
                .sortedByDescending { it.value }
                .take(5)
                .map { (catId, amount) ->
                    val cat = categoryMap[catId]
                    val pct = if (expense > 0) ((amount * 100) / expense).toInt() else 0
                    TopCategory(
                        category = cat,
                        label = cat?.name ?: "Uncategorized",
                        amountCents = amount,
                        percentOfTotal = pct
                    )
                }

            val newState = ReportUiState(
                selectedYear = year,
                selectedMonth = month,
                totalIncomeCents = income,
                totalExpenseCents = expense,
                pieSlices = pieSlices,
                dailyExpenses = dailyExpenses,
                monthlyTrend = trend,
                budgetComparisons = budgetComparisons,
                topCategories = topCategories
            )

            _uiState.update {
                newState.copy(suggestions = SuggestionsEngine.generate(newState))
            }
        }
    }

    fun exportPdf() {
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true, exportError = null) }
            try {
                val file = withContext(Dispatchers.IO) {
                    generatePdf(_uiState.value)
                }
                sharePdf(file)
            } catch (e: Exception) {
                _uiState.update { it.copy(exportError = e.message ?: "Export failed") }
            } finally {
                _uiState.update { it.copy(isExporting = false) }
            }
        }
    }

    fun clearExportError() {
        _uiState.update { it.copy(exportError = null) }
    }

    // ── PDF generation ────────────────────────────────────────────────────────

    private fun generatePdf(state: ReportUiState): File {
        val currency = NumberFormat.getCurrencyInstance(Locale.US)
        val monthName = SimpleDateFormat("MMMM yyyy", Locale.US)
            .format(Calendar.getInstance().apply {
                set(Calendar.YEAR, state.selectedYear)
                set(Calendar.MONTH, state.selectedMonth - 1)
            }.time)

        val pdfDoc = PdfDocument()
        val pageWidth = 595  // A4 pt
        val pageHeight = 842

        val titlePaint = Paint().apply { textSize = 20f; isFakeBoldText = true; color = AndroidColor.BLACK }
        val headPaint = Paint().apply { textSize = 14f; isFakeBoldText = true; color = AndroidColor.BLACK }
        val bodyPaint = Paint().apply { textSize = 11f; color = AndroidColor.DKGRAY }
        val greenPaint = Paint().apply { textSize = 11f; color = AndroidColor.rgb(46, 125, 50) }
        val redPaint = Paint().apply { textSize = 11f; color = AndroidColor.rgb(198, 40, 40) }
        val linePaint = Paint().apply { color = AndroidColor.LTGRAY; strokeWidth = 1f }

        // ── Page 1: Summary + Spending by Category ───────────────────────────
        val page1 = pdfDoc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create())
        val c1 = page1.canvas
        var y = 60f

        c1.drawText("Presupuestador — $monthName", 40f, y, titlePaint); y += 30f
        c1.drawLine(40f, y, pageWidth - 40f, y, linePaint); y += 20f

        // Summary metrics
        c1.drawText("Summary", 40f, y, headPaint); y += 20f
        c1.drawText("Total Income:", 40f, y, bodyPaint)
        c1.drawText(currency.format(state.totalIncomeCents / 100.0), 200f, y, greenPaint); y += 16f
        c1.drawText("Total Expenses:", 40f, y, bodyPaint)
        c1.drawText(currency.format(state.totalExpenseCents / 100.0), 200f, y, redPaint); y += 16f
        val netPaint = if (state.netCents >= 0) greenPaint else redPaint
        c1.drawText("Net:", 40f, y, bodyPaint)
        c1.drawText(currency.format(state.netCents / 100.0), 200f, y, netPaint); y += 26f

        c1.drawLine(40f, y, pageWidth - 40f, y, linePaint); y += 20f

        // Pie chart as table
        c1.drawText("Spending by Category", 40f, y, headPaint); y += 20f
        if (state.pieSlices.isEmpty()) {
            c1.drawText("No expense transactions this month.", 40f, y, bodyPaint); y += 16f
        } else {
            // Simple bar representation
            val barMaxWidth = (pageWidth - 260).toFloat()
            state.pieSlices.forEach { slice ->
                c1.drawText(slice.label.take(22), 40f, y, bodyPaint)
                c1.drawText("${slice.fraction.times(100).toInt()}%", 200f, y, bodyPaint)
                c1.drawText(currency.format(slice.amountCents / 100.0), 250f, y, bodyPaint)
                val barW = barMaxWidth * slice.fraction
                val barPaint = Paint().apply {
                    color = parseColor(slice.colorHex)
                    style = Paint.Style.FILL
                }
                c1.drawRect(350f, y - 10f, 350f + barW, y, barPaint)
                y += 16f
            }
        }

        // ── Top Categories ────────────────────────────────────────────────────
        y += 10f
        c1.drawLine(40f, y, pageWidth - 40f, y, linePaint); y += 20f
        c1.drawText("Top Spending Categories", 40f, y, headPaint); y += 20f
        state.topCategories.take(5).forEach { tc ->
            c1.drawText("${tc.label.take(22)}", 40f, y, bodyPaint)
            c1.drawText("${tc.percentOfTotal}%", 200f, y, bodyPaint)
            c1.drawText(currency.format(tc.amountCents / 100.0), 250f, y, bodyPaint)
            y += 16f
        }

        pdfDoc.finishPage(page1)

        // ── Page 2: Daily Spending + 4-Month Trend ───────────────────────────
        val page2 = pdfDoc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 2).create())
        val c2 = page2.canvas
        y = 60f

        c2.drawText("Daily Spending — $monthName", 40f, y, headPaint); y += 20f
        val maxDaily = state.dailyExpenses.maxOrNull()?.takeIf { it > 0 } ?: 1f
        val barH = 10f
        val chartW = (pageWidth - 80).toFloat()
        state.dailyExpenses.forEachIndexed { idx, amount ->
            val day = idx + 1
            c2.drawText("%2d".format(day), 40f, y, bodyPaint)
            val bw = (amount / maxDaily) * (chartW - 60)
            if (bw > 0f) {
                val bp = Paint().apply { color = AndroidColor.rgb(25, 118, 210); style = Paint.Style.FILL }
                c2.drawRect(70f, y - barH, 70f + bw, y, bp)
            }
            if (amount > 0f) c2.drawText(currency.format(amount), 70f + (chartW - 60) + 4f, y, bodyPaint)
            y += 14f
            if (y > pageHeight - 120) { y = 60f } // simple overflow guard
        }

        y += 20f
        c2.drawLine(40f, y, pageWidth - 40f, y, linePaint); y += 20f
        c2.drawText("4-Month Trend (Expenses)", 40f, y, headPaint); y += 20f
        val trendMonthFmt = SimpleDateFormat("MMM yyyy", Locale.US)
        state.monthlyTrend.reversed().forEach { ms ->
            val cal = Calendar.getInstance().apply { set(Calendar.YEAR, ms.year); set(Calendar.MONTH, ms.month - 1) }
            val label = trendMonthFmt.format(cal.time)
            c2.drawText(label, 40f, y, bodyPaint)
            c2.drawText(currency.format(ms.totalExpenseCents / 100.0), 160f, y, redPaint)
            c2.drawText("inc: ${currency.format(ms.totalIncomeCents / 100.0)}", 280f, y, greenPaint)
            y += 18f
        }

        pdfDoc.finishPage(page2)

        // ── Page 3: Budgets + Suggestions ────────────────────────────────────
        val page3 = pdfDoc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 3).create())
        val c3 = page3.canvas
        y = 60f

        c3.drawText("Budget vs Actual", 40f, y, headPaint); y += 20f
        if (state.budgetComparisons.isEmpty()) {
            c3.drawText("No budgets configured.", 40f, y, bodyPaint); y += 16f
        } else {
            // Header
            val hPaint = Paint().apply { textSize = 10f; isFakeBoldText = true; color = AndroidColor.BLACK }
            c3.drawText("Category", 40f, y, hPaint)
            c3.drawText("Budget", 180f, y, hPaint)
            c3.drawText("Spent", 260f, y, hPaint)
            c3.drawText("Remaining", 340f, y, hPaint)
            c3.drawText("%", 440f, y, hPaint)
            y += 16f
            c3.drawLine(40f, y, pageWidth - 40f, y, linePaint); y += 8f

            state.budgetComparisons.forEach { bc ->
                c3.drawText(bc.category.name.take(18), 40f, y, bodyPaint)
                c3.drawText(currency.format(bc.budgetCents / 100.0), 180f, y, bodyPaint)
                val spPaint = if (bc.percentUsed > 100) redPaint else bodyPaint
                c3.drawText(currency.format(bc.spentCents / 100.0), 260f, y, spPaint)
                val remPaint = if (bc.remainingCents < 0) redPaint else greenPaint
                c3.drawText(currency.format(bc.remainingCents / 100.0), 340f, y, remPaint)
                c3.drawText("${bc.percentUsed}%", 440f, y, if (bc.percentUsed > 100) redPaint else bodyPaint)
                y += 16f
            }
        }

        y += 20f
        c3.drawLine(40f, y, pageWidth - 40f, y, linePaint); y += 20f
        c3.drawText("Insights & Suggestions", 40f, y, headPaint); y += 20f
        if (state.suggestions.isEmpty()) {
            c3.drawText("No suggestions for this month.", 40f, y, bodyPaint)
        } else {
            state.suggestions.forEach { suggestion ->
                // Wrap text manually at ~75 chars
                val words = suggestion.split(" ")
                var line = ""
                words.forEach { word ->
                    val candidate = if (line.isEmpty()) word else "$line $word"
                    if (candidate.length > 75) {
                        c3.drawText("• $line", 40f, y, bodyPaint); y += 15f
                        line = word
                    } else {
                        line = candidate
                    }
                }
                if (line.isNotEmpty()) { c3.drawText("  $line", 40f, y, bodyPaint); y += 15f }
                y += 4f
            }
        }

        pdfDoc.finishPage(page3)

        val fileName = "report_${state.selectedYear}_${"%02d".format(state.selectedMonth)}.pdf"
        val file = File(appContext.cacheDir, fileName)
        pdfDoc.writeTo(FileOutputStream(file))
        pdfDoc.close()
        return file
    }

    private fun sharePdf(file: File) {
        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(Intent.createChooser(intent, "Share PDF").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildPieSlices(
        spendByCategory: Map<Long?, Long>,
        categoryMap: Map<Long, Category>,
        totalExpense: Long
    ): List<PieSlice> {
        if (totalExpense == 0L) return emptyList()
        val palette = listOf(
            "#E53935", "#1E88E5", "#43A047", "#FB8C00", "#8E24AA",
            "#00ACC1", "#F4511E", "#6D4C41", "#546E7A", "#039BE5"
        )
        return spendByCategory.entries
            .sortedByDescending { it.value }
            .mapIndexed { idx, (catId, amount) ->
                val cat = catId?.let { categoryMap[it] }
                val colorHex = cat?.colorHex ?: palette[idx % palette.size]
                PieSlice(
                    category = cat,
                    label = cat?.name ?: "Uncategorized",
                    amountCents = amount,
                    fraction = amount.toFloat() / totalExpense,
                    colorHex = colorHex
                )
            }
    }

    private fun parseColor(hex: String): Int = try {
        AndroidColor.parseColor(if (hex.startsWith("#")) hex else "#$hex")
    } catch (e: Exception) {
        AndroidColor.GRAY
    }

    private fun monthBounds(year: Int, month: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(year, month - 1, 1, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return start to cal.timeInMillis
    }

    private fun daysInMonth(year: Int, month: Int): Int {
        val cal = Calendar.getInstance()
        cal.set(year, month - 1, 1)
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    private fun dayOfMonth(epochMs: Long): Int {
        val cal = Calendar.getInstance()
        cal.timeInMillis = epochMs
        return cal.get(Calendar.DAY_OF_MONTH)
    }
}

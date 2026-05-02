# SPRINT_5_REPORTS.md — Monthly Report & Exports

**Parent:** `CLAUDE.md`

---

## Overview

Build a comprehensive monthly report with interactive charts (pie, bar, line trend), budget-vs-actual analysis, AI-generated suggestions, and PDF export. This sprint focuses on data visualization and insights.

---

## Tasks

### Report Screen

- [ ] Create `ReportScreen` composable with:
  - Month picker at the top (default: current month). Allow navigating backward/forward.
  - Once a month is selected, display:
    1. **Pie chart** (by category) — spending breakdown.
    2. **Bar chart** (by day) — daily spend for the month.
    3. **Line chart** (trend) — 4-month trend (current month + 3 prior).
    4. **Budget vs Actual table** — rows for each category with budget, spent, remaining, progress bar.
    5. **Top categories** — list of top 3–5 spending categories with amounts.
    6. **Suggestions** — AI-generated or rule-based spending insights (see §8 in main spec).
    7. **PDF Export button** — exports the current month's report.

- [ ] Chart Implementation (using Vico):
  - **Pie Chart:**
    ```kotlin
    val pieData = List(categories.size) { index ->
        Chart.Pie.Entry(
            label = categories[index].name,
            value = totalSpentByCategory[categories[index].id]?.toFloat() ?: 0f
        )
    }
    PieChart(data = pieData, modifier = Modifier.fillMaxWidth())
    ```
  - **Bar Chart (daily spend):**
    ```kotlin
    val barData = (1..daysInMonth).map { day ->
      dailySpent[day]?.toFloat() ?: 0f
    }
    BarChart(data = barData)
    ```
  - **Line Chart (4-month trend):**
    ```kotlin
    val lineData = listOf(
        currentMonth.totalExpenseCents,
        priorMonth1.totalExpenseCents,
        priorMonth2.totalExpenseCents,
        priorMonth3.totalExpenseCents
    ).map { it.toFloat() / 100 }
    LineChart(data = lineData)
    ```

- [ ] Create `ReportViewModel` that exposes:
  - `selectedMonth: StateFlow<YearMonth>` — allows month picker to update it.
  - `pieChartData: Flow<List<PieEntry>>` — computed from transactions.
  - `barChartData: Flow<List<Float>>` — daily spend for the month.
  - `lineChartData: Flow<List<Float>>` — 4-month trend.
  - `budgetVsActualData: Flow<List<BudgetComparison>>` — see below.
  - `topCategories: Flow<List<TopCategory>>` — sorted by amount DESC.
  - `suggestions: Flow<List<String>>` — AI or rule-based.
  - `onMonthChanged(month: YearMonth)` — update state and recompute all charts.

---

### Budget vs Actual Table

- [ ] Data class:
  ```kotlin
  data class BudgetComparison(
      val category: Category,
      val budgetCents: Long,  // for the current period
      val spentCents: Long,   // in the current period
      val remainingCents: Long,  // budget - spent
      val percentUsed: Int  // 0-100+
  )
  ```

- [ ] Query logic:
  - Get all budgets for the *current* cycle (not the selected month).
  - Sum transactions by category for the selected month.
  - Compute remaining and percentUsed.
  - Display as a table/list: category | budget | spent | remaining | progress bar.

---

### Suggestions Engine

Implement rule-based suggestions (no external AI calls for v1):

```kotlin
fun generateSuggestions(
    month: YearMonth,
    transactions: List<Transaction>,
    budgets: List<BudgetComparison>,
    priorMonths: List<MonthData>
): List<String> {
    val suggestions = mutableListOf<String>()
    
    val currentMonthExpense = transactions.filter { it.type == EXPENSE }.sumOf { it.amountCents }
    val priorAvgExpense = priorMonths.take(3).map { it.totalExpense }.average()
    
    // Rule 1: Spending trend
    if (currentMonthExpense > priorAvgExpense * 1.2) {
        suggestions.add("Your spending is ${((currentMonthExpense - priorAvgExpense) / priorAvgExpense * 100).toInt()}% higher than the last 3 months.")
    }
    
    // Rule 2: Budget overages
    val overageBudgets = budgets.filter { it.percentUsed > 100 }
    if (overageBudgets.isNotEmpty()) {
        suggestions.add("You're over budget in ${overageBudgets.size} categor(ies). Consider reducing spending or raising the budget.")
    }
    
    // Rule 3: Top category alert
    val topCategory = transactions.filter { it.type == EXPENSE }
        .groupBy { it.categoryId }
        .maxByOrNull { it.value.sumOf { tx -> tx.amountCents } }
    if (topCategory != null) {
        val categoryName = ... // fetch from repo
        val total = topCategory.value.sumOf { it.amountCents }
        val pct = (total * 100) / currentMonthExpense
        if (pct > 40) {
            suggestions.add("Your top spending category ($categoryName) accounts for $pct% of expenses.")
        }
    }
    
    return suggestions
}
```

---

### PDF Export (Report Only)

- [ ] Export button on Report screen → generates PDF for the selected month.

- [ ] PDF layout (multi-page):
  - **Page 1:**
    - Title: "FinTrack Report — {Month} {Year}"
    - Pie chart (spending by category).
    - Summary metrics: total income, total expense, net.
  - **Page 2:**
    - Bar chart (daily spend).
    - Line chart (4-month trend).
  - **Page 3:**
    - Budget vs Actual table.
    - Top categories list.
    - Suggestions list.

- [ ] Implementation using `PdfDocument`:
  ```kotlin
  suspend fun exportMonthlyReportToPdf(
      month: YearMonth,
      context: Context
  ): File {
      val pdfDoc = PdfDocument()
      
      // Page 1: Pie chart + summary
      val page1 = pdfDoc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
      val canvas = page1.canvas
      
      // Draw title
      canvas.drawText("FinTrack Report — ${month.format()}", 50f, 50f, paint)
      
      // Draw pie chart (render to Bitmap first, then draw on canvas)
      val pieChartBitmap = renderPieChartToBitmap(...)
      canvas.drawBitmap(pieChartBitmap, 50f, 100f, null)
      
      // ... continue for remaining content
      pdfDoc.finishPage(page1)
      
      // Save to file
      val file = File(context.cacheDir, "report_${month}.pdf")
      pdfDoc.writeTo(FileOutputStream(file))
      pdfDoc.close()
      
      return file
  }
  ```

- [ ] Share PDF:
  - After generating PDF, launch Android share sheet via `FileProvider`:
  ```kotlin
  val pdfUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", pdfFile)
  context.startActivity(
      Intent(Intent.ACTION_SEND)
          .apply {
              type = "application/pdf"
              putExtra(Intent.EXTRA_STREAM, pdfUri)
              addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
          }
  )
  ```

---

### Charting Library Notes

**Vico specifics:**
- Lightweight, Compose-native.
- No external dependencies (unlike MPAndroidChart).
- Renders cleanly in Compose.
- Example:
  ```kotlin
  import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
  import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
  
  val modelProducer = remember { CartesianChartModelProducer() }
  
  LaunchedEffect(data) {
      modelProducer.runTransaction {
          lineSeries {
              series(data)
          }
      }
  }
  
  CartesianChartHost(
      modelProducer = modelProducer,
      modifier = Modifier.fillMaxWidth().height(300.dp)
  )
  ```

---

### Testing

- [ ] Unit tests: suggestions engine; verify rules fire correctly given sample data.
- [ ] Compose UI tests: select different months; verify charts update.
- [ ] Instrumented tests: export PDF; verify file is created and is valid PDF.

---

## Verification Checklist

- [ ] Month picker works; can navigate to past months.
- [ ] For a month with ≥10 mixed transactions, all 6 chart/table components render.
- [ ] Pie chart shows correct category breakdown (visually verify or unit test numbers).
- [ ] Bar chart shows daily spending (each day has correct sum).
- [ ] Line chart shows 4-month trend (current + 3 prior months).
- [ ] Budget vs Actual table shows correct budget, spent, remaining, and progress bar colors.
- [ ] Top categories list is sorted by amount DESC and limited to top 3–5.
- [ ] Suggestions engine fires at least 2–3 different rules; suggestions are relevant.
- [ ] PDF export button generates a multi-page PDF.
- [ ] PDF contains pie chart, bar chart, line chart, budget table, and suggestions.
- [ ] PDF opens in system PDF viewer after export.
- [ ] PDF is ≤5MB for reasonable data sizes (≤500 transactions).

---

*End of Sprint 5. Proceed to `SPRINT_6_EXPORT.md` when verified.*

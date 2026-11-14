package com.tuapp.fintrack.ui.report

import androidx.annotation.StringRes
import com.tuapp.fintrack.data.model.Category
import com.tuapp.fintrack.ui.common.UiMessage

data class PieSlice(
    val category: Category?,
    val label: String,
    val amountCents: Long,
    val fraction: Float,
    val colorHex: String
)

data class BudgetComparison(
    val category: Category,
    val budgetCents: Long,
    val spentCents: Long,
    val remainingCents: Long,
    val percentUsed: Int
)

data class TopCategory(
    val category: Category?,
    val label: String,
    val amountCents: Long,
    val percentOfTotal: Int
)

data class MonthSummary(
    val year: Int,
    val month: Int,
    val totalIncomeCents: Long,
    val totalExpenseCents: Long
)

data class ReportUiState(
    val selectedYear: Int = 0,
    val selectedMonth: Int = 0,
    val totalIncomeCents: Long = 0L,
    val totalExpenseCents: Long = 0L,
    val totalReservedCents: Long = 0L,
    val pieSlices: List<PieSlice> = emptyList(),
    val dailyExpenses: List<Float> = emptyList(),
    val monthlyTrend: List<MonthSummary> = emptyList(),
    val budgetComparisons: List<BudgetComparison> = emptyList(),
    val topCategories: List<TopCategory> = emptyList(),
    val suggestions: List<UiMessage> = emptyList(),
    val isExporting: Boolean = false,
    @StringRes val exportError: Int? = null
) {
    val netCents: Long get() = totalIncomeCents - totalExpenseCents - totalReservedCents
}

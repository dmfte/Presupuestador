package com.tuapp.fintrack.ui.report

import com.tuapp.fintrack.data.model.TransactionType
import java.text.NumberFormat
import java.util.Locale

object SuggestionsEngine {

    private val currency = NumberFormat.getCurrencyInstance(Locale.US)

    fun generate(
        state: ReportUiState
    ): List<String> {
        val suggestions = mutableListOf<String>()

        val currentExpense = state.totalExpenseCents
        val trend = state.monthlyTrend

        // Rule 1: Spending trend vs prior 3 months
        val priorMonths = trend.drop(1).take(3)
        if (priorMonths.isNotEmpty()) {
            val priorAvg = priorMonths.map { it.totalExpenseCent }.average().toLong()
            if (priorAvg > 0 && currentExpense > priorAvg * 1.2) {
                val pct = ((currentExpense - priorAvg) * 100 / priorAvg).toInt()
                suggestions.add("Your spending is $pct% higher than your 3-month average (${currency.format(priorAvg / 100.0)}).")
            } else if (priorAvg > 0 && currentExpense < priorAvg * 0.8) {
                val pct = ((priorAvg - currentExpense) * 100 / priorAvg).toInt()
                suggestions.add("Great job! Spending is $pct% lower than your 3-month average.")
            }
        }

        // Rule 2: Over-budget categories
        val overBudget = state.budgetComparisons.filter { it.percentUsed > 100 }
        if (overBudget.isNotEmpty()) {
            val names = overBudget.joinToString(", ") { it.category.name }
            suggestions.add("Over budget in ${overBudget.size} category(ies): $names. Consider adjusting your budget or reducing spending.")
        }

        // Rule 3: Near-budget categories (80–100%)
        val nearBudget = state.budgetComparisons.filter { it.percentUsed in 80..100 }
        if (nearBudget.isNotEmpty()) {
            val names = nearBudget.joinToString(", ") { it.category.name }
            suggestions.add("Approaching budget limit in: $names.")
        }

        // Rule 4: Top category concentration
        val top = state.topCategories.firstOrNull()
        if (top != null && currentExpense > 0 && top.percentOfTotal > 40) {
            suggestions.add("\"${top.label}\" accounts for ${top.percentOfTotal}% of expenses (${currency.format(top.amountCents / 100.0)}). Consider diversifying spending.")
        }

        // Rule 5: No income recorded
        if (state.totalIncomeCents == 0L && currentExpense > 0L) {
            suggestions.add("No income recorded this month. Remember to log your income for accurate net tracking.")
        }

        // Rule 6: Positive net
        if (state.netCents > 0 && currentExpense > 0) {
            suggestions.add("You saved ${currency.format(state.netCents / 100.0)} this month. Keep it up!")
        }

        return suggestions
    }
}

private val MonthSummary.totalExpenseCent: Long get() = totalExpenseCents

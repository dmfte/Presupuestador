package com.tuapp.fintrack.ui.report

import com.tuapp.fintrack.R
import com.tuapp.fintrack.domain.util.Money
import com.tuapp.fintrack.ui.common.UiMessage

object SuggestionsEngine {

    fun generate(state: ReportUiState): List<UiMessage> {
        val suggestions = mutableListOf<UiMessage>()

        val currentExpense = state.totalExpenseCents
        val trend = state.monthlyTrend

        // Rule 1: Spending trend vs prior 3 months
        val priorMonths = trend.drop(1).take(3)
        if (priorMonths.isNotEmpty()) {
            val priorAvg = priorMonths.map { it.totalExpenseCents }.average().toLong()
            if (priorAvg > 0 && currentExpense > priorAvg * 1.2) {
                val pct = ((currentExpense - priorAvg) * 100 / priorAvg).toInt()
                suggestions.add(UiMessage(R.string.suggest_spending_up, pct, Money.formatUsd(priorAvg)))
            } else if (priorAvg > 0 && currentExpense < priorAvg * 0.8) {
                val pct = ((priorAvg - currentExpense) * 100 / priorAvg).toInt()
                suggestions.add(UiMessage(R.string.suggest_spending_down, pct))
            }
        }

        // Rule 2: Over-budget categories
        val overBudget = state.budgetComparisons.filter { it.percentUsed > 100 }
        if (overBudget.isNotEmpty()) {
            val names = overBudget.joinToString(", ") { it.category.name }
            suggestions.add(UiMessage(R.string.suggest_over_budget, names))
        }

        // Rule 3: Near-budget categories (80–100%)
        val nearBudget = state.budgetComparisons.filter { it.percentUsed in 80..100 }
        if (nearBudget.isNotEmpty()) {
            val names = nearBudget.joinToString(", ") { it.category.name }
            suggestions.add(UiMessage(R.string.suggest_near_budget, names))
        }

        // Rule 4: Top category concentration
        val top = state.topCategories.firstOrNull()
        if (top != null && currentExpense > 0 && top.percentOfTotal > 40) {
            suggestions.add(
                UiMessage(R.string.suggest_top_category, top.label, top.percentOfTotal, Money.formatUsd(top.amountCents))
            )
        }

        // Rule 5: No income recorded
        if (state.totalIncomeCents == 0L && currentExpense > 0L) {
            suggestions.add(UiMessage(R.string.suggest_no_income))
        }

        // Rule 6: Positive net
        if (state.netCents > 0 && currentExpense > 0) {
            suggestions.add(UiMessage(R.string.suggest_saved, Money.formatUsd(state.netCents)))
        }

        return suggestions
    }
}

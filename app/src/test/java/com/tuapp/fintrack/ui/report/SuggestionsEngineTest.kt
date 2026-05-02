package com.tuapp.fintrack.ui.report

import com.google.common.truth.Truth.assertThat
import com.tuapp.fintrack.data.model.Category
import com.tuapp.fintrack.data.model.CategoryApplicability
import org.junit.Test

class SuggestionsEngineTest {

    private fun makeCategory(id: Long, name: String) = Category(
        id = id,
        name = name,
        applicability = CategoryApplicability.EXPENSE,
        colorHex = "#E53935",
        createdAt = 0L
    )

    private fun makeBudget(cat: Category, budgetCents: Long, spentCents: Long): BudgetComparison {
        val pct = if (budgetCents > 0) ((spentCents * 100) / budgetCents).toInt() else 0
        return BudgetComparison(
            category = cat,
            budgetCents = budgetCents,
            spentCents = spentCents,
            remainingCents = budgetCents - spentCents,
            percentUsed = pct
        )
    }

    private fun makeTopCategory(cat: Category?, label: String, amountCents: Long, pct: Int) =
        TopCategory(category = cat, label = label, amountCents = amountCents, percentOfTotal = pct)

    private fun baseState(
        incomeCents: Long = 0L,
        expenseCents: Long = 0L,
        trend: List<MonthSummary> = emptyList(),
        budgets: List<BudgetComparison> = emptyList(),
        topCats: List<TopCategory> = emptyList()
    ) = ReportUiState(
        selectedYear = 2025,
        selectedMonth = 5,
        totalIncomeCents = incomeCents,
        totalExpenseCents = expenseCents,
        monthlyTrend = trend,
        budgetComparisons = budgets,
        topCategories = topCats
    )

    @Test
    fun `spending spike rule fires when current expense is 25pct above 3-month average`() {
        val trend = listOf(
            MonthSummary(2025, 5, 0L, 12_500L),   // current — 25% above avg of 10_000
            MonthSummary(2025, 4, 0L, 10_000L),
            MonthSummary(2025, 3, 0L, 10_000L),
            MonthSummary(2025, 2, 0L, 10_000L)
        )
        val state = baseState(expenseCents = 12_500L, trend = trend)
        val suggestions = SuggestionsEngine.generate(state)
        assertThat(suggestions.any { it.contains("higher") }).isTrue()
    }

    @Test
    fun `savings praise fires when current expense is 25pct below 3-month average`() {
        val trend = listOf(
            MonthSummary(2025, 5, 0L, 7_500L),    // current — 25% below avg of 10_000
            MonthSummary(2025, 4, 0L, 10_000L),
            MonthSummary(2025, 3, 0L, 10_000L),
            MonthSummary(2025, 2, 0L, 10_000L)
        )
        val state = baseState(expenseCents = 7_500L, trend = trend)
        val suggestions = SuggestionsEngine.generate(state)
        assertThat(suggestions.any { it.contains("lower") }).isTrue()
    }

    @Test
    fun `over budget rule fires when percentUsed exceeds 100`() {
        val cat = makeCategory(1L, "Dining")
        val state = baseState(
            expenseCents = 5_000L,
            budgets = listOf(makeBudget(cat, 3_000L, 5_000L))
        )
        val suggestions = SuggestionsEngine.generate(state)
        assertThat(suggestions.any { it.contains("Over budget") }).isTrue()
    }

    @Test
    fun `near budget rule fires at 80 to 100 percent`() {
        val cat = makeCategory(1L, "Groceries")
        val state = baseState(
            expenseCents = 4_500L,
            budgets = listOf(makeBudget(cat, 5_000L, 4_500L))
        )
        val suggestions = SuggestionsEngine.generate(state)
        assertThat(suggestions.any { it.contains("Approaching") }).isTrue()
    }

    @Test
    fun `top category concentration rule fires above 40pct`() {
        val cat = makeCategory(1L, "Rent")
        val state = baseState(
            expenseCents = 10_000L,
            topCats = listOf(makeTopCategory(cat, "Rent", 4_500L, 45))
        )
        val suggestions = SuggestionsEngine.generate(state)
        assertThat(suggestions.any { it.contains("Rent") && it.contains("45%") }).isTrue()
    }

    @Test
    fun `no income rule fires when income is zero and there are expenses`() {
        val state = baseState(incomeCents = 0L, expenseCents = 5_000L)
        val suggestions = SuggestionsEngine.generate(state)
        assertThat(suggestions.any { it.contains("No income") }).isTrue()
    }

    @Test
    fun `positive net praise fires when net is positive`() {
        val state = baseState(incomeCents = 10_000L, expenseCents = 7_000L)
        val suggestions = SuggestionsEngine.generate(state)
        assertThat(suggestions.any { it.contains("saved") }).isTrue()
    }

    @Test
    fun `no suggestions when data is empty`() {
        val state = baseState()
        val suggestions = SuggestionsEngine.generate(state)
        assertThat(suggestions).isEmpty()
    }
}

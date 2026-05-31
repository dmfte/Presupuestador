package com.tuapp.fintrack.domain.model

import com.tuapp.fintrack.data.model.Budget
import com.tuapp.fintrack.data.model.Category

data class BudgetWithProgress(
    val budget: Budget,
    val category: Category,
    val currentPeriodSpendingCents: Long,
    val progressPercent: Int
)

package com.tuapp.fintrack.domain.model

import com.tuapp.fintrack.data.model.Budget
import com.tuapp.fintrack.data.model.Category
import com.tuapp.fintrack.data.model.PayCycle

data class BudgetWithProgress(
    val budget: Budget,
    val category: Category,
    val cycle: PayCycle,
    val currentPeriodSpendingCents: Long,
    val progressPercent: Int
)

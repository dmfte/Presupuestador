package com.tuapp.fintrack.domain.model

data class PeriodSummary(
    val period: PayPeriod,
    val totalIncomeCents: Long,
    val totalExpensesCents: Long
) {
    val netCents: Long get() = totalIncomeCents - totalExpensesCents
}

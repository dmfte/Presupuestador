package com.tuapp.fintrack.domain.model

data class MonthPeriod(
    val year: Int,
    val month: Int,
    val startDateMs: Long,
    val endDateMs: Long
)

data class PeriodSummary(
    val period: MonthPeriod,
    val totalIncomeCents: Long,
    val totalExpensesCents: Long,
    val totalReservedCents: Long = 0L
) {
    val netCents: Long get() = totalIncomeCents - totalExpensesCents - totalReservedCents
}

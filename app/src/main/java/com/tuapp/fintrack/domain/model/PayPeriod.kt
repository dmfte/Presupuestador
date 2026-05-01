package com.tuapp.fintrack.domain.model

data class PayPeriod(
    val startDateMs: Long,
    val endDateMs: Long,
    val nextPaydayMs: Long,
    val daysRemaining: Int
)

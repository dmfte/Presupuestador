package com.tuapp.fintrack.widget

import com.tuapp.fintrack.domain.util.Money
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatDateShort(epochMs: Long): String {
    val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
    return sdf.format(Date(epochMs))
}

fun formatCurrency(cents: Long): String = Money.formatUsd(cents)

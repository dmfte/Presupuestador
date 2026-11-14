package com.tuapp.fintrack.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Theme-aware semantic colours for money. Values chosen for ≥4.5:1 on the matching surface. */
@Immutable
data class FinColors(
    val income: Color,
    val expense: Color,
    val reserve: Color,
    val warning: Color,
    val onIncome: Color,
    val onExpense: Color,
    val onReserve: Color
)

internal val LightFinColors = FinColors(
    income = Color(0xFF2E7D32),
    expense = Color(0xFFC62828),
    reserve = Color(0xFF8D5B00),
    warning = Color(0xFFB34700),
    onIncome = Color.White,
    onExpense = Color.White,
    onReserve = Color.White
)

internal val DarkFinColors = FinColors(
    income = Color(0xFF81C784),
    expense = Color(0xFFEF9A9A),
    reserve = Color(0xFFFFCA28),
    warning = Color(0xFFFFB74D),
    onIncome = Color(0xFF1A1A1A),
    onExpense = Color(0xFF1A1A1A),
    onReserve = Color(0xFF1A1A1A)
)

val LocalFinColors = staticCompositionLocalOf { LightFinColors }

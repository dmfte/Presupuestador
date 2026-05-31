package com.tuapp.fintrack.widget

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WidgetHelpersTest {

    @Test
    fun `formatCurrency formats zero correctly`() {
        assertThat(formatCurrency(0L)).isEqualTo("$0.00")
    }

    @Test
    fun `formatCurrency formats whole dollar amounts`() {
        assertThat(formatCurrency(100L)).isEqualTo("$1.00")
        assertThat(formatCurrency(10000L)).isEqualTo("$100.00")
    }

    @Test
    fun `formatCurrency formats cents correctly`() {
        assertThat(formatCurrency(99L)).isEqualTo("$0.99")
        assertThat(formatCurrency(1234L)).isEqualTo("$12.34")
    }

    @Test
    fun `WidgetPeriodSummary net is income minus expense`() {
        val summary = WidgetPeriodSummary(
            periodStartMs = 1000L,
            periodEndMs = 2000L,
            incomeCents = 5000L,
            expenseCents = 3000L
        )
        assertThat(summary.incomeCents - summary.expenseCents).isEqualTo(2000L)
    }

    @Test
    fun `formatDateShort formats epoch`() {
        val formatted = formatDateShort(0L)
        assertThat(formatted).isNotEmpty()
    }
}

package com.tuapp.fintrack.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.tuapp.fintrack.data.model.Transaction
import com.tuapp.fintrack.data.model.TransactionType
import org.junit.Test

class ReserveDeductionTest {

    private fun reserve(id: Long, cents: Long) = Transaction(
        id = id, type = TransactionType.RESERVE, amountCents = cents,
        categoryId = 3L, description = "Rent", occurredAt = id * 10, createdAt = id * 10
    )

    @Test
    fun `expense smaller than reserve consumes it and leaves a remainder row`() {
        val plan = ReserveDeduction.plan(listOf(reserve(1, 10_000)), 7_000, now = 99)
        assertThat(plan.consumedReserveIds).containsExactly(1L)
        assertThat(plan.remainderReserve!!.amountCents).isEqualTo(3_000)
        assertThat(plan.remainderReserve!!.id).isEqualTo(0L)
        assertThat(plan.remainderReserve!!.categoryId).isEqualTo(3L)
        assertThat(plan.remainderReserve!!.occurredAt).isEqualTo(10L)
        assertThat(plan.fromReserveCents).isEqualTo(7_000)
        assertThat(plan.fromBalanceCents).isEqualTo(0)
    }

    @Test
    fun `expense larger than reserves consumes all and the rest comes from balance`() {
        val plan = ReserveDeduction.plan(listOf(reserve(1, 4_000), reserve(2, 6_000)), 15_000, now = 99)
        assertThat(plan.consumedReserveIds).containsExactly(1L, 2L).inOrder()
        assertThat(plan.remainderReserve).isNull()
        assertThat(plan.fromReserveCents).isEqualTo(10_000)
        assertThat(plan.fromBalanceCents).isEqualTo(5_000)
    }

    @Test
    fun `reserves are consumed oldest first and untouched ones stay`() {
        val plan = ReserveDeduction.plan(listOf(reserve(1, 4_000), reserve(2, 6_000), reserve(3, 1_000)), 5_000, now = 99)
        assertThat(plan.consumedReserveIds).containsExactly(1L, 2L).inOrder()
        assertThat(plan.remainderReserve!!.amountCents).isEqualTo(5_000)
    }

    @Test
    fun `exact amount leaves no remainder`() {
        val plan = ReserveDeduction.plan(listOf(reserve(1, 4_000)), 4_000, now = 99)
        assertThat(plan.consumedReserveIds).containsExactly(1L)
        assertThat(plan.remainderReserve).isNull()
        assertThat(plan.fromBalanceCents).isEqualTo(0)
    }

    @Test
    fun `split preview`() {
        assertThat(ReserveDeduction.split(10_000, 7_000)).isEqualTo(7_000L to 0L)
        assertThat(ReserveDeduction.split(10_000, 15_000)).isEqualTo(10_000L to 5_000L)
    }
}

package com.tuapp.fintrack.domain.usecase

import com.tuapp.fintrack.data.model.Transaction

/**
 * Plans how an expense is paid out of reserved money.
 *
 * Reserve rows are consumed oldest-first. Every row the expense touches is soft-deleted
 * (releasing it back to the available balance); if the last touched row is only partially
 * used, a new reserve row with the leftover amount replaces it (same category, description
 * and date). Whatever the reserves can't cover comes out of the available balance.
 */
object ReserveDeduction {

    data class Plan(
        val consumedReserveIds: List<Long>,
        val remainderReserve: Transaction?,
        val fromReserveCents: Long,
        val fromBalanceCents: Long
    )

    fun plan(reservesOldestFirst: List<Transaction>, expenseCents: Long, now: Long): Plan {
        var remaining = expenseCents
        val consumed = mutableListOf<Long>()
        var remainder: Transaction? = null

        for (reserve in reservesOldestFirst) {
            if (remaining <= 0L) break
            if (reserve.amountCents <= 0L) continue
            consumed += reserve.id
            if (reserve.amountCents <= remaining) {
                remaining -= reserve.amountCents
            } else {
                remainder = reserve.copy(
                    id = 0L,
                    amountCents = reserve.amountCents - remaining,
                    createdAt = now,
                    deletedAt = null
                )
                remaining = 0L
            }
        }

        return Plan(
            consumedReserveIds = consumed,
            remainderReserve = remainder,
            fromReserveCents = expenseCents - remaining,
            fromBalanceCents = remaining
        )
    }

    /** UI preview: how much of [expenseCents] would come from [reservedCents] vs. the balance. */
    fun split(reservedCents: Long, expenseCents: Long): Pair<Long, Long> {
        val fromReserve = minOf(reservedCents.coerceAtLeast(0L), expenseCents)
        return fromReserve to (expenseCents - fromReserve)
    }
}

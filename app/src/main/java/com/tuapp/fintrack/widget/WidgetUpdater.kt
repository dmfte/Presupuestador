package com.tuapp.fintrack.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.tuapp.fintrack.data.model.TransactionType
import com.tuapp.fintrack.data.repository.FinTrackRepository
import com.tuapp.fintrack.domain.usecase.GetCurrentMonthPeriodUseCase
import kotlinx.coroutines.flow.first

suspend fun updateAllWidgets(context: Context) {
    runCatching { SmallEntryWidget().updateAll(context) }
}

suspend fun refreshWidgetPeriodSummary(
    context: Context,
    repository: FinTrackRepository,
    getCurrentPeriod: GetCurrentMonthPeriodUseCase
) {
    runCatching {
        val period = getCurrentPeriod()
        val transactions = repository.allTransactions.first()
        var income = 0L
        var expense = 0L
        var reserved = 0L
        transactions.forEach { tx ->
            if (tx.deletedAt == null && tx.occurredAt in period.startDateMs..period.endDateMs) {
                when (tx.type) {
                    TransactionType.INCOME -> income += tx.amountCents
                    TransactionType.EXPENSE -> expense += tx.amountCents
                    TransactionType.RESERVE -> reserved += tx.amountCents
                }
            }
        }
        context.writeWidgetPeriodSummary(
            WidgetPeriodSummary(
                periodStartMs = period.startDateMs,
                periodEndMs = period.endDateMs,
                incomeCents = income,
                expenseCents = expense,
                reservedCents = reserved
            )
        )
        updateAllWidgets(context)
    }
}

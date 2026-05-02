package com.tuapp.fintrack.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.tuapp.fintrack.data.model.Transaction
import com.tuapp.fintrack.data.model.TransactionType
import com.tuapp.fintrack.data.repository.FinTrackRepository
import com.tuapp.fintrack.domain.usecase.GetCurrentPeriodUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

class SaveTransactionCallback : ActionCallback {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SaveTransactionEntryPoint {
        fun repository(): FinTrackRepository
        fun getCurrentPeriod(): GetCurrentPeriodUseCase
    }

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            SaveTransactionEntryPoint::class.java
        )
        val repository = entryPoint.repository()
        val entryState = context.readWidgetEntryState()

        if (entryState.amountCents <= 0L) {
            SmallEntryWidget().update(context, glanceId)
            MediumEntryWidget().update(context, glanceId)
            return
        }

        val type = if (entryState.selectedType == "INCOME") TransactionType.INCOME else TransactionType.EXPENSE
        val categoryId = if (entryState.selectedCategoryId > 0L) entryState.selectedCategoryId else null
        val now = System.currentTimeMillis()

        repository.addTransaction(
            Transaction(
                type = type,
                amountCents = entryState.amountCents,
                categoryId = categoryId,
                description = "",
                occurredAt = now,
                createdAt = now
            )
        )

        context.resetWidgetEntryState()

        val period = entryPoint.getCurrentPeriod()()
        val transactions = repository.allTransactions.first()
        var income = 0L
        var expense = 0L
        transactions.forEach { tx ->
            if (tx.deletedAt == null && tx.occurredAt in period.startDateMs..period.endDateMs) {
                when (tx.type) {
                    TransactionType.INCOME -> income += tx.amountCents
                    TransactionType.EXPENSE -> expense += tx.amountCents
                }
            }
        }
        context.writeWidgetPeriodSummary(
            WidgetPeriodSummary(
                periodStartMs = period.startDateMs,
                periodEndMs = period.endDateMs,
                incomeCents = income,
                expenseCents = expense
            )
        )

        SmallEntryWidget().update(context, glanceId)
        MediumEntryWidget().update(context, glanceId)
        updateAllWidgets(context)
    }
}

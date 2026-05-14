package com.tuapp.fintrack.domain.usecase

import com.tuapp.fintrack.data.dao.CategoryDao
import com.tuapp.fintrack.data.dao.PayEventDao
import com.tuapp.fintrack.data.dao.TransactionDao
import com.tuapp.fintrack.data.model.TransactionType
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

data class ExportTransaction(
    val type: TransactionType,
    val amountCents: Long,
    val categoryName: String?,
    val description: String,
    val occurredAt: Long,
    val isPayEvent: Boolean
)

@Singleton
class ExportTransactionsUseCase @Inject constructor(
    private val transactionDao: TransactionDao,
    private val payEventDao: PayEventDao,
    private val categoryDao: CategoryDao
) {
    suspend operator fun invoke(): List<ExportTransaction> {
        val realTransactions = transactionDao.getAllActive().first()
        val payEvents = payEventDao.getAllActive().first()
        val categories = categoryDao.getAllActive().first()
        val catMap = categories.associateBy { it.id }

        val syntheticTransactions = payEvents.map { event ->
            ExportTransaction(
                type = TransactionType.INCOME,
                amountCents = 0L,
                categoryName = null,
                description = "Pay event",
                occurredAt = event.occurredAt,
                isPayEvent = true
            )
        }

        val denormReal = realTransactions.map { tx ->
            ExportTransaction(
                type = tx.type,
                amountCents = tx.amountCents,
                categoryName = tx.categoryId?.let { catMap[it]?.name },
                description = tx.description,
                occurredAt = tx.occurredAt,
                isPayEvent = false
            )
        }

        return (denormReal + syntheticTransactions).sortedBy { it.occurredAt }
    }
}

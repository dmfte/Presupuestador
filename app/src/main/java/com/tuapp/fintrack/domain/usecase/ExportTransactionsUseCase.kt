package com.tuapp.fintrack.domain.usecase

import com.tuapp.fintrack.data.dao.CategoryDao
import com.tuapp.fintrack.data.dao.TransactionDao
import com.tuapp.fintrack.data.model.TransactionType
import com.tuapp.fintrack.data.settings.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

data class ExportTransaction(
    val type: TransactionType,
    val amountCents: Long,
    val categoryName: String?,
    val description: String,
    val occurredAt: Long,
    val isStartingBalance: Boolean = false
)

@Singleton
class ExportTransactionsUseCase @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val settingsRepository: SettingsRepository
) {
    suspend operator fun invoke(): List<ExportTransaction> {
        val transactions = transactionDao.getAllActive().first()
        val categories = categoryDao.getAllActive().first()
        val catMap = categories.associateBy { it.id }

        val real = transactions.map { tx ->
            ExportTransaction(
                type = tx.type,
                amountCents = tx.amountCents,
                categoryName = tx.categoryId?.let { catMap[it]?.name },
                description = tx.description,
                occurredAt = tx.occurredAt
            )
        }

        val startingBalance = buildStartingBalanceRow()
        val combined = if (startingBalance != null) listOf(startingBalance) + real else real
        return combined.sortedBy { it.occurredAt }
    }

    private suspend fun buildStartingBalanceRow(): ExportTransaction? {
        val cents = settingsRepository.startingBalanceCents.first()
        if (cents == 0L) return null
        val setAt = settingsRepository.startingBalanceSetAt.first().takeIf { it > 0L }
            ?: System.currentTimeMillis()
        return ExportTransaction(
            type = if (cents >= 0) TransactionType.INCOME else TransactionType.EXPENSE,
            amountCents = kotlin.math.abs(cents),
            categoryName = "Opening balance",
            description = "Starting balance",
            occurredAt = setAt,
            isStartingBalance = true
        )
    }
}

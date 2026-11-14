package com.tuapp.fintrack.data.repository

import androidx.room.withTransaction
import com.tuapp.fintrack.data.dao.BudgetDao
import com.tuapp.fintrack.data.dao.CategoryDao
import com.tuapp.fintrack.data.dao.TransactionDao
import com.tuapp.fintrack.data.db.FinTrackDatabase
import com.tuapp.fintrack.data.model.Budget
import com.tuapp.fintrack.data.model.Category
import com.tuapp.fintrack.data.model.Transaction
import com.tuapp.fintrack.data.model.TransactionType
import com.tuapp.fintrack.domain.usecase.ReserveDeduction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FinTrackRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val budgetDao: BudgetDao,
    private val database: FinTrackDatabase
) {
    val allTransactions: Flow<List<Transaction>> = transactionDao.getAllActive()
    val allCategories: Flow<List<Category>> = categoryDao.getAllActive()

    suspend fun addTransaction(tx: Transaction): Long = transactionDao.insert(tx)
    suspend fun updateTransaction(tx: Transaction) = transactionDao.update(tx)
    suspend fun softDeleteTransaction(id: Long) = transactionDao.softDelete(id, System.currentTimeMillis())
    suspend fun restoreTransaction(id: Long) = transactionDao.restore(id)
    suspend fun getTransactionById(id: Long): Transaction? = transactionDao.getById(id)

    /**
     * Saves [expense] paying for it with reserved money first (reserves since [reservesSinceMs],
     * oldest first). See [ReserveDeduction] for how reserve rows are consumed.
     */
    suspend fun addExpenseFromReserves(expense: Transaction, reservesSinceMs: Long): ReserveDeduction.Plan =
        database.withTransaction {
            val now = System.currentTimeMillis()
            val reserves = transactionDao.getActiveByTypeSince(TransactionType.RESERVE, reservesSinceMs)
            val plan = ReserveDeduction.plan(reserves, expense.amountCents, now)
            plan.consumedReserveIds.forEach { transactionDao.softDelete(it, now) }
            plan.remainderReserve?.let { transactionDao.insert(it) }
            transactionDao.insert(expense)
            plan
        }

    /** Hard-deletes all transactions; with [includeCategoriesAndBudgets] also wipes those tables. */
    suspend fun deleteAllData(includeCategoriesAndBudgets: Boolean) {
        database.withTransaction {
            transactionDao.deleteAll()
            if (includeCategoriesAndBudgets) {
                budgetDao.deleteAll()
                categoryDao.deleteAll()
            }
        }
    }

    /** Active totals per type for rows on/after [sinceMs], aggregated in SQLite. */
    fun totalsByTypeSince(sinceMs: Long): Flow<Map<TransactionType, Long>> =
        transactionDao.totalsByTypeSince(sinceMs).map { rows -> rows.associate { it.type to it.totalCents } }

    fun recentTransactions(limit: Int): Flow<List<Transaction>> = transactionDao.getRecent(limit)
    val activeTransactionCount: Flow<Int> = transactionDao.countActive()

    suspend fun addCategory(cat: Category): Long = categoryDao.insert(cat)
    suspend fun updateCategory(cat: Category) = categoryDao.update(cat)
    suspend fun archiveCategory(id: Long) = categoryDao.archive(id, System.currentTimeMillis())
    suspend fun getCategoryById(id: Long): Category? = categoryDao.getById(id)

    suspend fun addBudget(budget: Budget): Long = budgetDao.insert(budget)
    suspend fun updateBudget(budget: Budget) = budgetDao.update(budget)
    suspend fun deactivateBudget(id: Long) = budgetDao.deactivate(id)
    suspend fun reactivateBudget(id: Long) = budgetDao.reactivate(id)
    suspend fun getBudgetById(id: Long): Budget? = budgetDao.getById(id)
    suspend fun hasDuplicateBudget(categoryId: Long): Boolean =
        budgetDao.countByCategory(categoryId) > 0
    suspend fun getAllActiveBudgets(): List<Budget> = budgetDao.getAllActiveOnce()
    val allBudgets: Flow<List<Budget>> = budgetDao.getAllActive()
}

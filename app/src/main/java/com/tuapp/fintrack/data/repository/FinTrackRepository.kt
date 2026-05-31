package com.tuapp.fintrack.data.repository

import com.tuapp.fintrack.data.dao.BudgetDao
import com.tuapp.fintrack.data.dao.CategoryDao
import com.tuapp.fintrack.data.dao.TransactionDao
import com.tuapp.fintrack.data.model.Budget
import com.tuapp.fintrack.data.model.Category
import com.tuapp.fintrack.data.model.CategoryApplicability
import com.tuapp.fintrack.data.model.Transaction
import com.tuapp.fintrack.data.model.TransactionType
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FinTrackRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val budgetDao: BudgetDao
) {
    val allTransactions: Flow<List<Transaction>> = transactionDao.getAllActive()
    val allCategories: Flow<List<Category>> = categoryDao.getAllActive()

    suspend fun addTransaction(tx: Transaction): Long = transactionDao.insert(tx)
    suspend fun updateTransaction(tx: Transaction) = transactionDao.update(tx)
    suspend fun softDeleteTransaction(id: Long) = transactionDao.softDelete(id, System.currentTimeMillis())
    suspend fun restoreTransaction(id: Long) = transactionDao.restore(id)
    suspend fun getTransactionById(id: Long): Transaction? = transactionDao.getById(id)

    fun getTransactionsByType(type: TransactionType): Flow<List<Transaction>> =
        transactionDao.getByType(type)

    fun getTransactionsByDateRange(startMs: Long, endMs: Long): Flow<List<Transaction>> =
        transactionDao.getByDateRange(startMs, endMs)

    suspend fun addCategory(cat: Category): Long = categoryDao.insert(cat)
    suspend fun updateCategory(cat: Category) = categoryDao.update(cat)
    suspend fun archiveCategory(id: Long) = categoryDao.archive(id, System.currentTimeMillis())
    suspend fun getCategoryById(id: Long): Category? = categoryDao.getById(id)
    fun getCategoriesByApplicability(types: List<CategoryApplicability>): Flow<List<Category>> =
        categoryDao.getByApplicability(types)

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

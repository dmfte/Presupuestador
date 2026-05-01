package com.tuapp.fintrack.data.repository

import com.tuapp.fintrack.data.dao.BudgetDao
import com.tuapp.fintrack.data.dao.CategoryDao
import com.tuapp.fintrack.data.dao.HolidayDao
import com.tuapp.fintrack.data.dao.PayCycleDao
import com.tuapp.fintrack.data.dao.PayEventDao
import com.tuapp.fintrack.data.dao.TransactionDao
import com.tuapp.fintrack.data.model.Budget
import com.tuapp.fintrack.data.model.Category
import com.tuapp.fintrack.data.model.CategoryApplicability
import com.tuapp.fintrack.data.model.Holiday
import com.tuapp.fintrack.data.model.PayCycle
import com.tuapp.fintrack.data.model.PayEvent
import com.tuapp.fintrack.data.model.Transaction
import com.tuapp.fintrack.data.model.TransactionType
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FinTrackRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val budgetDao: BudgetDao,
    private val payCycleDao: PayCycleDao,
    private val payEventDao: PayEventDao,
    private val holidayDao: HolidayDao
) {
    val allTransactions: Flow<List<Transaction>> = transactionDao.getAllActive()
    val allCategories: Flow<List<Category>> = categoryDao.getAllActive()
    val allPayCycles: Flow<List<PayCycle>> = payCycleDao.getAllActive()
    val allPayEvents: Flow<List<PayEvent>> = payEventDao.getAllActive()
    val allHolidays: Flow<List<Holiday>> = holidayDao.getAll()

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
    fun getCategoriesByApplicability(types: List<CategoryApplicability>): Flow<List<Category>> =
        categoryDao.getByApplicability(types)

    suspend fun addBudget(budget: Budget): Long = budgetDao.insert(budget)
    suspend fun updateBudget(budget: Budget) = budgetDao.update(budget)
    suspend fun deactivateBudget(id: Long) = budgetDao.deactivate(id)

    suspend fun addPayCycle(cycle: PayCycle): Long = payCycleDao.insert(cycle)
    suspend fun updatePayCycle(cycle: PayCycle) = payCycleDao.update(cycle)
    suspend fun deactivatePayCycle(id: Long) = payCycleDao.deactivate(id)

    suspend fun addPayEvent(event: PayEvent): Long = payEventDao.insert(event)
    suspend fun getPayEventsInRange(startMs: Long, endMs: Long): List<PayEvent> =
        payEventDao.getByDateRange(startMs, endMs)

    suspend fun addHoliday(holiday: Holiday): Long = holidayDao.insert(holiday)
    suspend fun updateHoliday(holiday: Holiday) = holidayDao.update(holiday)
    suspend fun setHolidayEnabled(id: Long, enabled: Boolean) = holidayDao.setEnabled(id, enabled)
    suspend fun deleteHoliday(id: Long) = holidayDao.delete(id)
}

package com.tuapp.fintrack.domain.usecase

import com.tuapp.fintrack.data.dao.CategoryDao
import com.tuapp.fintrack.data.dao.TransactionDao
import com.tuapp.fintrack.data.dao.TypeTotal
import com.tuapp.fintrack.data.model.Category
import com.tuapp.fintrack.data.model.Transaction
import com.tuapp.fintrack.data.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeTransactionDao : TransactionDao {
    private val _transactions = mutableListOf<Transaction>()
    private val _flow = MutableStateFlow<List<Transaction>>(emptyList())

    fun setTransactions(txs: List<Transaction>) {
        _transactions.clear()
        _transactions.addAll(txs)
        _flow.value = _transactions.filter { it.deletedAt == null }
    }

    override fun getAllActive(): Flow<List<Transaction>> = _flow
    override suspend fun insert(transaction: Transaction): Long {
        val id = (_transactions.maxOfOrNull { it.id } ?: 0L) + 1L
        _transactions.add(transaction.copy(id = id))
        _flow.value = _transactions.filter { it.deletedAt == null }
        return id
    }
    override suspend fun update(transaction: Transaction) {
        val idx = _transactions.indexOfFirst { it.id == transaction.id }
        if (idx >= 0) _transactions[idx] = transaction
        _flow.value = _transactions.filter { it.deletedAt == null }
    }
    override suspend fun softDelete(id: Long, deletedAt: Long) {}
    override suspend fun restore(id: Long) {}
    override suspend fun getById(id: Long): Transaction? = _transactions.find { it.id == id }
    override fun totalsByTypeSince(sinceMs: Long): Flow<List<TypeTotal>> = _flow.map { txs ->
        txs.filter { it.occurredAt >= sinceMs }.groupBy { it.type }
            .map { (type, rows) -> TypeTotal(type, rows.sumOf { it.amountCents }) }
    }
    override fun getRecent(limit: Int): Flow<List<Transaction>> = _flow.map { txs ->
        txs.sortedWith(compareByDescending<Transaction> { it.occurredAt }.thenByDescending { it.id }).take(limit)
    }
    override fun countActive(): Flow<Int> = _flow.map { it.size }
    override suspend fun getActiveByTypeSince(type: TransactionType, sinceMs: Long): List<Transaction> =
        _transactions.filter { it.type == type && it.deletedAt == null && it.occurredAt >= sinceMs }
            .sortedWith(compareBy({ it.occurredAt }, { it.id }))
    override suspend fun deleteAll() {
        _transactions.clear()
        _flow.value = emptyList()
    }
}

class FakeCategoryDao : CategoryDao {
    private val _categories = mutableListOf<Category>()
    private val _flow = MutableStateFlow<List<Category>>(emptyList())

    fun setCategories(cats: List<Category>) {
        _categories.clear()
        _categories.addAll(cats)
        _flow.value = _categories.filter { it.archivedAt == null }
    }

    override fun getAllActive(): Flow<List<Category>> = _flow
    override suspend fun insert(category: Category): Long {
        val id = (_categories.maxOfOrNull { it.id } ?: 0L) + 1L
        _categories.add(category.copy(id = id))
        _flow.value = _categories.filter { it.archivedAt == null }
        return id
    }
    override suspend fun update(category: Category) {}
    override suspend fun getById(id: Long): Category? = _categories.find { it.id == id }
    override suspend fun archive(id: Long, archivedAt: Long) {}
    override suspend fun deleteAll() {
        _categories.clear()
        _flow.value = emptyList()
    }
}

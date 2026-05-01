package com.tuapp.fintrack.ui.list

import com.tuapp.fintrack.data.model.Category
import com.tuapp.fintrack.data.model.Transaction
import com.tuapp.fintrack.data.model.TransactionType

enum class DateRangePreset { THIS_MONTH, LAST_MONTH, CUSTOM, ALL }

data class TransactionListUiState(
    val transactions: List<Transaction> = emptyList(),
    val categories: List<Category> = emptyList(),
    val typeFilter: TransactionType? = null,
    val categoryFilter: Long? = null,
    val datePreset: DateRangePreset = DateRangePreset.ALL,
    val customStartMs: Long? = null,
    val customEndMs: Long? = null,
    val searchQuery: String = "",
    val deletedTransactionId: Long? = null
)

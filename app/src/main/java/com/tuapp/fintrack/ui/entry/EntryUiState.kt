package com.tuapp.fintrack.ui.entry

import com.tuapp.fintrack.data.model.Category
import com.tuapp.fintrack.data.model.TransactionType

data class EntryUiState(
    val transactionId: Long? = null,
    val type: TransactionType = TransactionType.EXPENSE,
    val amountCents: Long = 0L,
    val amountText: String = "",
    val selectedCategoryId: Long? = null,
    val description: String = "",
    val occurredAtMs: Long = System.currentTimeMillis(),
    val availableCategories: List<Category> = emptyList(),
    val requireCategory: Boolean = false,
    val amountError: String? = null,
    val categoryError: String? = null,
    val isSaving: Boolean = false,
    val savedEvent: Boolean = false
)

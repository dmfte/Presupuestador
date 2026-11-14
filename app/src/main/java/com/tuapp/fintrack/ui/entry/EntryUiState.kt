package com.tuapp.fintrack.ui.entry

import androidx.annotation.StringRes
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
    @param:StringRes val amountError: Int? = null,
    @param:StringRes val categoryError: Int? = null,
    val isSaving: Boolean = false,
    val savedEvent: SavedEvent? = null,
    @param:StringRes val saveError: Int? = null,
    /** Sum of active RESERVE rows in the current budget period. */
    val reservedAvailableCents: Long = 0L,
    val deductFromReserved: Boolean = false
) {
    val isEditing: Boolean get() = transactionId != null

    val canDeductFromReserved: Boolean
        get() = !isEditing &&
            type == TransactionType.EXPENSE &&
            reservedAvailableCents > 0L &&
            amountCents > 0L

    val willDeductFromReserved: Boolean get() = deductFromReserved && canDeductFromReserved
}

enum class SavedEvent { CREATED, UPDATED }

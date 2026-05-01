package com.tuapp.fintrack.ui.entry

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.fintrack.data.model.Category
import com.tuapp.fintrack.data.model.CategoryApplicability
import com.tuapp.fintrack.data.model.Transaction
import com.tuapp.fintrack.data.model.TransactionType
import com.tuapp.fintrack.data.repository.FinTrackRepository
import com.tuapp.fintrack.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EntryViewModel @Inject constructor(
    private val repository: FinTrackRepository,
    private val settings: SettingsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(EntryUiState())
    val uiState: StateFlow<EntryUiState> = _uiState.asStateFlow()

    private val editingTransactionId: Long? =
        savedStateHandle.get<Long>("transactionId")?.takeIf { it != -1L }

    init {
        observeSettings()
        observeCategories()
        if (editingTransactionId != null) loadTransaction(editingTransactionId)
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settings.requireCategory.collectLatest { required ->
                _uiState.update { it.copy(requireCategory = required) }
            }
        }
    }

    private fun observeCategories() {
        viewModelScope.launch {
            repository.allCategories.collectLatest { cats ->
                val type = _uiState.value.type
                _uiState.update { it.copy(availableCategories = cats.forType(type)) }
            }
        }
    }

    private fun loadTransaction(id: Long) {
        viewModelScope.launch {
            val tx = repository.getTransactionById(id) ?: return@launch
            _uiState.update {
                it.copy(
                    transactionId = tx.id,
                    type = tx.type,
                    amountCents = tx.amountCents,
                    amountText = formatCentsToInput(tx.amountCents),
                    selectedCategoryId = tx.categoryId,
                    description = tx.description,
                    occurredAtMs = tx.occurredAt
                )
            }
        }
    }

    fun onTypeChanged(type: TransactionType) {
        viewModelScope.launch {
            repository.allCategories.collectLatest { cats ->
                _uiState.update {
                    it.copy(
                        type = type,
                        selectedCategoryId = null,
                        availableCategories = cats.forType(type)
                    )
                }
                return@collectLatest
            }
        }
    }

    fun onAmountTextChanged(text: String) {
        val cents = parseToCents(text)
        _uiState.update {
            it.copy(
                amountText = text,
                amountCents = cents,
                amountError = null
            )
        }
    }

    fun onCategorySelected(categoryId: Long?) {
        _uiState.update { it.copy(selectedCategoryId = categoryId, categoryError = null) }
    }

    fun onDescriptionChanged(text: String) {
        _uiState.update { it.copy(description = text) }
    }

    fun onDateChanged(dateMs: Long) {
        _uiState.update { it.copy(occurredAtMs = dateMs) }
    }

    fun onSavedEventConsumed() {
        _uiState.update { it.copy(savedEvent = false) }
    }

    fun addCategory(name: String, applicability: CategoryApplicability, colorHex: String) {
        viewModelScope.launch {
            val id = repository.addCategory(
                Category(
                    name = name.trim(),
                    applicability = applicability,
                    colorHex = colorHex,
                    createdAt = System.currentTimeMillis()
                )
            )
            _uiState.update { it.copy(selectedCategoryId = id, categoryError = null) }
        }
    }

    fun onSave() {
        val state = _uiState.value
        var hasError = false

        if (state.amountCents <= 0L) {
            _uiState.update { it.copy(amountError = "Amount must be greater than 0") }
            hasError = true
        }
        if (state.requireCategory && state.selectedCategoryId == null) {
            _uiState.update { it.copy(categoryError = "Category is required") }
            hasError = true
        }
        if (hasError) return

        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (state.transactionId != null) {
                repository.updateTransaction(
                    Transaction(
                        id = state.transactionId,
                        type = state.type,
                        amountCents = state.amountCents,
                        categoryId = state.selectedCategoryId,
                        description = state.description,
                        occurredAt = state.occurredAtMs,
                        createdAt = now
                    )
                )
            } else {
                repository.addTransaction(
                    Transaction(
                        type = state.type,
                        amountCents = state.amountCents,
                        categoryId = state.selectedCategoryId,
                        description = state.description,
                        occurredAt = state.occurredAtMs,
                        createdAt = now
                    )
                )
            }
            _uiState.update {
                EntryUiState(
                    requireCategory = state.requireCategory,
                    availableCategories = state.availableCategories,
                    savedEvent = true
                )
            }
        }
    }

    private fun List<Category>.forType(type: TransactionType): List<Category> = filter { cat ->
        when (type) {
            TransactionType.INCOME -> cat.applicability == CategoryApplicability.INCOME || cat.applicability == CategoryApplicability.BOTH
            TransactionType.EXPENSE -> cat.applicability == CategoryApplicability.EXPENSE || cat.applicability == CategoryApplicability.BOTH
        }
    }

    companion object {
        fun parseToCents(input: String): Long {
            val clean = input.replace(Regex("[^0-9.]"), "")
            return (clean.toDoubleOrNull() ?: 0.0).let { (it * 100).toLong() }
        }

        fun formatCentsToInput(cents: Long): String = "%.2f".format(cents / 100.0)
    }
}

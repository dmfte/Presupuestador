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
import com.tuapp.fintrack.CrashReporter
import com.tuapp.fintrack.domain.util.Money
import com.tuapp.fintrack.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
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

    private var allCategories: List<Category> = emptyList()
    private var reservePeriodStartMs: Long = 0L
    private var loadedTransaction: Transaction? = null

    private val editingTransactionId: Long? =
        savedStateHandle.get<Long>("transactionId")?.takeIf { it != -1L }

    init {
        val typeArg = savedStateHandle.get<String>("type")
        when (typeArg) {
            "INCOME" -> _uiState.update { it.copy(type = TransactionType.INCOME) }
            "RESERVE" -> _uiState.update { it.copy(type = TransactionType.RESERVE) }
        }
        observeSettings()
        observeCategories()
        observeReserved()
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
                allCategories = cats
                _uiState.update { it.copy(availableCategories = cats.forType(it.type)) }
            }
        }
    }

    /** Mirrors HomeViewModel: reserved = active RESERVE rows since the carry-forward epoch. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeReserved() {
        viewModelScope.launch {
            settings.carryForwardEpoch.flatMapLatest { epoch ->
                repository.totalsByTypeSince(epoch).map { totals ->
                    epoch to (totals[TransactionType.RESERVE] ?: 0L)
                }
            }.collectLatest { (epoch, reserved) ->
                reservePeriodStartMs = epoch
                _uiState.update { it.copy(reservedAvailableCents = reserved) }
            }
        }
    }

    private fun loadTransaction(id: Long) {
        viewModelScope.launch {
            val tx = repository.getTransactionById(id) ?: return@launch
            loadedTransaction = tx
            _uiState.update {
                it.copy(
                    transactionId = tx.id,
                    type = tx.type,
                    amountCents = tx.amountCents,
                    amountText = formatCentsToInput(tx.amountCents),
                    selectedCategoryId = tx.categoryId,
                    description = tx.description,
                    occurredAtMs = tx.occurredAt,
                    availableCategories = allCategories.forType(tx.type)
                )
            }
        }
    }

    fun onTypeChanged(type: TransactionType) {
        _uiState.update {
            it.copy(
                type = type,
                selectedCategoryId = null,
                categoryError = null,
                availableCategories = allCategories.forType(type)
            )
        }
    }

    fun onDeductFromReservedChanged(checked: Boolean) {
        _uiState.update { it.copy(deductFromReserved = checked) }
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
        _uiState.update { it.copy(savedEvent = null) }
    }

    fun clearSaveError() {
        _uiState.update { it.copy(saveError = null) }
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
            _uiState.update { it.copy(amountError = R.string.amount_error) }
            hasError = true
        }
        if (state.requireCategory && state.selectedCategoryId == null) {
            _uiState.update { it.copy(categoryError = R.string.category_required_error) }
            hasError = true
        }
        if (hasError) return

        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val original = loadedTransaction
                if (state.transactionId != null && original != null) {
                    repository.updateTransaction(
                        original.copy(
                            type = state.type,
                            amountCents = state.amountCents,
                            categoryId = state.selectedCategoryId,
                            description = state.description,
                            occurredAt = state.occurredAtMs
                        )
                    )
                } else {
                    val tx = Transaction(
                        type = state.type,
                        amountCents = state.amountCents,
                        categoryId = state.selectedCategoryId,
                        description = state.description,
                        occurredAt = state.occurredAtMs,
                        createdAt = now
                    )
                    if (state.willDeductFromReserved) {
                        repository.addExpenseFromReserves(tx, reservePeriodStartMs)
                    } else {
                        repository.addTransaction(tx)
                    }
                }
                _uiState.update {
                    if (state.transactionId != null) {
                        it.copy(isSaving = false, savedEvent = SavedEvent.UPDATED)
                    } else {
                        EntryUiState(
                            requireCategory = state.requireCategory,
                            availableCategories = allCategories.forType(TransactionType.EXPENSE),
                            reservedAvailableCents = it.reservedAvailableCents,
                            savedEvent = SavedEvent.CREATED
                        )
                    }
                }
            } catch (e: Exception) {
                CrashReporter.log(e, "Failed to save transaction")
                _uiState.update { it.copy(isSaving = false, saveError = R.string.entry_save_error) }
            }
        }
    }

    private fun List<Category>.forType(type: TransactionType): List<Category> = filter { cat ->
        when (type) {
            TransactionType.INCOME -> cat.applicability == CategoryApplicability.INCOME || cat.applicability == CategoryApplicability.BOTH
            TransactionType.EXPENSE -> cat.applicability == CategoryApplicability.EXPENSE || cat.applicability == CategoryApplicability.BOTH
            TransactionType.RESERVE -> cat.applicability == CategoryApplicability.EXPENSE || cat.applicability == CategoryApplicability.BOTH
        }
    }

    companion object {
        fun parseToCents(input: String): Long =
            Money.parseToCentsOrNull(input)?.coerceAtLeast(0L) ?: 0L

        fun formatCentsToInput(cents: Long): String = Money.centsToPlain(cents)
    }
}

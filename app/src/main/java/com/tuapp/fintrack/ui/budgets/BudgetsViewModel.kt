package com.tuapp.fintrack.ui.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.fintrack.data.model.Budget
import com.tuapp.fintrack.data.model.Category
import com.tuapp.fintrack.data.model.PayCycle
import com.tuapp.fintrack.data.model.TransactionType
import com.tuapp.fintrack.data.repository.FinTrackRepository
import com.tuapp.fintrack.domain.model.BudgetWithProgress
import com.tuapp.fintrack.domain.usecase.GetCurrentPeriodUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BudgetsUiState(
    val snackbarMessage: String? = null,
    val pendingRestoreId: Long? = null
)

@HiltViewModel
class BudgetsViewModel @Inject constructor(
    private val repository: FinTrackRepository,
    private val getCurrentPeriod: GetCurrentPeriodUseCase
) : ViewModel() {

    val budgets: StateFlow<List<BudgetWithProgress>> =
        combine(
            repository.allBudgets,
            repository.allCategories,
            repository.allPayCycles,
            repository.allTransactions
        ) { budgets, categories, cycles, transactions ->
            val period = getCurrentPeriod()
            val categoryMap = categories.associateBy { it.id }
            val cycleMap = cycles.associateBy { it.id }

            val periodTransactions = transactions.filter {
                it.deletedAt == null &&
                    it.type == TransactionType.EXPENSE &&
                    it.occurredAt in period.startDateMs..period.endDateMs
            }

            budgets.mapNotNull { budget ->
                val category = categoryMap[budget.categoryId] ?: return@mapNotNull null
                val cycle = cycleMap[budget.cycleId] ?: return@mapNotNull null
                val spent = periodTransactions
                    .filter { it.categoryId == budget.categoryId }
                    .sumOf { it.amountCents }
                val progress = if (budget.amountCents > 0)
                    ((spent * 100L) / budget.amountCents).toInt()
                else 0
                BudgetWithProgress(
                    budget = budget,
                    category = category,
                    cycle = cycle,
                    currentPeriodSpendingCents = spent,
                    progressPercent = progress
                )
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val categories: StateFlow<List<Category>> = repository.allCategories
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val payCycles: StateFlow<List<PayCycle>> = repository.allPayCycles
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _uiState = MutableStateFlow(BudgetsUiState())
    val uiState: StateFlow<BudgetsUiState> = _uiState.asStateFlow()

    fun onAddBudget(categoryId: Long, cycleId: Long, amountCents: Long) {
        viewModelScope.launch {
            if (repository.hasDuplicateBudget(categoryId, cycleId)) {
                _uiState.update { it.copy(snackbarMessage = "Budget already exists for this category and cycle") }
                return@launch
            }
            val id = repository.addBudget(
                Budget(
                    categoryId = categoryId,
                    cycleId = cycleId,
                    amountCents = amountCents,
                    createdAt = System.currentTimeMillis()
                )
            )
            val catName = categories.value.find { it.id == categoryId }?.name ?: ""
            val formatted = formatCents(amountCents)
            _uiState.update { it.copy(snackbarMessage = "Budget created: $formatted for $catName") }
        }
    }

    fun onEditBudget(id: Long, amountCents: Long) {
        viewModelScope.launch {
            val existing = repository.getBudgetById(id) ?: return@launch
            repository.updateBudget(existing.copy(amountCents = amountCents))
            _uiState.update { it.copy(snackbarMessage = "Budget updated") }
        }
    }

    fun onDeleteBudget(id: Long) {
        viewModelScope.launch {
            repository.deactivateBudget(id)
            _uiState.update { it.copy(snackbarMessage = "Budget deleted", pendingRestoreId = id) }
        }
    }

    fun onUndoDelete(id: Long) {
        viewModelScope.launch {
            repository.reactivateBudget(id)
            _uiState.update { it.copy(snackbarMessage = null, pendingRestoreId = null) }
        }
    }

    fun onSnackbarConsumed() {
        _uiState.update { it.copy(snackbarMessage = null, pendingRestoreId = null) }
    }

    private fun formatCents(cents: Long): String {
        val dollars = cents / 100.0
        return java.text.NumberFormat.getCurrencyInstance(java.util.Locale.US).format(dollars)
    }
}

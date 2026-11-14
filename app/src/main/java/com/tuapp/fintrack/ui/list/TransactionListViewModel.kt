package com.tuapp.fintrack.ui.list

import java.time.YearMonth
import com.tuapp.fintrack.domain.util.Dates
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.fintrack.data.model.Transaction
import com.tuapp.fintrack.data.model.TransactionType
import com.tuapp.fintrack.data.repository.FinTrackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TransactionListViewModel @Inject constructor(
    private val repository: FinTrackRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransactionListUiState())
    val uiState: StateFlow<TransactionListUiState> = _uiState.asStateFlow()

    /** Latest unfiltered rows from Room; filters are re-applied to this in memory. */
    private var allTxs: List<Transaction> = emptyList()

    init {
        viewModelScope.launch {
            combine(
                repository.allTransactions,
                repository.allCategories
            ) { txs, cats -> txs to cats }
                .collectLatest { (txs, cats) ->
                    allTxs = txs
                    _uiState.update { state ->
                        state.copy(
                            transactions = applyFilters(state, txs),
                            categories = cats,
                            usedCategoryIds = txs.mapNotNullTo(mutableSetOf()) { it.categoryId }
                        )
                    }
                }
        }
    }

    fun onTypeFilterChanged(type: TransactionType?) {
        _uiState.update { it.copy(typeFilter = type) }
        reloadTransactions()
    }

    fun onCategoryFilterChanged(categoryId: Long?) {
        _uiState.update { it.copy(categoryFilter = categoryId) }
        reloadTransactions()
    }

    fun onDatePresetChanged(preset: DateRangePreset) {
        _uiState.update { it.copy(datePreset = preset, customStartMs = null, customEndMs = null) }
        reloadTransactions()
    }

    fun onCustomDateRangeChanged(startMs: Long?, endMs: Long?) {
        _uiState.update {
            it.copy(
                datePreset = DateRangePreset.CUSTOM,
                customStartMs = startMs,
                customEndMs = endMs
            )
        }
        reloadTransactions()
    }

    fun onSearchChanged(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        reloadTransactions()
    }

    fun onDeleteTransaction(id: Long) {
        viewModelScope.launch {
            repository.softDeleteTransaction(id)
            _uiState.update { it.copy(deletedTransactionId = id) }
        }
    }

    fun onRestoreTransaction(id: Long) {
        viewModelScope.launch {
            repository.restoreTransaction(id)
            _uiState.update { it.copy(deletedTransactionId = null) }
        }
    }

    fun onUndoConsumed() {
        _uiState.update { it.copy(deletedTransactionId = null) }
    }

    private fun reloadTransactions() {
        _uiState.update { state -> state.copy(transactions = applyFilters(state, allTxs)) }
    }

    private fun applyFilters(
        state: TransactionListUiState,
        allTxs: List<Transaction>
    ): List<Transaction> {
        val (startMs, endMs) = dateRange(state)

        return allTxs.filter { tx ->
            val typeOk = state.typeFilter == null || tx.type == state.typeFilter
            val catOk = state.categoryFilter == null || tx.categoryId == state.categoryFilter
            val dateOk = (startMs == null || tx.occurredAt >= startMs) &&
                    (endMs == null || tx.occurredAt <= endMs)
            val searchOk = state.searchQuery.isBlank() ||
                    tx.description.contains(state.searchQuery, ignoreCase = true)
            typeOk && catOk && dateOk && searchOk
        }
    }

    private fun dateRange(state: TransactionListUiState): Pair<Long?, Long?> {
        return when (state.datePreset) {
            DateRangePreset.ALL -> null to null
            DateRangePreset.CUSTOM -> state.customStartMs to state.customEndMs
            DateRangePreset.THIS_MONTH -> {
                val ym = YearMonth.now()
                Dates.monthBounds(ym.year, ym.monthValue)
            }
            DateRangePreset.LAST_MONTH -> {
                val ym = YearMonth.now().minusMonths(1)
                Dates.monthBounds(ym.year, ym.monthValue)
            }
        }
    }
}

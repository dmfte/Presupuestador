package com.tuapp.fintrack.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.fintrack.data.model.TransactionType
import com.tuapp.fintrack.data.repository.FinTrackRepository
import com.tuapp.fintrack.domain.model.PayPeriod
import com.tuapp.fintrack.domain.model.PeriodSummary
import com.tuapp.fintrack.domain.usecase.GetCurrentPeriodUseCase
import com.tuapp.fintrack.domain.usecase.RecordManualPayEventUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isPayEventLoading: Boolean = false,
    val lastSnackbar: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: FinTrackRepository,
    private val getCurrentPeriod: GetCurrentPeriodUseCase,
    private val recordManualPayEvent: RecordManualPayEventUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val periodSummary: StateFlow<PeriodSummary?> = repository.allTransactions
        .map { transactions ->
            val period = getCurrentPeriod()
            val periodTransactions = transactions.filter { tx ->
                tx.deletedAt == null &&
                    tx.occurredAt in period.startDateMs..period.endDateMs
            }
            val totalIncome = periodTransactions
                .filter { it.type == TransactionType.INCOME }
                .sumOf { it.amountCents }
            val totalExpenses = periodTransactions
                .filter { it.type == TransactionType.EXPENSE }
                .sumOf { it.amountCents }
            PeriodSummary(
                period = period,
                totalIncomeCents = totalIncome,
                totalExpensesCents = totalExpenses
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val currentPeriod: StateFlow<PayPeriod?> = periodSummary
        .map { it?.period }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onPaymentRecorded() {
        viewModelScope.launch {
            _uiState.update { it.copy(isPayEventLoading = true) }
            val recorded = recordManualPayEvent()
            val message = if (recorded) {
                "Pay event recorded — new period started"
            } else {
                "A pay event already exists for today"
            }
            _uiState.update { it.copy(isPayEventLoading = false, lastSnackbar = message) }
        }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(lastSnackbar = null) }
    }
}

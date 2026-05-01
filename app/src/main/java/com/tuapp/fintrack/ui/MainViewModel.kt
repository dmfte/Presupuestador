package com.tuapp.fintrack.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.fintrack.data.model.Transaction
import com.tuapp.fintrack.data.model.TransactionType
import com.tuapp.fintrack.data.repository.FinTrackRepository
import com.tuapp.fintrack.domain.model.PayPeriod
import com.tuapp.fintrack.domain.usecase.GetCurrentPeriodUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: FinTrackRepository,
    private val getCurrentPeriod: GetCurrentPeriodUseCase
) : ViewModel() {

    val transactions: StateFlow<List<Transaction>> = repository.allTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun insertDebugTransaction() {
        viewModelScope.launch {
            repository.addTransaction(
                Transaction(
                    type = TransactionType.EXPENSE,
                    amountCents = 1234L,
                    description = "Debug coffee",
                    occurredAt = System.currentTimeMillis(),
                    createdAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun loadCurrentPeriod(onResult: (PayPeriod) -> Unit) {
        viewModelScope.launch {
            onResult(getCurrentPeriod())
        }
    }
}

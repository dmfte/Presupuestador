package com.tuapp.fintrack.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.fintrack.data.model.Transaction
import com.tuapp.fintrack.data.model.TransactionType
import com.tuapp.fintrack.data.repository.FinTrackRepository
import com.tuapp.fintrack.data.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: FinTrackRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val transactions: StateFlow<List<Transaction>> = repository.allTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val showStartingBalancePrompt: StateFlow<Boolean> =
        settingsRepository.hasSeenStartingBalancePrompt
            .map { !it }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setStartingBalance(cents: Long) {
        viewModelScope.launch { settingsRepository.setStartingBalance(cents) }
    }

    fun skipStartingBalancePrompt() {
        viewModelScope.launch { settingsRepository.setStartingBalance(0L) }
    }

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
}

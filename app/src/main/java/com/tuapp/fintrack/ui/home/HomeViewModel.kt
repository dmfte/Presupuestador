package com.tuapp.fintrack.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.fintrack.data.model.TransactionType
import com.tuapp.fintrack.data.repository.FinTrackRepository
import com.tuapp.fintrack.data.settings.SettingsRepository
import com.tuapp.fintrack.domain.model.MonthPeriod
import com.tuapp.fintrack.domain.model.PeriodSummary
import com.tuapp.fintrack.widget.refreshWidgetPeriodSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: FinTrackRepository,
    private val settingsRepository: SettingsRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    val startingBalanceCents: StateFlow<Long> = settingsRepository.startingBalanceCents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val periodSummary: StateFlow<PeriodSummary?> = combine(
        repository.allTransactions,
        settingsRepository.carryForwardEpoch
    ) { transactions, epoch ->
        val periodTransactions = transactions.filter { tx ->
            tx.deletedAt == null && tx.occurredAt >= epoch
        }
        val totalIncome = periodTransactions
            .filter { it.type == TransactionType.INCOME }
            .sumOf { it.amountCents }
        val totalExpenses = periodTransactions
            .filter { it.type == TransactionType.EXPENSE }
            .sumOf { it.amountCents }
        val totalReserved = periodTransactions
            .filter { it.type == TransactionType.RESERVE }
            .sumOf { it.amountCents }
        val cal = Calendar.getInstance().apply {
            timeInMillis = if (epoch > 0L) epoch else System.currentTimeMillis()
        }
        PeriodSummary(
            period = MonthPeriod(
                year = cal.get(Calendar.YEAR),
                month = cal.get(Calendar.MONTH),
                startDateMs = epoch,
                endDateMs = Long.MAX_VALUE
            ),
            totalIncomeCents = totalIncome,
            totalExpensesCents = totalExpenses,
            totalReservedCents = totalReserved
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val currentPeriod: StateFlow<MonthPeriod?> = periodSummary
        .map { it?.period }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val availableBalanceCents: StateFlow<Long> = combine(
        startingBalanceCents,
        periodSummary
    ) { startingBalance, summary ->
        startingBalance + (summary?.netCents ?: 0L)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    fun resetBudgetPeriod() {
        viewModelScope.launch {
            settingsRepository.recordCarryForward(
                periodStartMs = System.currentTimeMillis(),
                newStartingBalanceCents = availableBalanceCents.value
            )
        }
    }
}

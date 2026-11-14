package com.tuapp.fintrack.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.fintrack.data.model.Transaction
import com.tuapp.fintrack.data.model.TransactionType
import com.tuapp.fintrack.data.repository.FinTrackRepository
import com.tuapp.fintrack.data.settings.SettingsRepository
import com.tuapp.fintrack.domain.model.MonthPeriod
import com.tuapp.fintrack.domain.model.PeriodSummary
import com.tuapp.fintrack.domain.util.Dates
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    repository: FinTrackRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val startingBalanceCents: StateFlow<Long> = settingsRepository.startingBalanceCents
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    /** Period totals since the carry-forward epoch, aggregated in SQLite (no full-table load). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val periodSummary: StateFlow<PeriodSummary?> = settingsRepository.carryForwardEpoch
        .flatMapLatest { epoch -> repository.totalsByTypeSince(epoch).map { epoch to it } }
        .map { (epoch, totals) ->
            val ym = Dates.yearMonthOf(if (epoch > 0L) epoch else System.currentTimeMillis())
            PeriodSummary(
                period = MonthPeriod(
                    year = ym.year,
                    month = ym.monthValue,
                    startDateMs = epoch,
                    endDateMs = Long.MAX_VALUE
                ),
                totalIncomeCents = totals[TransactionType.INCOME] ?: 0L,
                totalExpensesCents = totals[TransactionType.EXPENSE] ?: 0L,
                totalReservedCents = totals[TransactionType.RESERVE] ?: 0L
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val recentTransactions: StateFlow<List<Transaction>> = repository.recentTransactions(RECENT_LIMIT)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val transactionCount: StateFlow<Int> = repository.activeTransactionCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

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

    private companion object {
        const val RECENT_LIMIT = 5
    }
}

package com.tuapp.fintrack.ui.paycycles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.fintrack.data.model.PayCycle
import com.tuapp.fintrack.data.repository.FinTrackRepository
import com.tuapp.fintrack.domain.model.PayCycleRule
import com.tuapp.fintrack.domain.model.PayCycleWithEffectiveDate
import com.tuapp.fintrack.domain.model.toJson
import com.tuapp.fintrack.domain.model.toPayCycleRule
import com.tuapp.fintrack.domain.usecase.HolidayResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class PayCyclesViewModel @Inject constructor(
    private val repository: FinTrackRepository,
    private val holidayResolver: HolidayResolver
) : ViewModel() {

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    // Track deleted cycle for undo support
    private val deletedCycles = mutableMapOf<Long, PayCycle>()

    val payCycles: StateFlow<List<PayCycleWithEffectiveDate>> = repository.allPayCycles
        .map { cycles ->
            cycles.mapNotNull { cycle ->
                val rule = try {
                    cycle.rule.toPayCycleRule()
                } catch (e: Exception) {
                    return@mapNotNull null
                }
                val (nominal, effective) = computeNextPayday(cycle, rule)
                PayCycleWithEffectiveDate(
                    cycle = cycle,
                    rule = rule,
                    nextNominalDateMs = nominal,
                    nextEffectiveDateMs = effective
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun onAddPayCycle(
        rule: PayCycleRule,
        rollBackOnWeekend: Boolean,
        rollBackOnHoliday: Boolean
    ) {
        viewModelScope.launch {
            val cycle = PayCycle(
                rule = rule.toJson(),
                rollBackOnWeekend = rollBackOnWeekend,
                rollBackOnHoliday = rollBackOnHoliday,
                createdAt = System.currentTimeMillis()
            )
            repository.addPayCycle(cycle)
            _snackbarMessage.update { "Pay cycle added" }
        }
    }

    fun onEditPayCycle(
        id: Long,
        rule: PayCycleRule,
        rollBackOnWeekend: Boolean,
        rollBackOnHoliday: Boolean
    ) {
        viewModelScope.launch {
            val existing = payCycles.value.find { it.cycle.id == id }?.cycle ?: return@launch
            val updated = existing.copy(
                rule = rule.toJson(),
                rollBackOnWeekend = rollBackOnWeekend,
                rollBackOnHoliday = rollBackOnHoliday
            )
            repository.updatePayCycle(updated)
            _snackbarMessage.update { "Pay cycle updated" }
        }
    }

    fun onDeletePayCycle(id: Long) {
        viewModelScope.launch {
            val cycle = payCycles.value.find { it.cycle.id == id }?.cycle
            if (cycle != null) {
                deletedCycles[id] = cycle
            }
            repository.deactivatePayCycle(id)
            _snackbarMessage.update { "Pay cycle deleted" }
        }
    }

    fun onUndoDelete(id: Long) {
        viewModelScope.launch {
            repository.reactivatePayCycle(id)
            deletedCycles.remove(id)
            _snackbarMessage.update { null }
        }
    }

    fun clearSnackbar() {
        _snackbarMessage.update { null }
    }

    private suspend fun computeNextPayday(cycle: PayCycle, rule: PayCycleRule): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        val todayStart = startOfDay(now)
        val windowEnd = now + TimeUnit.DAYS.toMillis(60)

        val candidates = mutableListOf<Long>()

        when (rule) {
            is PayCycleRule.DayOfMonthRule -> {
                val cal = Calendar.getInstance().apply { timeInMillis = todayStart }
                cal.set(Calendar.DAY_OF_MONTH, 1)
                repeat(3) {
                    val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                    val targetDay = minOf(rule.day, maxDay)
                    cal.set(Calendar.DAY_OF_MONTH, targetDay)
                    val nominal = startOfDay(cal.timeInMillis)
                    if (nominal >= todayStart) candidates.add(nominal)
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    cal.add(Calendar.MONTH, 1)
                }
            }

            is PayCycleRule.LastDayOfMonthRule -> {
                val cal = Calendar.getInstance().apply { timeInMillis = todayStart }
                cal.set(Calendar.DAY_OF_MONTH, 1)
                repeat(3) {
                    val lastDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                    cal.set(Calendar.DAY_OF_MONTH, lastDay)
                    val nominal = startOfDay(cal.timeInMillis)
                    if (nominal >= todayStart) candidates.add(nominal)
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    cal.add(Calendar.MONTH, 1)
                }
            }

            is PayCycleRule.SpecificDateRule -> {
                val nominal = startOfDay(rule.dateMs)
                if (nominal >= todayStart) candidates.add(nominal)
            }

            is PayCycleRule.WeeklyRule -> {
                val cal = Calendar.getInstance().apply { timeInMillis = todayStart }
                var count = 0
                while (count < 3 && !cal.after(Calendar.getInstance().apply { timeInMillis = windowEnd })) {
                    if (cal.get(Calendar.DAY_OF_WEEK) == rule.dayOfWeekAnchor) {
                        candidates.add(startOfDay(cal.timeInMillis))
                        count++
                    }
                    cal.add(Calendar.DAY_OF_MONTH, 1)
                }
            }

            is PayCycleRule.BiweeklyRule -> {
                val anchor = startOfDay(rule.anchorDateMs)
                var current = anchor
                while (current < todayStart) current += TimeUnit.DAYS.toMillis(14)
                repeat(3) {
                    candidates.add(current)
                    current += TimeUnit.DAYS.toMillis(14)
                }
            }
        }

        val nextNominal = candidates.firstOrNull() ?: (todayStart + TimeUnit.DAYS.toMillis(30))
        val nextEffective = applyRollBack(nextNominal, cycle)

        return Pair(nextNominal, nextEffective)
    }

    private suspend fun applyRollBack(nominalDateMs: Long, cycle: PayCycle): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = nominalDateMs }
        while (true) {
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
            val isWeekend = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
            val isHoliday = cycle.rollBackOnHoliday && holidayResolver.isHoliday(cal.timeInMillis)
            val needsRollback = (isWeekend && cycle.rollBackOnWeekend) || isHoliday
            if (!needsRollback) break
            cal.add(Calendar.DAY_OF_MONTH, -1)
        }
        return cal.timeInMillis
    }

    private fun startOfDay(epochMs: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}

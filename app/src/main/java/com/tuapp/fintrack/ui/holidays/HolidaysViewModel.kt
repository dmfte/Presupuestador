package com.tuapp.fintrack.ui.holidays

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.fintrack.data.model.Holiday
import com.tuapp.fintrack.data.repository.FinTrackRepository
import com.tuapp.fintrack.domain.usecase.HolidayResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HolidaysViewModel @Inject constructor(
    private val repository: FinTrackRepository,
    private val holidayResolver: HolidayResolver
) : ViewModel() {

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    // Store deleted holidays for undo
    private val deletedHolidays = mutableMapOf<Long, Holiday>()

    val holidays: StateFlow<List<Holiday>> = repository.allHolidays
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun onAddHoliday(label: String, dateMs: Long, recurringYearly: Boolean) {
        viewModelScope.launch {
            repository.addHoliday(
                Holiday(
                    label = label,
                    dateOfYear = dateMs,
                    recurringYearly = recurringYearly,
                    enabled = true,
                    createdAt = System.currentTimeMillis()
                )
            )
            holidayResolver.invalidate()
            _snackbarMessage.update { "Holiday \"$label\" added" }
        }
    }

    fun onEditHoliday(id: Long, label: String, dateMs: Long, recurringYearly: Boolean) {
        viewModelScope.launch {
            val existing = holidays.value.find { it.id == id } ?: return@launch
            repository.updateHoliday(
                existing.copy(label = label, dateOfYear = dateMs, recurringYearly = recurringYearly)
            )
            holidayResolver.invalidate()
            _snackbarMessage.update { "Holiday updated" }
        }
    }

    fun onToggleHoliday(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            repository.setHolidayEnabled(id, enabled)
            holidayResolver.invalidate()
        }
    }

    fun onDeleteHoliday(id: Long) {
        viewModelScope.launch {
            val holiday = holidays.value.find { it.id == id }
            if (holiday != null) {
                deletedHolidays[id] = holiday
            }
            repository.deleteHoliday(id)
            holidayResolver.invalidate()
            _snackbarMessage.update { "Holiday deleted" }
        }
    }

    fun onUndoDelete(id: Long) {
        viewModelScope.launch {
            val holiday = deletedHolidays.remove(id) ?: return@launch
            repository.addHoliday(holiday)
            holidayResolver.invalidate()
            _snackbarMessage.update { null }
        }
    }

    fun clearSnackbar() {
        _snackbarMessage.update { null }
    }
}

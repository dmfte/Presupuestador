package com.tuapp.fintrack.domain.usecase

import com.tuapp.fintrack.data.dao.HolidayDao
import com.tuapp.fintrack.data.dao.PayCycleDao
import com.tuapp.fintrack.data.dao.PayEventDao
import com.tuapp.fintrack.data.model.Holiday
import com.tuapp.fintrack.data.model.PayCycle
import com.tuapp.fintrack.data.model.PayEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakePayCycleDao : PayCycleDao {
    private val _cycles = mutableListOf<PayCycle>()
    var cycles: List<PayCycle>
        get() = _cycles.toList()
        set(value) { _cycles.clear(); _cycles.addAll(value) }

    private val _cyclesFlow = kotlinx.coroutines.flow.MutableStateFlow<List<PayCycle>>(emptyList())

    override fun getAllActive(): Flow<List<PayCycle>> = _cyclesFlow
    override suspend fun getAllActiveOnce(): List<PayCycle> = _cycles.filter { it.active }

    override suspend fun insert(cycle: PayCycle): Long {
        val id = (_cycles.maxOfOrNull { it.id } ?: 0L) + 1L
        val withId = cycle.copy(id = id)
        _cycles.add(withId)
        _cyclesFlow.value = _cycles.filter { it.active }
        return id
    }

    override suspend fun update(cycle: PayCycle) {
        val idx = _cycles.indexOfFirst { it.id == cycle.id }
        if (idx >= 0) {
            _cycles[idx] = cycle
            _cyclesFlow.value = _cycles.filter { it.active }
        }
    }

    override suspend fun deactivate(id: Long) {
        val idx = _cycles.indexOfFirst { it.id == id }
        if (idx >= 0) {
            _cycles[idx] = _cycles[idx].copy(active = false)
            _cyclesFlow.value = _cycles.filter { it.active }
        }
    }

    override suspend fun reactivate(id: Long) {
        val idx = _cycles.indexOfFirst { it.id == id }
        if (idx >= 0) {
            _cycles[idx] = _cycles[idx].copy(active = true)
            _cyclesFlow.value = _cycles.filter { it.active }
        }
    }
}

class FakePayEventDao : PayEventDao {
    private val _events = mutableListOf<PayEvent>()
    var events: List<PayEvent>
        get() = _events.toList()
        set(value) { _events.clear(); _events.addAll(value) }

    override fun getAllActive(): Flow<List<PayEvent>> = flowOf(_events.toList())
    override suspend fun getAllOnce(): List<PayEvent> = _events.toList()

    override suspend fun insert(event: PayEvent): Long {
        val id = (_events.maxOfOrNull { it.id } ?: 0L) + 1L
        _events.add(event.copy(id = id))
        return id
    }

    override suspend fun getByDateRange(startMs: Long, endMs: Long): List<PayEvent> =
        _events.filter { it.occurredAt in startMs..endMs }

    override suspend fun countInRange(startMs: Long, endMs: Long): Int =
        _events.count { it.occurredAt in startMs..endMs }

    override suspend fun existsForDate(startMs: Long, endMs: Long): Boolean =
        _events.any { it.occurredAt in startMs..endMs }
}

class FakeHolidayDao : HolidayDao {
    private val _holidays = mutableListOf<Holiday>()
    var holidays: List<Holiday>
        get() = _holidays.toList()
        set(value) { _holidays.clear(); _holidays.addAll(value) }

    private val _holidaysFlow = kotlinx.coroutines.flow.MutableStateFlow<List<Holiday>>(emptyList())
    private val _allHolidaysFlow = kotlinx.coroutines.flow.MutableStateFlow<List<Holiday>>(emptyList())

    override fun getAllActive(): Flow<List<Holiday>> = _holidaysFlow
    override suspend fun getAllActiveOnce(): List<Holiday> = _holidays.filter { it.enabled }
    override fun getAll(): Flow<List<Holiday>> = _allHolidaysFlow

    override suspend fun insert(holiday: Holiday): Long {
        val id = if (holiday.id != 0L) holiday.id else (_holidays.maxOfOrNull { it.id } ?: 0L) + 1L
        val withId = holiday.copy(id = id)
        _holidays.removeIf { it.id == id }
        _holidays.add(withId)
        _holidaysFlow.value = _holidays.filter { it.enabled }
        _allHolidaysFlow.value = _holidays.toList()
        return id
    }

    override suspend fun update(holiday: Holiday) {
        val idx = _holidays.indexOfFirst { it.id == holiday.id }
        if (idx >= 0) {
            _holidays[idx] = holiday
            _holidaysFlow.value = _holidays.filter { it.enabled }
            _allHolidaysFlow.value = _holidays.toList()
        }
    }

    override suspend fun setEnabled(id: Long, enabled: Boolean) {
        val idx = _holidays.indexOfFirst { it.id == id }
        if (idx >= 0) {
            _holidays[idx] = _holidays[idx].copy(enabled = enabled)
            _holidaysFlow.value = _holidays.filter { it.enabled }
            _allHolidaysFlow.value = _holidays.toList()
        }
    }

    override suspend fun delete(id: Long) {
        _holidays.removeIf { it.id == id }
        _holidaysFlow.value = _holidays.filter { it.enabled }
        _allHolidaysFlow.value = _holidays.toList()
    }
}

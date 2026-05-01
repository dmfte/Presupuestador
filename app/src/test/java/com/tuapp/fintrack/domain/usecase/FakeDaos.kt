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
    var cycles: List<PayCycle> = emptyList()

    override fun getAllActive(): Flow<List<PayCycle>> = flowOf(cycles)
    override suspend fun getAllActiveOnce(): List<PayCycle> = cycles
    override suspend fun insert(cycle: PayCycle): Long = 0L
    override suspend fun update(cycle: PayCycle) {}
    override suspend fun deactivate(id: Long) {}
}

class FakePayEventDao : PayEventDao {
    var events: List<PayEvent> = emptyList()

    override fun getAllActive(): Flow<List<PayEvent>> = flowOf(events)
    override suspend fun getAllOnce(): List<PayEvent> = events
    override suspend fun insert(event: PayEvent): Long = 0L
    override suspend fun getByDateRange(startMs: Long, endMs: Long): List<PayEvent> =
        events.filter { it.occurredAt in startMs..endMs }
    override suspend fun countInRange(startMs: Long, endMs: Long): Int =
        events.count { it.occurredAt in startMs..endMs }
}

class FakeHolidayDao : HolidayDao {
    var holidays: List<Holiday> = emptyList()

    override fun getAllActive(): Flow<List<Holiday>> = flowOf(holidays.filter { it.enabled })
    override suspend fun getAllActiveOnce(): List<Holiday> = holidays.filter { it.enabled }
    override fun getAll(): Flow<List<Holiday>> = flowOf(holidays)
    override suspend fun insert(holiday: Holiday): Long = 0L
    override suspend fun update(holiday: Holiday) {}
    override suspend fun setEnabled(id: Long, enabled: Boolean) {}
    override suspend fun delete(id: Long) {}
}

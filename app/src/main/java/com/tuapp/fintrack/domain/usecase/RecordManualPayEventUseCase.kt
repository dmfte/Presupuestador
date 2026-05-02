package com.tuapp.fintrack.domain.usecase

import com.tuapp.fintrack.data.model.PayEvent
import com.tuapp.fintrack.data.repository.FinTrackRepository
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordManualPayEventUseCase @Inject constructor(
    private val repository: FinTrackRepository,
    private val holidayResolver: HolidayResolver
) {
    suspend operator fun invoke(): Boolean {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfDay = cal.timeInMillis
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val endOfDay = cal.timeInMillis

        val exists = repository.existsPayEventForDate(startOfDay, endOfDay)
        if (exists) return false

        repository.addPayEvent(PayEvent(occurredAt = startOfDay, createdAt = now))
        holidayResolver.invalidate()
        return true
    }
}

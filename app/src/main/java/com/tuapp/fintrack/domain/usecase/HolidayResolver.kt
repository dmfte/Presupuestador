package com.tuapp.fintrack.domain.usecase

import com.tuapp.fintrack.data.dao.HolidayDao
import com.tuapp.fintrack.data.model.Holiday
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HolidayResolver @Inject constructor(
    private val holidayDao: HolidayDao
) {
    private var cachedHolidays: List<Holiday>? = null
    private var cacheValidMs: Long? = null
    private val cacheTtlMs = 60_000L

    suspend fun getHolidaysForYear(year: Int): List<Holiday> {
        val now = System.currentTimeMillis()
        val cached = cachedHolidays
        val validUntil = cacheValidMs
        if (cached != null && validUntil != null && (now - validUntil) < cacheTtlMs) {
            return cached.filter { it.matchesYear(year) }
        }
        val all = holidayDao.getAllActiveOnce()
        cachedHolidays = all
        cacheValidMs = now
        return all.filter { it.matchesYear(year) }
    }

    suspend fun isHoliday(dateMs: Long): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = dateMs }
        val year = cal.get(Calendar.YEAR)
        val holidays = getHolidaysForYear(year)
        return holidays.any { holiday -> holiday.matchesDate(dateMs) }
    }

    fun invalidate() {
        cachedHolidays = null
        cacheValidMs = null
    }

    private fun Holiday.matchesYear(year: Int): Boolean {
        if (recurringYearly) return true
        val cal = Calendar.getInstance().apply { timeInMillis = dateOfYear }
        return cal.get(Calendar.YEAR) == year
    }

    private fun Holiday.matchesDate(dateMs: Long): Boolean {
        if (!enabled) return false
        val targetCal = Calendar.getInstance().apply { timeInMillis = dateMs }
        val targetMonth = targetCal.get(Calendar.MONTH)
        val targetDay = targetCal.get(Calendar.DAY_OF_MONTH)

        val holidayCal = Calendar.getInstance().apply { timeInMillis = dateOfYear }
        val holidayMonth = holidayCal.get(Calendar.MONTH)
        val holidayDay = holidayCal.get(Calendar.DAY_OF_MONTH)

        return if (recurringYearly) {
            holidayMonth == targetMonth && holidayDay == targetDay
        } else {
            val targetYear = targetCal.get(Calendar.YEAR)
            val holidayYear = holidayCal.get(Calendar.YEAR)
            holidayYear == targetYear && holidayMonth == targetMonth && holidayDay == targetDay
        }
    }
}

package com.tuapp.fintrack.domain.usecase

import com.tuapp.fintrack.domain.model.MonthPeriod
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetCurrentMonthPeriodUseCase @Inject constructor() {
    operator fun invoke(now: Long = System.currentTimeMillis()): MonthPeriod {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1

        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis

        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val end = cal.timeInMillis

        return MonthPeriod(year = year, month = month, startDateMs = start, endDateMs = end)
    }
}

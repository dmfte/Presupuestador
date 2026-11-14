package com.tuapp.fintrack.domain.usecase

import com.tuapp.fintrack.domain.model.MonthPeriod
import com.tuapp.fintrack.domain.util.Dates
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetCurrentMonthPeriodUseCase @Inject constructor() {
    operator fun invoke(now: Long = System.currentTimeMillis()): MonthPeriod {
        val ym = Dates.yearMonthOf(now)
        val (start, end) = Dates.monthBounds(ym.year, ym.monthValue)
        return MonthPeriod(year = ym.year, month = ym.monthValue, startDateMs = start, endDateMs = end)
    }
}

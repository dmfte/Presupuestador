package com.tuapp.fintrack.domain.usecase

import com.tuapp.fintrack.data.dao.PayCycleDao
import com.tuapp.fintrack.data.dao.PayEventDao
import com.tuapp.fintrack.data.model.PayCycle
import com.tuapp.fintrack.domain.model.PayCycleRule
import com.tuapp.fintrack.domain.model.PayPeriod
import com.tuapp.fintrack.domain.model.toPayCycleRule
import java.util.Calendar
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetCurrentPeriodUseCase @Inject constructor(
    private val payCycleDao: PayCycleDao,
    private val payEventDao: PayEventDao,
    private val holidayResolver: HolidayResolver
) {
    suspend operator fun invoke(now: Long = System.currentTimeMillis()): PayPeriod {
        val cycles = payCycleDao.getAllActiveOnce()
        val payEvents = payEventDao.getAllOnce()

        val windowStart = now - TimeUnit.DAYS.toMillis(120)
        val windowEnd = now + TimeUnit.DAYS.toMillis(60)

        val allPaydays = mutableListOf<Long>()

        for (cycle in cycles) {
            allPaydays.addAll(computePaydaysInWindow(cycle, windowStart, windowEnd))
        }

        payEvents.forEach { allPaydays.add(it.occurredAt) }
        allPaydays.sort()

        val pastPaydays = allPaydays.filter { it <= now }.distinct().sorted()
        val futurePaydays = allPaydays.filter { it > now }.distinct().sorted()

        val periodStart = pastPaydays.lastOrNull() ?: startOfMonth(now)
        val periodEnd = futurePaydays.firstOrNull() ?: endOfMonth(now)
        val nextPayday = periodEnd

        val daysRemaining = TimeUnit.MILLISECONDS.toDays(periodEnd - now).toInt().coerceAtLeast(0)

        return PayPeriod(
            startDateMs = periodStart,
            endDateMs = periodEnd,
            nextPaydayMs = nextPayday,
            daysRemaining = daysRemaining
        )
    }

    private suspend fun computePaydaysInWindow(
        cycle: PayCycle,
        windowStart: Long,
        windowEnd: Long
    ): List<Long> {
        val rule = try {
            cycle.rule.toPayCycleRule()
        } catch (e: Exception) {
            return emptyList()
        }

        val result = mutableListOf<Long>()
        val startCal = Calendar.getInstance().apply { timeInMillis = windowStart }
        val endCal = Calendar.getInstance().apply { timeInMillis = windowEnd }

        when (rule) {
            is PayCycleRule.DayOfMonthRule -> {
                val cal = Calendar.getInstance().apply { timeInMillis = windowStart }
                cal.set(Calendar.DAY_OF_MONTH, 1)
                while (!cal.after(endCal)) {
                    val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                    val targetDay = minOf(rule.day, maxDay)
                    cal.set(Calendar.DAY_OF_MONTH, targetDay)
                    val nominal = startOfDay(cal.timeInMillis)
                    val effective = applyRollBack(nominal, cycle)
                    if (effective >= windowStart) result.add(effective)
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    cal.add(Calendar.MONTH, 1)
                }
            }

            is PayCycleRule.LastDayOfMonthRule -> {
                val cal = Calendar.getInstance().apply { timeInMillis = windowStart }
                cal.set(Calendar.DAY_OF_MONTH, 1)
                while (!cal.after(endCal)) {
                    val lastDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                    cal.set(Calendar.DAY_OF_MONTH, lastDay)
                    val nominal = startOfDay(cal.timeInMillis)
                    val effective = applyRollBack(nominal, cycle)
                    if (effective >= windowStart) result.add(effective)
                    cal.set(Calendar.DAY_OF_MONTH, 1)
                    cal.add(Calendar.MONTH, 1)
                }
            }

            is PayCycleRule.SpecificDateRule -> {
                val nominal = startOfDay(rule.dateMs)
                val effective = applyRollBack(nominal, cycle)
                if (effective in windowStart..windowEnd) result.add(effective)
            }

            is PayCycleRule.WeeklyRule -> {
                val cal = Calendar.getInstance().apply { timeInMillis = windowStart }
                while (!cal.after(endCal)) {
                    if (cal.get(Calendar.DAY_OF_WEEK) == rule.dayOfWeekAnchor) {
                        val nominal = startOfDay(cal.timeInMillis)
                        val effective = applyRollBack(nominal, cycle)
                        if (effective >= windowStart) result.add(effective)
                    }
                    cal.add(Calendar.DAY_OF_MONTH, 1)
                }
            }

            is PayCycleRule.BiweeklyRule -> {
                val anchor = startOfDay(rule.anchorDateMs)
                var current = anchor
                while (current < windowStart) current += TimeUnit.DAYS.toMillis(14)
                while (current <= windowEnd) {
                    val effective = applyRollBack(current, cycle)
                    if (effective >= windowStart) result.add(effective)
                    current += TimeUnit.DAYS.toMillis(14)
                }
            }
        }

        return result
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

    private fun startOfMonth(epochMs: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
        cal.set(Calendar.DAY_OF_MONTH, 1)
        return startOfDay(cal.timeInMillis)
    }

    private fun endOfMonth(epochMs: Long): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        return startOfDay(cal.timeInMillis)
    }
}

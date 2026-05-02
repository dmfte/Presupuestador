package com.tuapp.fintrack.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tuapp.fintrack.data.dao.PayCycleDao
import com.tuapp.fintrack.data.dao.PayEventDao
import com.tuapp.fintrack.data.model.PayEvent
import com.tuapp.fintrack.domain.model.PayCycleRule
import com.tuapp.fintrack.domain.model.toPayCycleRule
import com.tuapp.fintrack.domain.usecase.HolidayResolver
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit

@HiltWorker
class PayPeriodAdvanceWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val payCycleDao: PayCycleDao,
    private val payEventDao: PayEventDao,
    private val holidayResolver: HolidayResolver
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val todayStart = cal.timeInMillis
            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            cal.set(Calendar.MILLISECOND, 999)
            val todayEnd = cal.timeInMillis

            val cycles = payCycleDao.getAllActiveOnce()
            for (cycle in cycles) {
                val rule = try {
                    cycle.rule.toPayCycleRule()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse rule for cycle ${cycle.id}", e)
                    continue
                }

                val nominal = computeNominalPayday(rule, todayStart) ?: continue
                val effective = applyRollBack(nominal, cycle.rollBackOnWeekend, cycle.rollBackOnHoliday)

                if (effective == todayStart) {
                    val exists = payEventDao.existsForDate(todayStart, todayEnd)
                    if (!exists) {
                        payEventDao.insert(
                            PayEvent(
                                occurredAt = todayStart,
                                createdAt = System.currentTimeMillis()
                            )
                        )
                        Log.d(TAG, "Auto-inserted pay event for today (cycle ${cycle.id})")
                    }
                }
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in PayPeriodAdvanceWorker", e)
            Result.retry()
        }
    }

    /**
     * Returns todayStart if the given rule produces a payday today; null otherwise.
     */
    private fun computeNominalPayday(rule: PayCycleRule, todayStart: Long): Long? {
        val todayCal = Calendar.getInstance().apply { timeInMillis = todayStart }
        val todayDom = todayCal.get(Calendar.DAY_OF_MONTH)
        val todayDow = todayCal.get(Calendar.DAY_OF_WEEK)

        return when (rule) {
            is PayCycleRule.DayOfMonthRule -> {
                val maxDay = todayCal.getActualMaximum(Calendar.DAY_OF_MONTH)
                val targetDay = minOf(rule.day, maxDay)
                if (targetDay == todayDom) todayStart else null
            }

            is PayCycleRule.LastDayOfMonthRule -> {
                val lastDay = todayCal.getActualMaximum(Calendar.DAY_OF_MONTH)
                if (todayDom == lastDay) todayStart else null
            }

            is PayCycleRule.SpecificDateRule -> {
                val nominalDay = startOfDay(rule.dateMs)
                if (nominalDay == todayStart) todayStart else null
            }

            is PayCycleRule.WeeklyRule -> {
                if (todayDow == rule.dayOfWeekAnchor) todayStart else null
            }

            is PayCycleRule.BiweeklyRule -> {
                val anchor = startOfDay(rule.anchorDateMs)
                val diffMs = todayStart - anchor
                if (diffMs >= 0) {
                    val diffDays = TimeUnit.MILLISECONDS.toDays(diffMs)
                    if (diffDays % 14 == 0L) todayStart else null
                } else {
                    null
                }
            }
        }
    }

    private suspend fun applyRollBack(
        nominalDateMs: Long,
        rollBackOnWeekend: Boolean,
        rollBackOnHoliday: Boolean
    ): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = nominalDateMs }
        while (true) {
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
            val isWeekend = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
            val isHoliday = rollBackOnHoliday && holidayResolver.isHoliday(cal.timeInMillis)
            val needsRollback = (isWeekend && rollBackOnWeekend) || isHoliday
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

    companion object {
        private const val TAG = "PayPeriodAdvanceWorker"
    }
}

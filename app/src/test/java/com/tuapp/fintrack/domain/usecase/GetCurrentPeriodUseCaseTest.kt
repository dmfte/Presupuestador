package com.tuapp.fintrack.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.tuapp.fintrack.data.model.Holiday
import com.tuapp.fintrack.data.model.PayCycle
import com.tuapp.fintrack.data.model.PayEvent
import com.tuapp.fintrack.domain.model.PayCycleRule
import com.tuapp.fintrack.domain.model.toJson
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.concurrent.TimeUnit

class GetCurrentPeriodUseCaseTest {

    private lateinit var fakePayCycleDao: FakePayCycleDao
    private lateinit var fakePayEventDao: FakePayEventDao
    private lateinit var fakeHolidayDao: FakeHolidayDao
    private lateinit var holidayResolver: HolidayResolver
    private lateinit var useCase: GetCurrentPeriodUseCase

    @Before
    fun setUp() {
        fakePayCycleDao = FakePayCycleDao()
        fakePayEventDao = FakePayEventDao()
        fakeHolidayDao = FakeHolidayDao()
        holidayResolver = HolidayResolver(fakeHolidayDao)
        useCase = GetCurrentPeriodUseCase(fakePayCycleDao, fakePayEventDao, holidayResolver)
    }

    @Test
    fun `period with 15th and last day cycle - mid month returns correct boundaries`() = runTest {
        fakePayCycleDao.cycles = listOf(
            PayCycle(
                id = 1,
                rule = PayCycleRule.DayOfMonthRule(15).toJson(),
                rollBackOnWeekend = false,
                rollBackOnHoliday = false,
                active = true,
                createdAt = 0L
            ),
            PayCycle(
                id = 2,
                rule = PayCycleRule.LastDayOfMonthRule.toJson(),
                rollBackOnWeekend = false,
                rollBackOnHoliday = false,
                active = true,
                createdAt = 0L
            )
        )

        // Use a fixed date: 2026-05-20 (after the 15th, before end of month)
        val now = calMs(2026, Calendar.MAY, 20)
        val period = useCase(now)

        val start = Calendar.getInstance().apply { timeInMillis = period.startDateMs }
        val end = Calendar.getInstance().apply { timeInMillis = period.endDateMs }

        assertThat(start.get(Calendar.DAY_OF_MONTH)).isEqualTo(15)
        assertThat(start.get(Calendar.MONTH)).isEqualTo(Calendar.MAY)
        assertThat(end.get(Calendar.DAY_OF_MONTH)).isEqualTo(31)
        assertThat(end.get(Calendar.MONTH)).isEqualTo(Calendar.MAY)
        assertThat(period.daysRemaining).isGreaterThan(0)
    }

    @Test
    fun `period with 15th cycle - before 15th returns prior period`() = runTest {
        fakePayCycleDao.cycles = listOf(
            PayCycle(
                id = 1,
                rule = PayCycleRule.DayOfMonthRule(15).toJson(),
                rollBackOnWeekend = false,
                rollBackOnHoliday = false,
                active = true,
                createdAt = 0L
            )
        )

        val now = calMs(2026, Calendar.MAY, 10)
        val period = useCase(now)

        val start = Calendar.getInstance().apply { timeInMillis = period.startDateMs }
        val end = Calendar.getInstance().apply { timeInMillis = period.endDateMs }

        assertThat(start.get(Calendar.DAY_OF_MONTH)).isEqualTo(15)
        assertThat(start.get(Calendar.MONTH)).isEqualTo(Calendar.APRIL)
        assertThat(end.get(Calendar.DAY_OF_MONTH)).isEqualTo(15)
        assertThat(end.get(Calendar.MONTH)).isEqualTo(Calendar.MAY)
    }

    @Test
    fun `rollback skips weekend - Saturday cycles back to Friday`() = runTest {
        // 2026-05-30 is a Saturday
        fakePayCycleDao.cycles = listOf(
            PayCycle(
                id = 1,
                rule = PayCycleRule.DayOfMonthRule(30).toJson(),
                rollBackOnWeekend = true,
                rollBackOnHoliday = false,
                active = true,
                createdAt = 0L
            )
        )

        val now = calMs(2026, Calendar.MAY, 20)
        val period = useCase(now)

        val end = Calendar.getInstance().apply { timeInMillis = period.endDateMs }
        assertThat(end.get(Calendar.DAY_OF_WEEK)).isNotEqualTo(Calendar.SATURDAY)
        assertThat(end.get(Calendar.DAY_OF_WEEK)).isNotEqualTo(Calendar.SUNDAY)
    }

    @Test
    fun `rollback skips holiday`() = runTest {
        // Set up a holiday on May 15
        val holidayCal = Calendar.getInstance().apply { set(2025, Calendar.MAY, 15, 0, 0, 0) }
        fakeHolidayDao.holidays = listOf(
            Holiday(
                id = 1,
                label = "Test Holiday",
                dateOfYear = holidayCal.timeInMillis,
                recurringYearly = true,
                enabled = true,
                createdAt = 0L
            )
        )
        holidayResolver.invalidate()

        fakePayCycleDao.cycles = listOf(
            PayCycle(
                id = 1,
                rule = PayCycleRule.DayOfMonthRule(15).toJson(),
                rollBackOnWeekend = false,
                rollBackOnHoliday = true,
                active = true,
                createdAt = 0L
            )
        )

        val now = calMs(2026, Calendar.MAY, 20)
        val period = useCase(now)

        val start = Calendar.getInstance().apply { timeInMillis = period.startDateMs }
        assertThat(start.get(Calendar.DAY_OF_MONTH)).isNotEqualTo(15)
    }

    @Test
    fun `no cycles returns fallback period covering current month`() = runTest {
        fakePayCycleDao.cycles = emptyList()
        val now = calMs(2026, Calendar.MAY, 15)
        val period = useCase(now)
        assertThat(period.startDateMs).isLessThan(now)
        assertThat(period.endDateMs).isGreaterThan(now)
    }

    @Test
    fun `manual pay event overrides cycle boundary`() = runTest {
        fakePayCycleDao.cycles = listOf(
            PayCycle(
                id = 1,
                rule = PayCycleRule.DayOfMonthRule(15).toJson(),
                rollBackOnWeekend = false,
                rollBackOnHoliday = false,
                active = true,
                createdAt = 0L
            )
        )
        val manualPayMs = calMs(2026, Calendar.MAY, 18)
        fakePayEventDao.events = listOf(PayEvent(id = 1, occurredAt = manualPayMs, createdAt = manualPayMs))

        val now = calMs(2026, Calendar.MAY, 25)
        val period = useCase(now)

        val start = Calendar.getInstance().apply { timeInMillis = period.startDateMs }
        assertThat(start.get(Calendar.DAY_OF_MONTH)).isEqualTo(18)
    }

    private fun calMs(year: Int, month: Int, day: Int): Long {
        return Calendar.getInstance().apply {
            set(year, month, day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}

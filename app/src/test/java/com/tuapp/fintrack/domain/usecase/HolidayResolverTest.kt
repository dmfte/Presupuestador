package com.tuapp.fintrack.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.tuapp.fintrack.data.model.Holiday
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.util.Calendar

class HolidayResolverTest {

    private lateinit var resolver: HolidayResolver
    private lateinit var fakeDao: FakeHolidayDao

    @Before
    fun setUp() {
        fakeDao = FakeHolidayDao()
        resolver = HolidayResolver(fakeDao)
    }

    @Test
    fun `isHoliday returns true for a recurring holiday matching month and day`() = runTest {
        val cal = Calendar.getInstance()
        cal.set(2024, Calendar.JANUARY, 1, 0, 0, 0)
        val holiday2024 = Holiday(
            id = 1,
            label = "New Year",
            dateOfYear = cal.timeInMillis,
            recurringYearly = true,
            enabled = true,
            createdAt = System.currentTimeMillis()
        )
        fakeDao.holidays = listOf(holiday2024)
        resolver.invalidate()

        val target = Calendar.getInstance()
        target.set(2026, Calendar.JANUARY, 1, 0, 0, 0)
        assertThat(resolver.isHoliday(target.timeInMillis)).isTrue()
    }

    @Test
    fun `isHoliday returns false for non-matching date`() = runTest {
        val cal = Calendar.getInstance()
        cal.set(2025, Calendar.JANUARY, 1, 0, 0, 0)
        val holiday = Holiday(
            id = 1,
            label = "New Year",
            dateOfYear = cal.timeInMillis,
            recurringYearly = true,
            enabled = true,
            createdAt = System.currentTimeMillis()
        )
        fakeDao.holidays = listOf(holiday)
        resolver.invalidate()

        val target = Calendar.getInstance()
        target.set(2026, Calendar.JANUARY, 2, 0, 0, 0)
        assertThat(resolver.isHoliday(target.timeInMillis)).isFalse()
    }

    @Test
    fun `isHoliday returns false for disabled holiday`() = runTest {
        val cal = Calendar.getInstance()
        cal.set(2025, Calendar.JANUARY, 1, 0, 0, 0)
        val holiday = Holiday(
            id = 1,
            label = "New Year",
            dateOfYear = cal.timeInMillis,
            recurringYearly = true,
            enabled = false,
            createdAt = System.currentTimeMillis()
        )
        fakeDao.holidays = listOf(holiday)
        resolver.invalidate()

        val target = Calendar.getInstance()
        target.set(2026, Calendar.JANUARY, 1, 0, 0, 0)
        assertThat(resolver.isHoliday(target.timeInMillis)).isFalse()
    }

    @Test
    fun `non-recurring holiday only matches its exact year`() = runTest {
        val cal = Calendar.getInstance()
        cal.set(2025, Calendar.APRIL, 18, 0, 0, 0)
        val goodFriday = Holiday(
            id = 1,
            label = "Good Friday",
            dateOfYear = cal.timeInMillis,
            recurringYearly = false,
            enabled = true,
            createdAt = System.currentTimeMillis()
        )
        fakeDao.holidays = listOf(goodFriday)
        resolver.invalidate()

        val sameDay2025 = Calendar.getInstance().apply { set(2025, Calendar.APRIL, 18, 0, 0, 0) }
        assertThat(resolver.isHoliday(sameDay2025.timeInMillis)).isTrue()

        resolver.invalidate()
        val sameDay2026 = Calendar.getInstance().apply { set(2026, Calendar.APRIL, 18, 0, 0, 0) }
        assertThat(resolver.isHoliday(sameDay2026.timeInMillis)).isFalse()
    }

    @Test
    fun `invalidate clears cache and refetches`() = runTest {
        val cal = Calendar.getInstance()
        cal.set(2025, Calendar.JANUARY, 1, 0, 0, 0)
        val holiday = Holiday(
            id = 1,
            label = "New Year",
            dateOfYear = cal.timeInMillis,
            recurringYearly = true,
            enabled = true,
            createdAt = System.currentTimeMillis()
        )
        fakeDao.holidays = listOf(holiday)
        resolver.invalidate()

        val target = Calendar.getInstance().apply { set(2026, Calendar.JANUARY, 1, 0, 0, 0) }
        assertThat(resolver.isHoliday(target.timeInMillis)).isTrue()

        fakeDao.holidays = emptyList()
        resolver.invalidate()
        assertThat(resolver.isHoliday(target.timeInMillis)).isFalse()
    }
}

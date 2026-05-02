package com.tuapp.fintrack.ui.holidays

import com.google.common.truth.Truth.assertThat
import com.tuapp.fintrack.data.model.Holiday
import com.tuapp.fintrack.data.repository.FinTrackRepository
import com.tuapp.fintrack.domain.usecase.HolidayResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class HolidaysViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FinTrackRepository
    private lateinit var holidayResolver: HolidayResolver

    private val holidaysFlow = MutableStateFlow<List<Holiday>>(emptyList())

    private val now = System.currentTimeMillis()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mock()
        holidayResolver = mock()
        whenever(repository.allHolidays).thenReturn(holidaysFlow)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() = HolidaysViewModel(repository, holidayResolver)

    @Test
    fun `onAddHoliday calls repository and sets snackbar`() = runTest {
        whenever(repository.addHoliday(any())).thenReturn(1L)
        val vm = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onAddHoliday("New Year", now, recurringYearly = true)
        testDispatcher.scheduler.advanceUntilIdle()

        verify(repository).addHoliday(any())
        verify(holidayResolver).invalidate()
        assertThat(vm.snackbarMessage.value).contains("New Year")
    }

    @Test
    fun `onEditHoliday updates holiday and invalidates cache`() = runTest {
        val holiday = Holiday(
            id = 1L, label = "Old Name", dateOfYear = now,
            recurringYearly = true, enabled = true, createdAt = 0L
        )
        holidaysFlow.value = listOf(holiday)
        whenever(repository.updateHoliday(any())).then {}

        val vm = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onEditHoliday(1L, "New Name", now + 86_400_000L, recurringYearly = false)
        testDispatcher.scheduler.advanceUntilIdle()

        verify(repository).updateHoliday(any())
        verify(holidayResolver).invalidate()
        assertThat(vm.snackbarMessage.value).contains("updated")
    }

    @Test
    fun `onToggleHoliday sets enabled state and invalidates cache`() = runTest {
        val holiday = Holiday(
            id = 1L, label = "Holiday", dateOfYear = now,
            recurringYearly = true, enabled = true, createdAt = 0L
        )
        holidaysFlow.value = listOf(holiday)
        whenever(repository.setHolidayEnabled(any(), any())).then {}

        val vm = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onToggleHoliday(1L, false)
        testDispatcher.scheduler.advanceUntilIdle()

        verify(repository).setHolidayEnabled(1L, false)
        verify(holidayResolver).invalidate()
    }

    @Test
    fun `onDeleteHoliday deletes and sets snackbar`() = runTest {
        val holiday = Holiday(
            id = 1L, label = "Holiday", dateOfYear = now,
            recurringYearly = true, enabled = true, createdAt = 0L
        )
        holidaysFlow.value = listOf(holiday)
        whenever(repository.deleteHoliday(any())).then {}

        val vm = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onDeleteHoliday(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        verify(repository).deleteHoliday(1L)
        verify(holidayResolver).invalidate()
        assertThat(vm.snackbarMessage.value).contains("deleted")
    }

    @Test
    fun `onUndoDelete re-inserts deleted holiday`() = runTest {
        val holiday = Holiday(
            id = 1L, label = "Holiday", dateOfYear = now,
            recurringYearly = true, enabled = true, createdAt = 0L
        )
        holidaysFlow.value = listOf(holiday)
        whenever(repository.deleteHoliday(any())).then {}
        whenever(repository.addHoliday(any())).thenReturn(1L)

        val vm = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Delete first
        vm.onDeleteHoliday(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then undo
        vm.onUndoDelete(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        verify(repository).addHoliday(any())
        assertThat(vm.snackbarMessage.value).isNull()
    }

    @Test
    fun `clearSnackbar sets message to null`() = runTest {
        whenever(repository.addHoliday(any())).thenReturn(1L)
        val vm = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onAddHoliday("Test", now, recurringYearly = true)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(vm.snackbarMessage.value).isNotNull()

        vm.clearSnackbar()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(vm.snackbarMessage.value).isNull()
    }

    @Test
    fun `holidays flow emits from repository`() = runTest {
        val holiday = Holiday(
            id = 1L, label = "Christmas", dateOfYear = now,
            recurringYearly = true, enabled = true, createdAt = 0L
        )
        holidaysFlow.value = listOf(holiday)

        val vm = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(vm.holidays.value).hasSize(1)
        assertThat(vm.holidays.value[0].label).isEqualTo("Christmas")
    }
}

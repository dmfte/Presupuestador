package com.tuapp.fintrack.ui.paycycles

import com.google.common.truth.Truth.assertThat
import com.tuapp.fintrack.data.model.PayCycle
import com.tuapp.fintrack.data.repository.FinTrackRepository
import com.tuapp.fintrack.domain.model.PayCycleRule
import com.tuapp.fintrack.domain.model.toJson
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
class PayCyclesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FinTrackRepository
    private lateinit var holidayResolver: HolidayResolver

    private val payCyclesFlow = MutableStateFlow<List<PayCycle>>(emptyList())

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mock()
        holidayResolver = mock()
        whenever(repository.allPayCycles).thenReturn(payCyclesFlow)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() = PayCyclesViewModel(repository, holidayResolver)

    @Test
    fun `onAddPayCycle calls repository and sets snackbar`() = runTest {
        whenever(repository.addPayCycle(any())).thenReturn(1L)
        val vm = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onAddPayCycle(
            rule = PayCycleRule.DayOfMonthRule(15),
            rollBackOnWeekend = true,
            rollBackOnHoliday = true
        )
        testDispatcher.scheduler.advanceUntilIdle()

        verify(repository).addPayCycle(any())
        assertThat(vm.snackbarMessage.value).contains("added")
    }

    @Test
    fun `onEditPayCycle updates cycle and sets snackbar`() = runTest {
        val rule = PayCycleRule.DayOfMonthRule(15)
        val cycle = PayCycle(
            id = 1L,
            rule = rule.toJson(),
            rollBackOnWeekend = true,
            rollBackOnHoliday = true,
            active = true,
            createdAt = 0L
        )
        payCyclesFlow.value = listOf(cycle)
        whenever(holidayResolver.isHoliday(any())).thenReturn(false)
        whenever(repository.updatePayCycle(any())).then {}

        val vm = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onEditPayCycle(
            id = 1L,
            rule = PayCycleRule.LastDayOfMonthRule,
            rollBackOnWeekend = false,
            rollBackOnHoliday = true
        )
        testDispatcher.scheduler.advanceUntilIdle()

        verify(repository).updatePayCycle(any())
        assertThat(vm.snackbarMessage.value).contains("updated")
    }

    @Test
    fun `onDeletePayCycle deactivates and sets snackbar`() = runTest {
        val rule = PayCycleRule.DayOfMonthRule(15)
        val cycle = PayCycle(
            id = 1L,
            rule = rule.toJson(),
            rollBackOnWeekend = true,
            rollBackOnHoliday = true,
            active = true,
            createdAt = 0L
        )
        payCyclesFlow.value = listOf(cycle)
        whenever(holidayResolver.isHoliday(any())).thenReturn(false)
        whenever(repository.deactivatePayCycle(any())).then {}

        val vm = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onDeletePayCycle(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        verify(repository).deactivatePayCycle(1L)
        assertThat(vm.snackbarMessage.value).contains("deleted")
    }

    @Test
    fun `onUndoDelete reactivates cycle`() = runTest {
        val rule = PayCycleRule.DayOfMonthRule(15)
        val cycle = PayCycle(
            id = 1L,
            rule = rule.toJson(),
            rollBackOnWeekend = true,
            rollBackOnHoliday = true,
            active = true,
            createdAt = 0L
        )
        payCyclesFlow.value = listOf(cycle)
        whenever(holidayResolver.isHoliday(any())).thenReturn(false)
        whenever(repository.deactivatePayCycle(any())).then {}
        whenever(repository.reactivatePayCycle(any())).then {}

        val vm = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onDeletePayCycle(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onUndoDelete(1L)
        testDispatcher.scheduler.advanceUntilIdle()

        verify(repository).reactivatePayCycle(1L)
        assertThat(vm.snackbarMessage.value).isNull()
    }

    @Test
    fun `clearSnackbar sets message to null`() = runTest {
        whenever(repository.addPayCycle(any())).thenReturn(1L)
        val vm = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onAddPayCycle(PayCycleRule.LastDayOfMonthRule, rollBackOnWeekend = true, rollBackOnHoliday = false)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(vm.snackbarMessage.value).isNotNull()

        vm.clearSnackbar()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(vm.snackbarMessage.value).isNull()
    }

    @Test
    fun `payCycles flow maps cycles to PayCycleWithEffectiveDate`() = runTest {
        val rule = PayCycleRule.DayOfMonthRule(20)
        val cycle = PayCycle(
            id = 1L,
            rule = rule.toJson(),
            rollBackOnWeekend = false,
            rollBackOnHoliday = false,
            active = true,
            createdAt = 0L
        )
        payCyclesFlow.value = listOf(cycle)
        whenever(holidayResolver.isHoliday(any())).thenReturn(false)

        val vm = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val cycles = vm.payCycles.value
        assertThat(cycles).hasSize(1)
        assertThat(cycles[0].cycle.id).isEqualTo(1L)
        assertThat(cycles[0].rule).isInstanceOf(PayCycleRule.DayOfMonthRule::class.java)
    }
}

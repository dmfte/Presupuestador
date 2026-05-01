package com.tuapp.fintrack.ui.entry

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.tuapp.fintrack.data.model.CategoryApplicability
import com.tuapp.fintrack.data.model.TransactionType
import com.tuapp.fintrack.data.repository.FinTrackRepository
import com.tuapp.fintrack.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class EntryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var repository: FinTrackRepository
    private lateinit var settings: SettingsRepository

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mock()
        settings = mock()
        whenever(repository.allCategories).thenReturn(flowOf(emptyList()))
        whenever(settings.requireCategory).thenReturn(flowOf(false))
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(transactionId: Long? = null): EntryViewModel {
        val handle = SavedStateHandle(
            if (transactionId != null) mapOf("transactionId" to transactionId) else emptyMap()
        )
        return EntryViewModel(repository, settings, handle)
    }

    @Test
    fun `amount zero produces error on save`() = runTest {
        val vm = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onSave()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(vm.uiState.value.amountError).isNotNull()
    }

    @Test
    fun `amount greater than zero clears error`() = runTest {
        val vm = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onAmountTextChanged("10.00")
        assertThat(vm.uiState.value.amountCents).isEqualTo(1000L)
        assertThat(vm.uiState.value.amountError).isNull()
    }

    @Test
    fun `require_category setting triggers category error when unset`() = runTest {
        val requireFlow = MutableStateFlow(true)
        whenever(settings.requireCategory).thenReturn(requireFlow)
        whenever(repository.addTransaction(any())).thenReturn(1L)

        val vm = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onAmountTextChanged("5.00")
        vm.onSave()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(vm.uiState.value.categoryError).isNotNull()
    }

    @Test
    fun `require_category off allows saving without category`() = runTest {
        whenever(settings.requireCategory).thenReturn(flowOf(false))
        whenever(repository.addTransaction(any())).thenReturn(1L)

        val vm = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onAmountTextChanged("5.00")
        vm.onSave()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(vm.uiState.value.categoryError).isNull()
    }

    @Test
    fun `form resets after successful save`() = runTest {
        whenever(repository.addTransaction(any())).thenReturn(1L)

        val vm = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onAmountTextChanged("20.00")
        vm.onDescriptionChanged("Coffee")
        vm.onSave()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(vm.uiState.value.amountText).isEmpty()
        assertThat(vm.uiState.value.description).isEmpty()
        assertThat(vm.uiState.value.savedEvent).isTrue()
    }

    @Test
    fun `parseToCents handles valid decimal input`() {
        assertThat(EntryViewModel.parseToCents("12.34")).isEqualTo(1234L)
        assertThat(EntryViewModel.parseToCents("0.99")).isEqualTo(99L)
        assertThat(EntryViewModel.parseToCents("100")).isEqualTo(10000L)
        assertThat(EntryViewModel.parseToCents("")).isEqualTo(0L)
        assertThat(EntryViewModel.parseToCents("abc")).isEqualTo(0L)
    }

    @Test
    fun `type change clears selected category`() = runTest {
        val vm = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onCategorySelected(42L)
        assertThat(vm.uiState.value.selectedCategoryId).isEqualTo(42L)

        vm.onTypeChanged(TransactionType.INCOME)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(vm.uiState.value.selectedCategoryId).isNull()
    }

    @Test
    fun `addCategory sets selectedCategoryId to new id`() = runTest {
        whenever(repository.addCategory(any())).thenReturn(99L)

        val vm = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.addCategory("Groceries", CategoryApplicability.EXPENSE, "#E53935")
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(vm.uiState.value.selectedCategoryId).isEqualTo(99L)
    }
}

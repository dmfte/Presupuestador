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
import com.tuapp.fintrack.data.model.Category
import com.tuapp.fintrack.data.model.Transaction
import com.tuapp.fintrack.domain.usecase.ReserveDeduction
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
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
        stubReserves(emptyList())
        whenever(settings.requireCategory).thenReturn(flowOf(false))
        whenever(settings.carryForwardEpoch).thenReturn(flowOf(0L))
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

    /** Stubs the SQL aggregate the ViewModel reads, computed from [txs] like Room would. */
    private fun stubReserves(txs: List<Transaction>) {
        whenever(repository.totalsByTypeSince(any())).thenAnswer { inv ->
            val since = inv.getArgument<Long>(0)
            flowOf(
                txs.filter { it.deletedAt == null && it.occurredAt >= since }
                    .groupBy { it.type }
                    .mapValues { (_, rows) -> rows.sumOf { it.amountCents } }
            )
        }
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
        assertThat(vm.uiState.value.savedEvent).isEqualTo(SavedEvent.CREATED)
    }

    @Test
    fun `parseToCents handles valid decimal input`() {
        assertThat(EntryViewModel.parseToCents("12.34")).isEqualTo(1234L)
        assertThat(EntryViewModel.parseToCents("0.99")).isEqualTo(99L)
        assertThat(EntryViewModel.parseToCents("100")).isEqualTo(10000L)
        assertThat(EntryViewModel.parseToCents("")).isEqualTo(0L)
        assertThat(EntryViewModel.parseToCents("abc")).isEqualTo(0L)
        assertThat(EntryViewModel.parseToCents("19.99")).isEqualTo(1999L)
        assertThat(EntryViewModel.parseToCents("12,50")).isEqualTo(1250L)
    }

    @Test
    fun `saving an edit keeps the transaction, preserves createdAt and emits UPDATED`() = runTest {
        val original = Transaction(
            id = 5L, type = TransactionType.INCOME, amountCents = 1_000L,
            occurredAt = 10_000L, createdAt = 123L
        )
        whenever(repository.getTransactionById(5L)).thenReturn(original)
        whenever(repository.allCategories).thenReturn(
            flowOf(listOf(Category(id = 1L, name = "Salary", applicability = CategoryApplicability.INCOME, colorHex = "#000000", createdAt = 0L)))
        )

        val vm = buildViewModel(transactionId = 5L)
        testDispatcher.scheduler.advanceUntilIdle()
        assertThat(vm.uiState.value.availableCategories.map { it.id }).containsExactly(1L)

        vm.onAmountTextChanged("25.00")
        vm.onSave()
        testDispatcher.scheduler.advanceUntilIdle()

        verify(repository).updateTransaction(original.copy(amountCents = 2_500L))
        assertThat(vm.uiState.value.transactionId).isEqualTo(5L)
        assertThat(vm.uiState.value.savedEvent).isEqualTo(SavedEvent.UPDATED)
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

    @Test
    fun `repository exception on save sets saveError and clears isSaving`() = runTest {
        whenever(repository.addTransaction(any())).thenThrow(RuntimeException("DB error"))

        val vm = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onAmountTextChanged("10.00")
        vm.onSave()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(vm.uiState.value.saveError).isNotNull()
        assertThat(vm.uiState.value.isSaving).isFalse()
        assertThat(vm.uiState.value.savedEvent).isNull()
    }

    @Test
    fun `clearSaveError removes error from state`() = runTest {
        whenever(repository.addTransaction(any())).thenThrow(RuntimeException("DB error"))

        val vm = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onAmountTextChanged("10.00")
        vm.onSave()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(vm.uiState.value.saveError).isNotNull()

        vm.clearSaveError()
        assertThat(vm.uiState.value.saveError).isNull()
    }

    private fun reserve(id: Long, cents: Long, at: Long = 1_000L) = Transaction(
        id = id, type = TransactionType.RESERVE, amountCents = cents, occurredAt = at, createdAt = at
    )

    @Test
    fun `new category stays selected after type change and category list refresh`() = runTest {
        val cats = MutableStateFlow(emptyList<Category>())
        whenever(repository.allCategories).thenReturn(cats)
        whenever(repository.addCategory(any())).thenReturn(7L)

        val vm = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onTypeChanged(TransactionType.INCOME)
        vm.onTypeChanged(TransactionType.EXPENSE)
        vm.addCategory("Food", CategoryApplicability.EXPENSE, "#E53935")
        testDispatcher.scheduler.advanceUntilIdle()

        cats.value = listOf(
            Category(id = 7L, name = "Food", applicability = CategoryApplicability.EXPENSE, colorHex = "#E53935", createdAt = 0L)
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(vm.uiState.value.selectedCategoryId).isEqualTo(7L)
        assertThat(vm.uiState.value.availableCategories.map { it.id }).containsExactly(7L)
    }

    @Test
    fun `deduct from reserved is only available for new expenses with amount and reserve`() = runTest {
        stubReserves(listOf(reserve(1L, 5_000L)))
        val vm = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(vm.uiState.value.reservedAvailableCents).isEqualTo(5_000L)
        assertThat(vm.uiState.value.canDeductFromReserved).isFalse() // no amount yet

        vm.onAmountTextChanged("10")
        assertThat(vm.uiState.value.canDeductFromReserved).isTrue()

        vm.onTypeChanged(TransactionType.INCOME)
        assertThat(vm.uiState.value.canDeductFromReserved).isFalse()
    }

    @Test
    fun `reserves before the carry-forward epoch are not available`() = runTest {
        whenever(settings.carryForwardEpoch).thenReturn(flowOf(2_000L))
        stubReserves(listOf(reserve(1L, 5_000L, at = 1_000L)))
        val vm = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onAmountTextChanged("10")
        assertThat(vm.uiState.value.reservedAvailableCents).isEqualTo(0L)
        assertThat(vm.uiState.value.canDeductFromReserved).isFalse()
    }

    @Test
    fun `checked deduct from reserved saves through addExpenseFromReserves`() = runTest {
        stubReserves(listOf(reserve(1L, 5_000L)))
        whenever(repository.addExpenseFromReserves(any(), any()))
            .thenReturn(ReserveDeduction.Plan(listOf(1L), null, 1_000L, 0L))
        val vm = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onAmountTextChanged("10")
        vm.onDeductFromReservedChanged(true)
        vm.onSave()
        testDispatcher.scheduler.advanceUntilIdle()

        verify(repository).addExpenseFromReserves(any(), eq(0L))
        verify(repository, never()).addTransaction(any())
        assertThat(vm.uiState.value.deductFromReserved).isFalse()
    }
}

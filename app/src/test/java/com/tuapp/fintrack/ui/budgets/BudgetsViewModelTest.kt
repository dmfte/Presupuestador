package com.tuapp.fintrack.ui.budgets

import com.google.common.truth.Truth.assertThat
import com.tuapp.fintrack.data.model.Budget
import com.tuapp.fintrack.data.model.Category
import com.tuapp.fintrack.data.model.CategoryApplicability
import com.tuapp.fintrack.data.model.PayCycle
import com.tuapp.fintrack.data.model.Transaction
import com.tuapp.fintrack.data.model.TransactionType
import com.tuapp.fintrack.data.repository.FinTrackRepository
import com.tuapp.fintrack.domain.model.PayPeriod
import com.tuapp.fintrack.domain.usecase.GetCurrentPeriodUseCase
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
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FinTrackRepository
    private lateinit var getCurrentPeriod: GetCurrentPeriodUseCase

    private val budgetsFlow = MutableStateFlow<List<Budget>>(emptyList())
    private val categoriesFlow = MutableStateFlow<List<Category>>(emptyList())
    private val cyclesFlow = MutableStateFlow<List<PayCycle>>(emptyList())
    private val transactionsFlow = MutableStateFlow<List<Transaction>>(emptyList())

    private val now = System.currentTimeMillis()
    private val fakePeriod = PayPeriod(
        startDateMs = now - 86_400_000L,
        endDateMs = now + 86_400_000L,
        nextPaydayMs = now + 86_400_000L,
        daysRemaining = 1
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mock()
        getCurrentPeriod = mock()
        whenever(repository.allBudgets).thenReturn(budgetsFlow)
        whenever(repository.allCategories).thenReturn(categoriesFlow)
        whenever(repository.allPayCycles).thenReturn(cyclesFlow)
        whenever(repository.allTransactions).thenReturn(transactionsFlow)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private suspend fun buildViewModel(): BudgetsViewModel {
        whenever(getCurrentPeriod(any())).thenReturn(fakePeriod)
        return BudgetsViewModel(repository, getCurrentPeriod)
    }

    @Test
    fun `onAddBudget with valid inputs sets snackbar`() = runTest {
        whenever(repository.hasDuplicateBudget(any(), any())).thenReturn(false)
        whenever(repository.addBudget(any())).thenReturn(1L)

        val cat = Category(id = 1L, name = "Food", applicability = CategoryApplicability.EXPENSE,
            colorHex = "#FF5722", createdAt = 0L)
        categoriesFlow.value = listOf(cat)

        val vm = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onAddBudget(1L, 1L, 10000L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(vm.uiState.value.snackbarMessage).contains("Budget created")
    }

    @Test
    fun `onAddBudget rejects duplicate`() = runTest {
        whenever(repository.hasDuplicateBudget(any(), any())).thenReturn(true)

        val vm = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onAddBudget(1L, 1L, 10000L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(vm.uiState.value.snackbarMessage).contains("already exists")
    }

    @Test
    fun `onDeleteBudget sets snackbar with restore id`() = runTest {
        whenever(repository.deactivateBudget(any())).then {}

        val vm = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onDeleteBudget(7L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(vm.uiState.value.pendingRestoreId).isEqualTo(7L)
        assertThat(vm.uiState.value.snackbarMessage).contains("deleted")
    }

    @Test
    fun `onEditBudget updates amount`() = runTest {
        val budget = Budget(id = 1L, categoryId = 1L, amountCents = 5000L, cycleId = 1L, createdAt = 0L)
        whenever(repository.getBudgetById(1L)).thenReturn(budget)
        whenever(repository.updateBudget(any())).then {}

        val vm = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onEditBudget(1L, 9999L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(vm.uiState.value.snackbarMessage).isEqualTo("Budget updated")
    }

    @Test
    fun `progress computed correctly for spending in period`() = runTest {
        val cat = Category(id = 1L, name = "Food", applicability = CategoryApplicability.EXPENSE,
            colorHex = "#FF5722", createdAt = 0L)
        val cycle = PayCycle(id = 1L, rule = "DOM:15", createdAt = 0L)
        val budget = Budget(id = 1L, categoryId = 1L, amountCents = 10000L, cycleId = 1L, createdAt = 0L)
        val tx = Transaction(
            id = 1L, type = TransactionType.EXPENSE, amountCents = 7500L,
            categoryId = 1L, occurredAt = now, createdAt = now
        )

        categoriesFlow.value = listOf(cat)
        cyclesFlow.value = listOf(cycle)
        budgetsFlow.value = listOf(budget)
        transactionsFlow.value = listOf(tx)

        val vm = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val bwpList = vm.budgets.value
        assertThat(bwpList).hasSize(1)
        assertThat(bwpList[0].progressPercent).isEqualTo(75)
        assertThat(bwpList[0].currentPeriodSpendingCents).isEqualTo(7500L)
    }

    @Test
    fun `progress over 100 when spending exceeds budget`() = runTest {
        val cat = Category(id = 1L, name = "Food", applicability = CategoryApplicability.EXPENSE,
            colorHex = "#FF5722", createdAt = 0L)
        val cycle = PayCycle(id = 1L, rule = "DOM:15", createdAt = 0L)
        val budget = Budget(id = 1L, categoryId = 1L, amountCents = 5000L, cycleId = 1L, createdAt = 0L)
        val tx = Transaction(
            id = 1L, type = TransactionType.EXPENSE, amountCents = 7000L,
            categoryId = 1L, occurredAt = now, createdAt = now
        )

        categoriesFlow.value = listOf(cat)
        cyclesFlow.value = listOf(cycle)
        budgetsFlow.value = listOf(budget)
        transactionsFlow.value = listOf(tx)

        val vm = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val bwpList = vm.budgets.value
        assertThat(bwpList[0].progressPercent).isGreaterThan(100)
    }

    @Test
    fun `budget without matching category does not appear`() = runTest {
        val cycle = PayCycle(id = 1L, rule = "DOM:15", createdAt = 0L)
        val budget = Budget(id = 1L, categoryId = 99L, amountCents = 5000L, cycleId = 1L, createdAt = 0L)

        categoriesFlow.value = emptyList()
        cyclesFlow.value = listOf(cycle)
        budgetsFlow.value = listOf(budget)

        val vm = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(vm.budgets.value).isEmpty()
    }
}

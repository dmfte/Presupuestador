package com.tuapp.fintrack.ui.list

import com.google.common.truth.Truth.assertThat
import com.tuapp.fintrack.data.model.Transaction
import com.tuapp.fintrack.data.model.TransactionType
import com.tuapp.fintrack.data.repository.FinTrackRepository
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FinTrackRepository

    private val now = System.currentTimeMillis()

    private val txIncome = tx(1L, TransactionType.INCOME, categoryId = 10L, desc = "Salary", ts = now)
    private val txExpense1 = tx(2L, TransactionType.EXPENSE, categoryId = 20L, desc = "Coffee shop", ts = now)
    private val txExpense2 = tx(3L, TransactionType.EXPENSE, categoryId = 10L, desc = "Groceries", ts = now)

    private val txsFlow = MutableStateFlow(listOf(txIncome, txExpense1, txExpense2))

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mock()
        whenever(repository.allTransactions).thenReturn(txsFlow)
        whenever(repository.allCategories).thenReturn(flowOf(emptyList()))
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun buildVm() = TransactionListViewModel(repository)

    @Test
    fun `initial state shows all transactions`() = runTest {
        val vm = buildVm()
        testDispatcher.scheduler.advanceUntilIdle()
        assertThat(vm.uiState.value.transactions).hasSize(3)
    }

    @Test
    fun `filter by INCOME shows only income transactions`() = runTest {
        val vm = buildVm()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onTypeFilterChanged(TransactionType.INCOME)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(vm.uiState.value.transactions).hasSize(1)
        assertThat(vm.uiState.value.transactions.first().type).isEqualTo(TransactionType.INCOME)
    }

    @Test
    fun `filter by EXPENSE shows only expense transactions`() = runTest {
        val vm = buildVm()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onTypeFilterChanged(TransactionType.EXPENSE)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(vm.uiState.value.transactions).hasSize(2)
        assertThat(vm.uiState.value.transactions.all { it.type == TransactionType.EXPENSE }).isTrue()
    }

    @Test
    fun `filter by null type shows all`() = runTest {
        val vm = buildVm()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onTypeFilterChanged(TransactionType.EXPENSE)
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onTypeFilterChanged(null)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(vm.uiState.value.transactions).hasSize(3)
    }

    @Test
    fun `filter by category 10 returns income and expense2`() = runTest {
        val vm = buildVm()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onCategoryFilterChanged(10L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(vm.uiState.value.transactions).hasSize(2)
        assertThat(vm.uiState.value.transactions.all { it.categoryId == 10L }).isTrue()
    }

    @Test
    fun `search by description is case insensitive`() = runTest {
        val vm = buildVm()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onSearchChanged("coffee")
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(vm.uiState.value.transactions).hasSize(1)
        assertThat(vm.uiState.value.transactions.first().description).isEqualTo("Coffee shop")
    }

    @Test
    fun `search partial match works`() = runTest {
        val vm = buildVm()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onSearchChanged("gro")
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(vm.uiState.value.transactions).hasSize(1)
        assertThat(vm.uiState.value.transactions.first().description).isEqualTo("Groceries")
    }

    @Test
    fun `combined type and search filter`() = runTest {
        val vm = buildVm()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onTypeFilterChanged(TransactionType.EXPENSE)
        vm.onSearchChanged("cof")
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(vm.uiState.value.transactions).hasSize(1)
    }

    @Test
    fun `this month preset includes current month transactions`() = runTest {
        val vm = buildVm()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onDatePresetChanged(DateRangePreset.THIS_MONTH)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(vm.uiState.value.transactions).hasSize(3)
    }

    @Test
    fun `last month preset excludes current month transactions`() = runTest {
        val vm = buildVm()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onDatePresetChanged(DateRangePreset.LAST_MONTH)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(vm.uiState.value.transactions).isEmpty()
    }

    @Test
    fun `all preset shows everything`() = runTest {
        val vm = buildVm()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onDatePresetChanged(DateRangePreset.LAST_MONTH)
        testDispatcher.scheduler.advanceUntilIdle()
        vm.onDatePresetChanged(DateRangePreset.ALL)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(vm.uiState.value.transactions).hasSize(3)
    }

    private fun tx(
        id: Long,
        type: TransactionType,
        categoryId: Long? = null,
        desc: String = "",
        ts: Long = System.currentTimeMillis()
    ) = Transaction(
        id = id,
        type = type,
        amountCents = 100L,
        categoryId = categoryId,
        description = desc,
        occurredAt = ts,
        createdAt = ts
    )
}

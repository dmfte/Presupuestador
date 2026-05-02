package com.tuapp.fintrack.ui.categories

import com.google.common.truth.Truth.assertThat
import com.tuapp.fintrack.data.model.Budget
import com.tuapp.fintrack.data.model.Category
import com.tuapp.fintrack.data.model.CategoryApplicability
import com.tuapp.fintrack.data.repository.FinTrackRepository
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
class CategoriesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var repository: FinTrackRepository

    private val categoriesFlow = MutableStateFlow<List<Category>>(emptyList())

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mock()
        whenever(repository.allCategories).thenReturn(categoriesFlow)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() = CategoriesViewModel(repository)

    @Test
    fun `onAddCategory inserts and sets snackbar message`() = runTest {
        whenever(repository.addCategory(any())).thenReturn(1L)
        val vm = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onAddCategory("Food", CategoryApplicability.EXPENSE)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(vm.uiState.value.snackbarMessage).contains("Food")
    }

    @Test
    fun `onEditCategory updates and sets snackbar message`() = runTest {
        val existing = Category(
            id = 1L, name = "Old", applicability = CategoryApplicability.EXPENSE,
            colorHex = "#FF5722", createdAt = 0L
        )
        categoriesFlow.value = listOf(existing)
        whenever(repository.updateCategory(any())).then {}

        val vm = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onEditCategory(1L, "New", CategoryApplicability.INCOME, "#2196F3")
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(vm.uiState.value.snackbarMessage).isEqualTo("Category updated")
    }

    @Test
    fun `onArchiveCategory sets snackbar with undo id`() = runTest {
        whenever(repository.archiveCategory(any())).then {}
        val vm = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        vm.onArchiveCategory(5L)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(vm.uiState.value.snackbarMessage).contains("archived")
        assertThat(vm.uiState.value.pendingRestoreId).isEqualTo(5L)
    }

    @Test
    fun `isDuplicateName returns true when name already exists`() = runTest {
        val cat = Category(
            id = 1L, name = "Groceries", applicability = CategoryApplicability.EXPENSE,
            colorHex = "#FF5722", createdAt = 0L
        )
        categoriesFlow.value = listOf(cat)
        val vm = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(vm.isDuplicateName("Groceries")).isTrue()
        assertThat(vm.isDuplicateName("groceries")).isTrue()
        assertThat(vm.isDuplicateName("Food")).isFalse()
    }

    @Test
    fun `isDuplicateName excludes self when editing`() = runTest {
        val cat = Category(
            id = 1L, name = "Groceries", applicability = CategoryApplicability.EXPENSE,
            colorHex = "#FF5722", createdAt = 0L
        )
        categoriesFlow.value = listOf(cat)
        val vm = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(vm.isDuplicateName("Groceries", excludeId = 1L)).isFalse()
        assertThat(vm.isDuplicateName("Groceries", excludeId = 99L)).isTrue()
    }

    @Test
    fun `hasBudgetsForCategory returns true when active budget exists`() = runTest {
        val vm = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val budget = Budget(id = 1L, categoryId = 3L, amountCents = 5000L, cycleId = 1L, createdAt = 0L)
        assertThat(vm.hasBudgetsForCategory(3L, listOf(budget))).isTrue()
        assertThat(vm.hasBudgetsForCategory(99L, listOf(budget))).isFalse()
    }

    @Test
    fun `color palette cycles through 12 colors`() = runTest {
        whenever(repository.addCategory(any())).thenReturn(1L)
        val vm = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(CategoriesViewModel.CATEGORY_COLORS).hasSize(12)
        val color0 = CategoriesViewModel.CATEGORY_COLORS[0]
        val color12 = CategoriesViewModel.CATEGORY_COLORS[12 % 12]
        assertThat(color0).isEqualTo(color12)
    }
}

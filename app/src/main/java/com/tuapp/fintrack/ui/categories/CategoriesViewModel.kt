package com.tuapp.fintrack.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tuapp.fintrack.data.model.Category
import com.tuapp.fintrack.data.model.CategoryApplicability
import com.tuapp.fintrack.data.repository.FinTrackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategoriesUiState(
    val snackbarMessage: String? = null,
    val pendingRestoreId: Long? = null
)

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val repository: FinTrackRepository
) : ViewModel() {

    val categories: StateFlow<List<Category>> = repository.allCategories
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _uiState = MutableStateFlow(CategoriesUiState())
    val uiState: StateFlow<CategoriesUiState> = _uiState.asStateFlow()

    fun onAddCategory(name: String, applicability: CategoryApplicability) {
        viewModelScope.launch {
            val existingCount = categories.value.size
            val color = CATEGORY_COLORS[existingCount % CATEGORY_COLORS.size]
            repository.addCategory(
                Category(
                    name = name.trim(),
                    applicability = applicability,
                    colorHex = color,
                    createdAt = System.currentTimeMillis()
                )
            )
            _uiState.update { it.copy(snackbarMessage = "Category added: ${name.trim()}") }
        }
    }

    fun onEditCategory(id: Long, name: String, applicability: CategoryApplicability, colorHex: String) {
        viewModelScope.launch {
            val existing = categories.value.find { it.id == id } ?: return@launch
            repository.updateCategory(
                existing.copy(name = name.trim(), applicability = applicability, colorHex = colorHex)
            )
            _uiState.update { it.copy(snackbarMessage = "Category updated") }
        }
    }

    fun onArchiveCategory(id: Long) {
        viewModelScope.launch {
            repository.archiveCategory(id)
            _uiState.update { it.copy(snackbarMessage = "Category archived", pendingRestoreId = id) }
        }
    }

    fun onUndoArchive(id: Long) {
        viewModelScope.launch {
            val existing = categories.value.find { it.id == id }
                ?: repository.getCategoryById(id)
                ?: return@launch
            repository.updateCategory(existing.copy(archivedAt = null))
            _uiState.update { it.copy(snackbarMessage = null, pendingRestoreId = null) }
        }
    }

    fun isDuplicateName(name: String, excludeId: Long? = null): Boolean {
        return categories.value.any {
            it.name.equals(name.trim(), ignoreCase = true) && it.id != (excludeId ?: -1L)
        }
    }

    fun hasBudgetsForCategory(id: Long, activeBudgets: List<com.tuapp.fintrack.data.model.Budget>): Boolean {
        return activeBudgets.any { it.categoryId == id }
    }

    fun onSnackbarConsumed() {
        _uiState.update { it.copy(snackbarMessage = null, pendingRestoreId = null) }
    }

    companion object {
        val CATEGORY_COLORS = listOf(
            "#FF5722",
            "#FF9800",
            "#FFC107",
            "#8BC34A",
            "#00BCD4",
            "#2196F3",
            "#9C27B0",
            "#E91E63",
            "#795548",
            "#9E9E9E",
            "#009688",
            "#3F51B5"
        )
    }
}

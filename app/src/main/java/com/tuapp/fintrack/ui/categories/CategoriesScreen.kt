package com.tuapp.fintrack.ui.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tuapp.fintrack.data.model.Budget
import com.tuapp.fintrack.data.model.Category
import com.tuapp.fintrack.data.model.CategoryApplicability
import com.tuapp.fintrack.ui.common.CategoryDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    onNavigateBack: () -> Unit,
    activeBudgets: List<Budget> = emptyList(),
    viewModel: CategoriesViewModel = hiltViewModel()
) {
    val categories by viewModel.categories.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showAddDialog by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Category?>(null) }
    var archiveTarget by remember { mutableStateOf<Category?>(null) }

    LaunchedEffect(uiState.snackbarMessage) {
        val message = uiState.snackbarMessage ?: return@LaunchedEffect
        val pendingId = uiState.pendingRestoreId
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = if (pendingId != null) "Undo" else null,
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed && pendingId != null) {
            viewModel.onUndoArchive(pendingId)
        } else {
            viewModel.onSnackbarConsumed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categories") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add category")
            }
        }
    ) { innerPadding ->
        if (categories.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No categories yet. Tap + to add one.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { Spacer(Modifier) }
                items(categories, key = { it.id }) { category ->
                    CategoryItem(
                        category = category,
                        onTap = { editTarget = category },
                        onLongPress = { archiveTarget = category }
                    )
                }
                item { Spacer(Modifier) }
            }
        }
    }

    if (showAddDialog) {
        CategoryDialog(
            title = "New Category",
            confirmLabel = "Add",
            existingCount = categories.size,
            onConfirm = { name, applicability, _ ->
                if (viewModel.isDuplicateName(name)) {
                    false
                } else {
                    viewModel.onAddCategory(name, applicability)
                    true
                }
            },
            onDismiss = { showAddDialog = false }
        )
    }

    editTarget?.let { cat ->
        CategoryDialog(
            title = "Edit Category",
            confirmLabel = "Save",
            initialName = cat.name,
            initialApplicability = cat.applicability,
            initialColor = cat.colorHex,
            existingCount = categories.size,
            onConfirm = { name, applicability, color ->
                if (viewModel.isDuplicateName(name, excludeId = cat.id)) {
                    false
                } else {
                    viewModel.onEditCategory(cat.id, name, applicability, color)
                    true
                }
            },
            onDismiss = { editTarget = null }
        )
    }

    archiveTarget?.let { cat ->
        val hasBudgets = viewModel.hasBudgetsForCategory(cat.id, activeBudgets)
        if (hasBudgets) {
            AlertDialog(
                onDismissRequest = { archiveTarget = null },
                title = { Text("Cannot Archive") },
                text = { Text("This category has active budgets. Remove the budgets first before archiving.") },
                confirmButton = {
                    TextButton(onClick = { archiveTarget = null }) { Text("OK") }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { archiveTarget = null },
                title = { Text("Archive Category?") },
                text = { Text("Archived categories are hidden from entry forms but remain in historical data.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.onArchiveCategory(cat.id)
                        archiveTarget = null
                    }) { Text("Archive") }
                },
                dismissButton = {
                    TextButton(onClick = { archiveTarget = null }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
private fun CategoryItem(
    category: Category,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val colorInt = remember(category.colorHex) {
        try {
            android.graphics.Color.parseColor(category.colorHex)
        } catch (e: Exception) {
            android.graphics.Color.GRAY
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onLongPress = { onLongPress() }
                )
            }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(Color(colorInt), CircleShape)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = category.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        SuggestionChip(
            onClick = {},
            label = {
                Text(
                    text = when (category.applicability) {
                        CategoryApplicability.EXPENSE -> "Expense"
                        CategoryApplicability.INCOME -> "Income"
                        CategoryApplicability.BOTH -> "Both"
                    },
                    style = MaterialTheme.typography.labelSmall
                )
            }
        )
    }
}

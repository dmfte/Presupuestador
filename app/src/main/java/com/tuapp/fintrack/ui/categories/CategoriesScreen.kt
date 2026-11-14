package com.tuapp.fintrack.ui.categories

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tuapp.fintrack.data.model.Category
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tuapp.fintrack.R
import com.tuapp.fintrack.ui.common.CategoryDialog
import com.tuapp.fintrack.ui.common.labelRes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    onNavigateBack: () -> Unit,
    viewModel: CategoriesViewModel = hiltViewModel()
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val activeBudgets by viewModel.activeBudgets.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarText = uiState.snackbarMessage?.resolve()
    val undoLabel = stringResource(R.string.undo)

    var showAddDialog by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Category?>(null) }
    var archiveTarget by remember { mutableStateOf<Category?>(null) }

    LaunchedEffect(uiState.snackbarMessage) {
        val message = snackbarText ?: return@LaunchedEffect
        val pendingId = uiState.pendingRestoreId
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = if (pendingId != null) undoLabel else null,
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
                title = { Text(stringResource(R.string.nav_categories)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.category_new_title))
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
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Label,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Text(
                        text = stringResource(R.string.category_empty_title),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.category_empty_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Button(onClick = { showAddDialog = true }) {
                        Text(stringResource(R.string.category_create))
                    }
                }
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
            title = stringResource(R.string.category_new_title),
            confirmLabel = stringResource(R.string.action_add),
            existingCount = categories.size,
            onConfirm = { name, applicability, color ->
                if (viewModel.isDuplicateName(name)) {
                    false
                } else {
                    viewModel.onAddCategory(name, applicability, color)
                    true
                }
            },
            onDismiss = { showAddDialog = false }
        )
    }

    editTarget?.let { cat ->
        CategoryDialog(
            title = stringResource(R.string.category_edit_title),
            confirmLabel = stringResource(R.string.action_save),
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
                title = { Text(stringResource(R.string.category_cannot_archive_title)) },
                text = { Text(stringResource(R.string.category_cannot_archive_message)) },
                confirmButton = {
                    TextButton(onClick = { archiveTarget = null }) { Text(stringResource(R.string.action_ok)) }
                }
            )
        } else {
            AlertDialog(
                onDismissRequest = { archiveTarget = null },
                title = { Text(stringResource(R.string.category_archive_title)) },
                text = { Text(stringResource(R.string.category_archive_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.onArchiveCategory(cat.id)
                        archiveTarget = null
                    }) { Text(stringResource(R.string.action_archive)) }
                },
                dismissButton = {
                    TextButton(onClick = { archiveTarget = null }) { Text(stringResource(R.string.action_cancel)) }
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
            .combinedClickable(
                onClickLabel = stringResource(R.string.action_edit),
                onLongClickLabel = stringResource(R.string.action_archive),
                onClick = onTap,
                onLongClick = onLongPress
            )
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
        // Informational label only; a SuggestionChip with a no-op onClick was announced as a button.
        Text(
            text = stringResource(category.applicability.labelRes()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

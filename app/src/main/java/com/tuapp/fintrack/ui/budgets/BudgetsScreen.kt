package com.tuapp.fintrack.ui.budgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tuapp.fintrack.R
import com.tuapp.fintrack.ui.theme.LocalFinColors
import com.tuapp.fintrack.data.model.Category
import com.tuapp.fintrack.domain.model.BudgetWithProgress
import com.tuapp.fintrack.domain.util.Money
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(
    onNavigateBack: () -> Unit,
    viewModel: BudgetsViewModel = hiltViewModel()
) {
    val budgets by viewModel.budgets.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarText = uiState.snackbarMessage?.resolve()
    val undoLabel = stringResource(R.string.undo)

    var showAddDialog by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<BudgetWithProgress?>(null) }
    var deleteTarget by remember { mutableStateOf<BudgetWithProgress?>(null) }

    LaunchedEffect(uiState.snackbarMessage) {
        val message = snackbarText ?: return@LaunchedEffect
        val pendingId = uiState.pendingRestoreId
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = if (pendingId != null) undoLabel else null,
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed && pendingId != null) {
            viewModel.onUndoDelete(pendingId)
        } else {
            viewModel.onSnackbarConsumed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_budgets)) },
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
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.budget_add))
            }
        }
    ) { innerPadding ->
        if (budgets.isEmpty()) {
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
                        imageVector = Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Text(
                        text = stringResource(R.string.budget_empty_title),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.budget_empty_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Button(onClick = { showAddDialog = true }) {
                        Text(stringResource(R.string.budget_create))
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Spacer(Modifier) }
                items(budgets, key = { it.budget.id }) { bwp ->
                    BudgetItem(
                        bwp = bwp,
                        onTap = { editTarget = bwp },
                        onLongPress = { deleteTarget = bwp }
                    )
                }
                item { Spacer(Modifier) }
            }
        }
    }

    if (showAddDialog) {
        AddBudgetDialog(
            categories = categories.filter { it.archivedAt == null },
            onConfirm = { catId, cents ->
                viewModel.onAddBudget(catId, cents)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }

    editTarget?.let { bwp ->
        EditBudgetDialog(
            current = bwp,
            onConfirm = { cents ->
                viewModel.onEditBudget(bwp.budget.id, cents)
                editTarget = null
            },
            onDismiss = { editTarget = null }
        )
    }

    deleteTarget?.let { bwp ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.budget_delete_title)) },
            text = { Text(stringResource(R.string.budget_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onDeleteBudget(bwp.budget.id)
                    deleteTarget = null
                }) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

@Composable
private fun BudgetItem(
    bwp: BudgetWithProgress,
    onTap: () -> Unit,
    onLongPress: () -> Unit
) {
    val currency = remember { NumberFormat.getCurrencyInstance(Locale.US) }
    val fin = LocalFinColors.current
    val colorInt = remember(bwp.category.colorHex) {
        try { android.graphics.Color.parseColor(bwp.category.colorHex) }
        catch (e: Exception) { android.graphics.Color.GRAY }
    }
    val progress = (bwp.progressPercent / 100f).coerceIn(0f, 1f)
    val progressColor = when {
        bwp.progressPercent >= 100 -> fin.expense
        bwp.progressPercent >= 75 -> fin.warning
        else -> fin.income
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClickLabel = stringResource(R.string.action_edit),
                onLongClickLabel = stringResource(R.string.action_delete),
                onClick = onTap,
                onLongClick = onLongPress
            )
            .padding(vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(Color(colorInt), CircleShape)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = bwp.category.name,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = currency.format(bwp.budget.amountCents / 100.0),
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.budget_monthly),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
            color = progressColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.budget_spent_progress, currency.format(bwp.currentPeriodSpendingCents / 100.0), bwp.progressPercent),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddBudgetDialog(
    categories: List<Category>,
    onConfirm: (categoryId: Long, amountCents: Long) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var amountText by remember { mutableStateOf("") }
    var amountError by remember { mutableStateOf<Int?>(null) }
    var catExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.budget_add)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExposedDropdownMenuBox(
                    expanded = catExpanded,
                    onExpandedChange = { catExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedCategory?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.category_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(catExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = catExpanded,
                        onDismissRequest = { catExpanded = false }
                    ) {
                        if (categories.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.budget_no_categories), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                onClick = {}
                            )
                        } else {
                            categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat.name) },
                                    onClick = { selectedCategory = cat; catExpanded = false }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; amountError = null },
                    label = { Text(stringResource(R.string.budget_amount_label)) },
                    placeholder = { Text("0.00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = amountError != null,
                    supportingText = amountError?.let { { Text(stringResource(it), color = MaterialTheme.colorScheme.error) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cents = parseCents(amountText)
                if (cents <= 0L) { amountError = R.string.budget_invalid_amount; return@TextButton }
                if (selectedCategory == null) return@TextButton
                onConfirm(selectedCategory!!.id, cents)
            }) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun EditBudgetDialog(
    current: BudgetWithProgress,
    onConfirm: (amountCents: Long) -> Unit,
    onDismiss: () -> Unit
) {
    val initial = remember { Money.centsToPlain(current.budget.amountCents) }
    var amountText by remember { mutableStateOf(initial) }
    var amountError by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.budget_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.budget_category_monthly, current.category.name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; amountError = null },
                    label = { Text(stringResource(R.string.budget_amount_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = amountError != null,
                    supportingText = amountError?.let { { Text(stringResource(it), color = MaterialTheme.colorScheme.error) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cents = parseCents(amountText)
                if (cents <= 0L) { amountError = R.string.budget_invalid_amount; return@TextButton }
                onConfirm(cents)
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

private fun parseCents(text: String): Long = Money.parseToCentsOrNull(text) ?: 0L

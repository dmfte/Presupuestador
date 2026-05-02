package com.tuapp.fintrack.ui.budgets

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tuapp.fintrack.data.model.Category
import com.tuapp.fintrack.data.model.CategoryApplicability
import com.tuapp.fintrack.data.model.PayCycle
import com.tuapp.fintrack.domain.model.BudgetWithProgress
import com.tuapp.fintrack.domain.model.PayCycleRule
import com.tuapp.fintrack.domain.model.toPayCycleRule
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(
    onNavigateBack: () -> Unit,
    viewModel: BudgetsViewModel = hiltViewModel()
) {
    val budgets by viewModel.budgets.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val cycles by viewModel.payCycles.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showAddDialog by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<BudgetWithProgress?>(null) }
    var deleteTarget by remember { mutableStateOf<BudgetWithProgress?>(null) }

    LaunchedEffect(uiState.snackbarMessage) {
        val message = uiState.snackbarMessage ?: return@LaunchedEffect
        val pendingId = uiState.pendingRestoreId
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = if (pendingId != null) "Undo" else null,
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
                title = { Text("Budgets") },
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
                Icon(Icons.Default.Add, contentDescription = "Add budget")
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
                Text(
                    text = "No budgets yet. Tap + to add one.",
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
            cycles = cycles,
            onConfirm = { catId, cycleId, cents ->
                viewModel.onAddBudget(catId, cycleId, cents)
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
            title = { Text("Delete Budget?") },
            text = { Text("This will remove the budget; category will have no limit.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onDeleteBudget(bwp.budget.id)
                    deleteTarget = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
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
    val currency = NumberFormat.getCurrencyInstance(Locale.US)
    val colorInt = remember(bwp.category.colorHex) {
        try { android.graphics.Color.parseColor(bwp.category.colorHex) }
        catch (e: Exception) { android.graphics.Color.GRAY }
    }
    val progress = (bwp.progressPercent / 100f).coerceIn(0f, 1f)
    val progressColor = when {
        bwp.progressPercent >= 100 -> Color(0xFFC62828)
        bwp.progressPercent >= 75 -> Color(0xFFE65100)
        else -> Color(0xFF2E7D32)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onTap() }, onLongPress = { onLongPress() })
            }
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
            text = cycleLabel(bwp.cycle),
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
            text = "${currency.format(bwp.currentPeriodSpendingCents / 100.0)} spent (${bwp.progressPercent}%)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun cycleLabel(cycle: PayCycle): String {
    return try {
        when (val rule = cycle.rule.toPayCycleRule()) {
            is PayCycleRule.DayOfMonthRule -> "${rule.day}th of month"
            is PayCycleRule.LastDayOfMonthRule -> "Last day of month"
            is PayCycleRule.SpecificDateRule -> "Specific date"
            is PayCycleRule.WeeklyRule -> "Weekly"
            is PayCycleRule.BiweeklyRule -> "Biweekly"
        }
    } catch (e: Exception) {
        cycle.rule
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddBudgetDialog(
    categories: List<Category>,
    cycles: List<PayCycle>,
    onConfirm: (categoryId: Long, cycleId: Long, amountCents: Long) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var selectedCycle by remember { mutableStateOf<PayCycle?>(null) }
    var amountText by remember { mutableStateOf("") }
    var amountError by remember { mutableStateOf<String?>(null) }
    var catExpanded by remember { mutableStateOf(false) }
    var cycleExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Budget") },
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
                        label = { Text("Category") },
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
                                text = { Text("No categories available", color = MaterialTheme.colorScheme.onSurfaceVariant) },
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

                ExposedDropdownMenuBox(
                    expanded = cycleExpanded,
                    onExpandedChange = { cycleExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedCycle?.let { cycleLabel(it) } ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Pay Cycle") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(cycleExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = cycleExpanded,
                        onDismissRequest = { cycleExpanded = false }
                    ) {
                        if (cycles.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No cycles available", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                onClick = {}
                            )
                        } else {
                            cycles.forEach { cycle ->
                                DropdownMenuItem(
                                    text = { Text(cycleLabel(cycle)) },
                                    onClick = { selectedCycle = cycle; cycleExpanded = false }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; amountError = null },
                    label = { Text("Budget Amount (USD)") },
                    placeholder = { Text("0.00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = amountError != null,
                    supportingText = amountError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cents = parseCents(amountText)
                if (cents <= 0L) { amountError = "Enter a valid amount"; return@TextButton }
                if (selectedCategory == null) return@TextButton
                if (selectedCycle == null) return@TextButton
                onConfirm(selectedCategory!!.id, selectedCycle!!.id, cents)
            }) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun EditBudgetDialog(
    current: BudgetWithProgress,
    onConfirm: (amountCents: Long) -> Unit,
    onDismiss: () -> Unit
) {
    val initial = remember {
        val d = current.budget.amountCents / 100.0
        "%.2f".format(d)
    }
    var amountText by remember { mutableStateOf(initial) }
    var amountError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Budget") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${current.category.name} · ${cycleLabel(current.cycle)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; amountError = null },
                    label = { Text("Budget Amount (USD)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = amountError != null,
                    supportingText = amountError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cents = parseCents(amountText)
                if (cents <= 0L) { amountError = "Enter a valid amount"; return@TextButton }
                onConfirm(cents)
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun parseCents(text: String): Long {
    val d = text.trim().toDoubleOrNull() ?: return 0L
    return (d * 100).toLong()
}

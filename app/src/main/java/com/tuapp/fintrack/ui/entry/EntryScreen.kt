package com.tuapp.fintrack.ui.entry

import com.tuapp.fintrack.domain.util.Dates
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.annotation.StringRes
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tuapp.fintrack.R
import com.tuapp.fintrack.ui.common.CategoryDialog
import com.tuapp.fintrack.ui.common.labelRes
import com.tuapp.fintrack.ui.theme.LocalFinColors
import com.tuapp.fintrack.data.model.CategoryApplicability
import com.tuapp.fintrack.domain.usecase.ReserveDeduction
import java.text.NumberFormat
import com.tuapp.fintrack.data.model.TransactionType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryScreen(
    onNavigateBack: () -> Unit,
    viewModel: EntryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val isEditing = state.transactionId != null
    val savedMsg = stringResource(R.string.transaction_saved)
    val saveErrorMsg = state.saveError?.let { stringResource(it) }

    LaunchedEffect(state.savedEvent) {
        when (state.savedEvent) {
            SavedEvent.UPDATED -> {
                viewModel.onSavedEventConsumed()
                onNavigateBack()
            }
            SavedEvent.CREATED -> {
                viewModel.onSavedEventConsumed()
                snackbarHostState.showSnackbar(savedMsg)
            }
            null -> Unit
        }
    }

    LaunchedEffect(saveErrorMsg) {
        saveErrorMsg?.let { error ->
            viewModel.clearSaveError()
            snackbarHostState.showSnackbar(error)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (isEditing) R.string.edit_transaction else R.string.add_transaction)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            TypeToggle(
                selected = state.type,
                onSelect = viewModel::onTypeChanged
            )

            if (!isEditing) {
                DeductFromReservedRow(
                    checked = state.willDeductFromReserved,
                    enabled = state.canDeductFromReserved,
                    reservedCents = state.reservedAvailableCents,
                    amountCents = state.amountCents,
                    onCheckedChange = viewModel::onDeductFromReservedChanged
                )
            }

            AmountField(
                text = state.amountText,
                onTextChange = viewModel::onAmountTextChanged,
                error = state.amountError
            )

            CategoryDropdown(
                categories = state.availableCategories,
                selectedId = state.selectedCategoryId,
                onSelect = viewModel::onCategorySelected,
                onAddCategory = { name, applicability, color ->
                    viewModel.addCategory(name, applicability, color)
                },
                transactionType = state.type,
                error = state.categoryError
            )

            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChanged,
                label = { Text(stringResource(R.string.entry_description_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                maxLines = 3
            )

            DateField(
                dateMs = state.occurredAtMs,
                onDateSelected = viewModel::onDateChanged
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = viewModel::onSave,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isSaving
            ) {
                Text(stringResource(if (isEditing) R.string.action_update else R.string.action_save))
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeToggle(
    selected: TransactionType,
    onSelect: (TransactionType) -> Unit
) {
    val options = listOf(TransactionType.EXPENSE, TransactionType.RESERVE, TransactionType.INCOME)
    val fin = LocalFinColors.current
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { idx, type ->
            SegmentedButton(
                selected = selected == type,
                onClick = { onSelect(type) },
                shape = SegmentedButtonDefaults.itemShape(index = idx, count = options.size),
                colors = when (type) {
                    TransactionType.EXPENSE -> SegmentedButtonDefaults.colors(
                        activeContainerColor = fin.expense,
                        activeContentColor = fin.onExpense
                    )
                    TransactionType.RESERVE -> SegmentedButtonDefaults.colors(
                        activeContainerColor = fin.reserve,
                        activeContentColor = fin.onReserve
                    )
                    TransactionType.INCOME -> SegmentedButtonDefaults.colors(
                        activeContainerColor = fin.income,
                        activeContentColor = fin.onIncome
                    )
                },
                label = { Text(stringResource(type.labelRes())) }
            )
        }
    }
}

@Composable
private fun DeductFromReservedRow(
    checked: Boolean,
    enabled: Boolean,
    reservedCents: Long,
    amountCents: Long,
    onCheckedChange: (Boolean) -> Unit
) {
    val currency = remember { NumberFormat.getCurrencyInstance(Locale.US) }
    val supporting = if (checked) {
        val (fromReserve, fromBalance) = ReserveDeduction.split(reservedCents, amountCents)
        stringResource(
            R.string.entry_reserve_split,
            currency.format(fromReserve / 100.0),
            currency.format(fromBalance / 100.0)
        )
    } else {
        stringResource(R.string.entry_reserved_available, currency.format(reservedCents.coerceAtLeast(0L) / 100.0))
    }
    val contentColor = if (enabled) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = onCheckedChange
            )
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(
                stringResource(R.string.entry_deduct_from_reserved),
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor
            )
            Text(
                supporting,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else contentColor
            )
        }
    }
}

@Composable
private fun AmountField(
    text: String,
    onTextChange: (String) -> Unit,
    @StringRes error: Int?
) {
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        label = { Text(stringResource(R.string.starting_balance_input_label)) },
        placeholder = { Text("0.00") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        isError = error != null,
        supportingText = error?.let { { Text(stringResource(it), color = MaterialTheme.colorScheme.error) } },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(
    categories: List<com.tuapp.fintrack.data.model.Category>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    onAddCategory: (name: String, applicability: CategoryApplicability, color: String) -> Unit,
    transactionType: TransactionType,
    @StringRes error: Int?
) {
    var expanded by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }

    val selectedName = categories.find { it.id == selectedId }?.name ?: ""

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.category_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            isError = error != null,
            supportingText = error?.let { { Text(stringResource(it), color = MaterialTheme.colorScheme.error) } },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.sms_category_none)) },
                onClick = {
                    onSelect(null)
                    expanded = false
                }
            )
            categories.forEach { cat ->
                DropdownMenuItem(
                    text = { Text(cat.name) },
                    onClick = {
                        onSelect(cat.id)
                        expanded = false
                    }
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.category_add_new), color = MaterialTheme.colorScheme.primary) },
                onClick = {
                    expanded = false
                    showAddDialog = true
                }
            )
        }
    }

    if (showAddDialog) {
        CategoryDialog(
            title = stringResource(R.string.category_new_title),
            confirmLabel = stringResource(R.string.action_add),
            initialApplicability = when (transactionType) {
                TransactionType.INCOME -> CategoryApplicability.INCOME
                TransactionType.EXPENSE, TransactionType.RESERVE -> CategoryApplicability.EXPENSE
            },
            existingCount = categories.size,
            onConfirm = { name, applicability, color ->
                val duplicate = categories.any { it.name.equals(name, ignoreCase = true) }
                if (!duplicate) onAddCategory(name, applicability, color)
                !duplicate
            },
            onDismiss = { showAddDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(
    dateMs: Long,
    onDateSelected: (Long) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    val formatter = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }
    val displayDate = formatter.format(Date(dateMs))

    OutlinedTextField(
        value = displayDate,
        onValueChange = {},
        readOnly = true,
        label = { Text(stringResource(R.string.entry_date_label)) },
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = {
            TextButton(onClick = { showPicker = true }) { Text(stringResource(R.string.action_change)) }
        }
    )

    if (showPicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = Dates.localMillisToPickerMillis(dateMs))
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        onDateSelected(Dates.pickerMillisToLocal(it, time = Dates.localTimeOf(dateMs)))
                    }
                    showPicker = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

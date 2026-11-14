package com.tuapp.fintrack.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tuapp.fintrack.R
import com.tuapp.fintrack.ui.common.labelRes
import com.tuapp.fintrack.ui.theme.LocalFinColors
import com.tuapp.fintrack.data.model.Transaction
import com.tuapp.fintrack.data.model.TransactionType
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionListScreen(
    onNavigateBack: () -> Unit,
    onEditTransaction: (Long) -> Unit,
    viewModel: TransactionListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val deletedMsg = stringResource(R.string.transaction_deleted)
    val undoLabel = stringResource(R.string.undo)
    val categoriesById = remember(state.categories) { state.categories.associateBy { it.id } }

    LaunchedEffect(state.deletedTransactionId) {
        val deletedId = state.deletedTransactionId ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = deletedMsg,
            actionLabel = undoLabel,
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.onRestoreTransaction(deletedId)
        } else {
            viewModel.onUndoConsumed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_transactions)) },
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
        ) {
            FilterBar(
                state = state,
                onTypeFilter = viewModel::onTypeFilterChanged,
                onCategoryFilter = viewModel::onCategoryFilterChanged,
                onPresetFilter = viewModel::onDatePresetChanged,
                onSearch = viewModel::onSearchChanged
            )

            if (state.transactions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.list_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(state.transactions, key = { it.id }) { tx ->
                        val cat = tx.categoryId?.let { categoriesById[it] }
                        TransactionItem(
                            transaction = tx,
                            categoryName = cat?.name,
                            categoryColor = cat?.colorHex,
                            onEdit = { onEditTransaction(tx.id) },
                            onDelete = { viewModel.onDeleteTransaction(tx.id) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBar(
    state: TransactionListUiState,
    onTypeFilter: (TransactionType?) -> Unit,
    onCategoryFilter: (Long?) -> Unit,
    onPresetFilter: (DateRangePreset) -> Unit,
    onSearch: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = state.searchQuery,
            onValueChange = onSearch,
            placeholder = { Text(stringResource(R.string.list_search_hint)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = state.typeFilter == null,
                onClick = { onTypeFilter(null) },
                label = { Text(stringResource(R.string.filter_all)) }
            )
            FilterChip(
                selected = state.typeFilter == TransactionType.INCOME,
                onClick = { onTypeFilter(TransactionType.INCOME) },
                label = { Text(stringResource(R.string.transaction_type_income)) }
            )
            FilterChip(
                selected = state.typeFilter == TransactionType.EXPENSE,
                onClick = { onTypeFilter(TransactionType.EXPENSE) },
                label = { Text(stringResource(R.string.transaction_type_expense)) }
            )
            FilterChip(
                selected = state.typeFilter == TransactionType.RESERVE,
                onClick = { onTypeFilter(TransactionType.RESERVE) },
                label = { Text(stringResource(R.string.transaction_type_reserve)) }
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DateRangePreset.entries.forEach { preset ->
                FilterChip(
                    selected = state.datePreset == preset,
                    onClick = { onPresetFilter(preset) },
                    label = {
                        Text(
                            stringResource(
                                when (preset) {
                                    DateRangePreset.ALL -> R.string.filter_all_time
                                    DateRangePreset.THIS_MONTH -> R.string.filter_this_month
                                    DateRangePreset.LAST_MONTH -> R.string.filter_last_month
                                    DateRangePreset.CUSTOM -> R.string.filter_custom
                                }
                            )
                        )
                    }
                )
            }
        }

        if (state.categories.isNotEmpty()) {
            var expanded by remember { mutableStateOf(false) }
            val selectedCatName = state.categories.find { it.id == state.categoryFilter }?.name ?: stringResource(R.string.filter_all_categories)
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = selectedCatName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.category_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.filter_all_categories)) },
                        onClick = { onCategoryFilter(null); expanded = false }
                    )
                    val catsWithTxns = state.categories.filter { it.id in state.usedCategoryIds }
                    catsWithTxns.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat.name) },
                            onClick = { onCategoryFilter(cat.id); expanded = false }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionItem(
    transaction: Transaction,
    categoryName: String?,
    categoryColor: String?,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.US) }
    val dateFormat = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    val amountText = currencyFormat.format(transaction.amountCents / 100.0)
    val dateText = dateFormat.format(Date(transaction.occurredAt))

    val fin = LocalFinColors.current
    val typeColor = when (transaction.type) {
        TransactionType.INCOME -> fin.income
        TransactionType.EXPENSE -> fin.expense
        TransactionType.RESERVE -> fin.reserve
    }
    val typeLabel = stringResource(transaction.type.labelRes())

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val accentColor = categoryColor?.let { parseHexColor(it) } ?: typeColor
            Box(
                modifier = Modifier
                    .padding(end = 12.dp)
                    .background(accentColor, shape = MaterialTheme.shapes.small)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .semantics { contentDescription = typeLabel }
            ) {
                Text(
                    text = when (transaction.type) {
                        TransactionType.INCOME -> "+"
                        TransactionType.EXPENSE -> "-"
                        TransactionType.RESERVE -> "~"
                    },
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = amountText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = typeColor
                )
                if (categoryName != null) {
                    Text(
                        text = categoryName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (transaction.description.isNotBlank()) {
                    Text(
                        text = transaction.description,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = dateText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_edit), tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun parseHexColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        Color(0xFF757575)
    }
}

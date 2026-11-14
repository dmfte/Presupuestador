package com.tuapp.fintrack.ui.sms

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tuapp.fintrack.R
import com.tuapp.fintrack.data.model.TransactionType

@Composable
private fun typeLabel(type: TransactionType): String = when (type) {
    TransactionType.EXPENSE -> stringResource(R.string.sms_type_expense)
    TransactionType.RESERVE -> stringResource(R.string.sms_type_reserve)
    TransactionType.INCOME -> stringResource(R.string.sms_type_income)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsRulesScreen(
    onNavigateBack: () -> Unit,
    onAddRule: () -> Unit,
    onEditRule: (Long) -> Unit,
    viewModel: SmsRulesViewModel = hiltViewModel()
) {
    val rows by viewModel.rows.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sms_rules_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddRule) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.sms_rule_add))
            }
        }
    ) { innerPadding ->
        val list = rows ?: return@Scaffold
        if (list.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    stringResource(R.string.sms_rules_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = innerPadding) {
            item {
                Text(
                    stringResource(R.string.sms_rule_order_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            itemsIndexed(list, key = { _, row -> row.rule.id }) { index, row ->
                SmsRuleListItem(
                    row = row,
                    canMoveUp = index > 0,
                    canMoveDown = index < list.lastIndex,
                    onClick = { onEditRule(row.rule.id) },
                    onToggle = { viewModel.setEnabled(row.rule.id, it) },
                    onMoveUp = { viewModel.move(row.rule.id, -1) },
                    onMoveDown = { viewModel.move(row.rule.id, +1) }
                )
                HorizontalDivider()
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }
}

@Composable
private fun SmsRuleListItem(
    row: SmsRuleRow,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val rule = row.rule
    val dimmed = !rule.enabled || row.duplicateOf != null
    ListItem(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        leadingContent = {
            // Default IconButton size keeps the 48dp minimum touch target.
            Row {
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.sms_rule_move_up))
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.sms_rule_move_down))
                }
            }
        },
        headlineContent = {
            Text(
                rule.displayName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
            )
        },
        supportingContent = {
            Column {
                val details = buildList {
                    add("“${rule.identifierText}”")
                    add("${stringResource(R.string.sms_amount_prefix_title)}: “${rule.amountPrefix}”")
                    add(typeLabel(rule.type))
                    row.categoryName?.let { add(it) }
                }.joinToString(" · ")
                Text(details, maxLines = 2, overflow = TextOverflow.Ellipsis)
                row.duplicateOf?.let { earlier ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            stringResource(R.string.sms_rule_duplicate_of, earlier.displayName),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
            }
        },
        trailingContent = {
            Switch(checked = rule.enabled, onCheckedChange = onToggle)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsRuleEditScreen(
    onNavigateBack: () -> Unit,
    viewModel: SmsRuleEditViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(state.finished) {
        if (state.finished) onNavigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (state.isNew) R.string.sms_rule_new_title else R.string.sms_rule_edit_title))
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.sms_rule_delete))
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (!state.loaded) return@Scaffold
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

            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onName,
                label = { Text(stringResource(R.string.sms_rule_name_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.identifierText,
                onValueChange = viewModel::onIdentifier,
                label = { Text(stringResource(R.string.sms_identifier_title)) },
                supportingText = {
                    Text(stringResource(if (state.identifierError) R.string.sms_rule_required else R.string.sms_identifier_subtitle))
                },
                isError = state.identifierError,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = state.amountPrefix,
                onValueChange = viewModel::onPrefix,
                label = { Text(stringResource(R.string.sms_amount_prefix_title)) },
                supportingText = {
                    Text(stringResource(if (state.prefixError) R.string.sms_rule_required else R.string.sms_amount_prefix_subtitle))
                },
                isError = state.prefixError,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text(stringResource(R.string.sms_type_title), style = MaterialTheme.typography.labelLarge)
            val types = listOf(TransactionType.EXPENSE, TransactionType.RESERVE, TransactionType.INCOME)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                types.forEachIndexed { idx, type ->
                    SegmentedButton(
                        selected = state.type == type,
                        onClick = { viewModel.onType(type) },
                        shape = SegmentedButtonDefaults.itemShape(idx, types.size),
                        label = { Text(typeLabel(type)) }
                    )
                }
            }

            var expanded by remember { mutableStateOf(false) }
            val noneLabel = stringResource(R.string.sms_category_none)
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = categories.find { it.id == state.categoryId }?.name ?: noneLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.sms_category_title)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text(noneLabel) },
                        onClick = { viewModel.onCategory(null); expanded = false }
                    )
                    categories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.name) },
                            onClick = { viewModel.onCategory(category.id); expanded = false }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = state.descriptionTemplate,
                onValueChange = viewModel::onDescription,
                label = { Text(stringResource(R.string.sms_description_title)) },
                supportingText = { Text(stringResource(R.string.sms_description_subtitle)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.sms_rule_enabled)) },
                trailingContent = { Switch(checked = state.enabled, onCheckedChange = viewModel::onEnabled) }
            )

            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_save))
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.sms_rule_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

package com.tuapp.fintrack.ui.paycycles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tuapp.fintrack.domain.model.PayCycleRule
import com.tuapp.fintrack.domain.model.PayCycleWithEffectiveDate
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)

private fun dayOfWeekName(calDay: Int): String = when (calDay) {
    Calendar.SUNDAY -> "Sunday"
    Calendar.MONDAY -> "Monday"
    Calendar.TUESDAY -> "Tuesday"
    Calendar.WEDNESDAY -> "Wednesday"
    Calendar.THURSDAY -> "Thursday"
    Calendar.FRIDAY -> "Friday"
    Calendar.SATURDAY -> "Saturday"
    else -> "Unknown"
}

private fun ruleDisplayName(rule: PayCycleRule): String = when (rule) {
    is PayCycleRule.DayOfMonthRule -> "Day ${rule.day} of month"
    is PayCycleRule.LastDayOfMonthRule -> "Last day of month"
    is PayCycleRule.SpecificDateRule -> "Fixed: ${dateFormat.format(Date(rule.dateMs))}"
    is PayCycleRule.WeeklyRule -> "Every ${dayOfWeekName(rule.dayOfWeekAnchor)}"
    is PayCycleRule.BiweeklyRule -> "Every 2 weeks (${dayOfWeekName(rule.dayOfWeek)})"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayCyclesScreen(
    onNavigateBack: () -> Unit,
    viewModel: PayCyclesViewModel = hiltViewModel()
) {
    val payCycles by viewModel.payCycles.collectAsState()
    val snackbarMessage by viewModel.snackbarMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingCycle by remember { mutableStateOf<PayCycleWithEffectiveDate?>(null) }
    var deleteConfirmId by remember { mutableStateOf<Long?>(null) }
    var lastDeletedId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage
        if (msg != null) {
            val result = snackbarHostState.showSnackbar(
                message = msg,
                actionLabel = if (msg.contains("deleted")) "Undo" else null,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                lastDeletedId?.let { id ->
                    viewModel.onUndoDelete(id)
                    lastDeletedId = null
                }
            }
            viewModel.clearSnackbar()
        }
    }

    if (showAddDialog) {
        PayCycleDialog(
            existing = null,
            onDismiss = { showAddDialog = false },
            onSave = { rule, weekend, holiday ->
                viewModel.onAddPayCycle(rule, weekend, holiday)
                showAddDialog = false
            }
        )
    }

    editingCycle?.let { cycleItem ->
        PayCycleDialog(
            existing = cycleItem,
            onDismiss = { editingCycle = null },
            onSave = { rule, weekend, holiday ->
                viewModel.onEditPayCycle(cycleItem.cycle.id, rule, weekend, holiday)
                editingCycle = null
            }
        )
    }

    deleteConfirmId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteConfirmId = null },
            title = { Text("Delete Pay Cycle") },
            text = { Text("Are you sure you want to delete this pay cycle?") },
            confirmButton = {
                Button(onClick = {
                    lastDeletedId = id
                    viewModel.onDeletePayCycle(id)
                    deleteConfirmId = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmId = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pay Cycles") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add pay cycle")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        if (payCycles.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "No pay cycles configured",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Tap + to add a pay cycle",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(payCycles, key = { it.cycle.id }) { item ->
                    PayCycleItem(
                        item = item,
                        onEdit = { editingCycle = item },
                        onDelete = { deleteConfirmId = item.cycle.id },
                        onToggleWeekend = { enabled ->
                            viewModel.onEditPayCycle(
                                item.cycle.id,
                                item.rule,
                                enabled,
                                item.cycle.rollBackOnHoliday
                            )
                        },
                        onToggleHoliday = { enabled ->
                            viewModel.onEditPayCycle(
                                item.cycle.id,
                                item.rule,
                                item.cycle.rollBackOnWeekend,
                                enabled
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PayCycleItem(
    item: PayCycleWithEffectiveDate,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleWeekend: (Boolean) -> Unit,
    onToggleHoliday: (Boolean) -> Unit
) {
    val nominalStr = dateFormat.format(Date(item.nextNominalDateMs))
    val effectiveStr = dateFormat.format(Date(item.nextEffectiveDateMs))
    val effectiveSameAsNominal = item.nextNominalDateMs == item.nextEffectiveDateMs

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = ruleDisplayName(item.rule),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
            Text(
                text = if (effectiveSameAsNominal) {
                    "Next: $effectiveStr"
                } else {
                    "Next: $effectiveStr (nominally $nominalStr)"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Weekend rollback",
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = item.cycle.rollBackOnWeekend,
                    onCheckedChange = onToggleWeekend
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Holiday rollback",
                    style = MaterialTheme.typography.bodyMedium
                )
                Switch(
                    checked = item.cycle.rollBackOnHoliday,
                    onCheckedChange = onToggleHoliday
                )
            }
        }
    }
}

private enum class RuleType { DayOfMonth, LastDayOfMonth, SpecificDate, Weekly, Biweekly }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PayCycleDialog(
    existing: PayCycleWithEffectiveDate?,
    onDismiss: () -> Unit,
    onSave: (PayCycleRule, Boolean, Boolean) -> Unit
) {
    val initialRuleType = when (existing?.rule) {
        is PayCycleRule.DayOfMonthRule -> RuleType.DayOfMonth
        is PayCycleRule.LastDayOfMonthRule -> RuleType.LastDayOfMonth
        is PayCycleRule.SpecificDateRule -> RuleType.SpecificDate
        is PayCycleRule.WeeklyRule -> RuleType.Weekly
        is PayCycleRule.BiweeklyRule -> RuleType.Biweekly
        null -> RuleType.DayOfMonth
    }

    var selectedRuleType by remember { mutableStateOf(initialRuleType) }
    var dayOfMonth by remember {
        mutableIntStateOf(
            (existing?.rule as? PayCycleRule.DayOfMonthRule)?.day ?: 15
        )
    }
    var dayOfMonthText by remember { mutableStateOf(dayOfMonth.toString()) }
    var specificDateMs by remember {
        mutableLongStateOf(
            (existing?.rule as? PayCycleRule.SpecificDateRule)?.dateMs ?: System.currentTimeMillis()
        )
    }
    var weeklyDayOfWeek by remember {
        mutableIntStateOf(
            (existing?.rule as? PayCycleRule.WeeklyRule)?.dayOfWeekAnchor ?: Calendar.FRIDAY
        )
    }
    var biweeklyAnchorMs by remember {
        mutableLongStateOf(
            (existing?.rule as? PayCycleRule.BiweeklyRule)?.anchorDateMs ?: System.currentTimeMillis()
        )
    }
    var biweeklyDayOfWeek by remember {
        mutableIntStateOf(
            (existing?.rule as? PayCycleRule.BiweeklyRule)?.dayOfWeek ?: Calendar.FRIDAY
        )
    }
    var rollBackOnWeekend by remember {
        mutableStateOf(existing?.cycle?.rollBackOnWeekend ?: true)
    }
    var rollBackOnHoliday by remember {
        mutableStateOf(existing?.cycle?.rollBackOnHoliday ?: true)
    }

    var showSpecificDatePicker by remember { mutableStateOf(false) }
    var showBiweeklyAnchorPicker by remember { mutableStateOf(false) }

    if (showSpecificDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = specificDateMs)
        DatePickerDialog(
            onDismissRequest = { showSpecificDatePicker = false },
            confirmButton = {
                Button(onClick = {
                    datePickerState.selectedDateMillis?.let { specificDateMs = it }
                    showSpecificDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showSpecificDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showBiweeklyAnchorPicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = biweeklyAnchorMs)
        DatePickerDialog(
            onDismissRequest = { showBiweeklyAnchorPicker = false },
            confirmButton = {
                Button(onClick = {
                    datePickerState.selectedDateMillis?.let { biweeklyAnchorMs = it }
                    showBiweeklyAnchorPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showBiweeklyAnchorPicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add Pay Cycle" else "Edit Pay Cycle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Rule type selection
                Text("Rule type", style = MaterialTheme.typography.labelLarge)

                RuleType.entries.forEach { type ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        RadioButton(
                            selected = selectedRuleType == type,
                            onClick = { selectedRuleType = type }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = when (type) {
                                RuleType.DayOfMonth -> "Day of month"
                                RuleType.LastDayOfMonth -> "Last day of month"
                                RuleType.SpecificDate -> "Fixed date"
                                RuleType.Weekly -> "Weekly"
                                RuleType.Biweekly -> "Every 2 weeks"
                            }
                        )
                    }
                }

                // Rule-specific inputs
                when (selectedRuleType) {
                    RuleType.DayOfMonth -> {
                        OutlinedTextField(
                            value = dayOfMonthText,
                            onValueChange = { text ->
                                dayOfMonthText = text
                                text.toIntOrNull()?.let { v ->
                                    if (v in 1..31) dayOfMonth = v
                                }
                            },
                            label = { Text("Day (1-31)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    RuleType.LastDayOfMonth -> {
                        // No extra input needed
                    }

                    RuleType.SpecificDate -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Date: ${dateFormat.format(Date(specificDateMs))}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            TextButton(onClick = { showSpecificDatePicker = true }) {
                                Text("Pick date")
                            }
                        }
                    }

                    RuleType.Weekly -> {
                        DayOfWeekDropdown(
                            label = "Day of week",
                            selected = weeklyDayOfWeek,
                            onSelected = { weeklyDayOfWeek = it }
                        )
                    }

                    RuleType.Biweekly -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Anchor: ${dateFormat.format(Date(biweeklyAnchorMs))}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            TextButton(onClick = { showBiweeklyAnchorPicker = true }) {
                                Text("Pick")
                            }
                        }
                        DayOfWeekDropdown(
                            label = "Day of week",
                            selected = biweeklyDayOfWeek,
                            onSelected = { biweeklyDayOfWeek = it }
                        )
                    }
                }

                HorizontalDivider()

                // Rollback toggles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Weekend rollback", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = rollBackOnWeekend, onCheckedChange = { rollBackOnWeekend = it })
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Holiday rollback", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = rollBackOnHoliday, onCheckedChange = { rollBackOnHoliday = it })
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val rule = when (selectedRuleType) {
                    RuleType.DayOfMonth -> PayCycleRule.DayOfMonthRule(dayOfMonth)
                    RuleType.LastDayOfMonth -> PayCycleRule.LastDayOfMonthRule
                    RuleType.SpecificDate -> PayCycleRule.SpecificDateRule(specificDateMs)
                    RuleType.Weekly -> PayCycleRule.WeeklyRule(weeklyDayOfWeek)
                    RuleType.Biweekly -> PayCycleRule.BiweeklyRule(biweeklyAnchorMs, biweeklyDayOfWeek)
                }
                onSave(rule, rollBackOnWeekend, rollBackOnHoliday)
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayOfWeekDropdown(
    label: String,
    selected: Int,
    onSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val days = listOf(
        Calendar.MONDAY to "Monday",
        Calendar.TUESDAY to "Tuesday",
        Calendar.WEDNESDAY to "Wednesday",
        Calendar.THURSDAY to "Thursday",
        Calendar.FRIDAY to "Friday",
        Calendar.SATURDAY to "Saturday",
        Calendar.SUNDAY to "Sunday"
    )

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = dayOfWeekName(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            days.forEach { (calDay, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onSelected(calDay)
                        expanded = false
                    }
                )
            }
        }
    }
}

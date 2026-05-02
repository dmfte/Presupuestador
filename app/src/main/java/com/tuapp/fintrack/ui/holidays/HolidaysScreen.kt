package com.tuapp.fintrack.ui.holidays

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tuapp.fintrack.data.model.Holiday
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val shortDateFormat = SimpleDateFormat("MMM d", Locale.US)
private val longDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HolidaysScreen(
    onNavigateBack: () -> Unit,
    viewModel: HolidaysViewModel = hiltViewModel()
) {
    val holidays by viewModel.holidays.collectAsState()
    val snackbarMessage by viewModel.snackbarMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingHoliday by remember { mutableStateOf<Holiday?>(null) }
    var deleteConfirmHoliday by remember { mutableStateOf<Holiday?>(null) }
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
        HolidayDialog(
            existing = null,
            onDismiss = { showAddDialog = false },
            onSave = { label, dateMs, recurring ->
                viewModel.onAddHoliday(label, dateMs, recurring)
                showAddDialog = false
            }
        )
    }

    editingHoliday?.let { holiday ->
        HolidayDialog(
            existing = holiday,
            onDismiss = { editingHoliday = null },
            onSave = { label, dateMs, recurring ->
                viewModel.onEditHoliday(holiday.id, label, dateMs, recurring)
                editingHoliday = null
            }
        )
    }

    deleteConfirmHoliday?.let { holiday ->
        AlertDialog(
            onDismissRequest = { deleteConfirmHoliday = null },
            title = { Text("Delete Holiday") },
            text = { Text("Delete \"${holiday.label}\"? This action can be undone.") },
            confirmButton = {
                Button(onClick = {
                    lastDeletedId = holiday.id
                    viewModel.onDeleteHoliday(holiday.id)
                    deleteConfirmHoliday = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmHoliday = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Holidays") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add holiday")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        if (holidays.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "No holidays configured",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Tap + to add a holiday",
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
                items(holidays, key = { it.id }) { holiday ->
                    HolidayItem(
                        holiday = holiday,
                        onEdit = { editingHoliday = holiday },
                        onDelete = { deleteConfirmHoliday = holiday },
                        onToggle = { enabled -> viewModel.onToggleHoliday(holiday.id, enabled) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HolidayItem(
    holiday: Holiday,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    val dateStr = shortDateFormat.format(Date(holiday.dateOfYear))

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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = holiday.label,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = dateStr,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (holiday.recurringYearly) {
                            FilterChip(
                                selected = true,
                                onClick = {},
                                label = { Text("Recurring", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
                Switch(
                    checked = holiday.enabled,
                    onCheckedChange = onToggle
                )
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HolidayDialog(
    existing: Holiday?,
    onDismiss: () -> Unit,
    onSave: (String, Long, Boolean) -> Unit
) {
    var label by remember { mutableStateOf(existing?.label ?: "") }
    var dateMs by remember { mutableLongStateOf(existing?.dateOfYear ?: System.currentTimeMillis()) }
    var recurringYearly by remember { mutableStateOf(existing?.recurringYearly ?: true) }
    var showDatePicker by remember { mutableStateOf(false) }
    var labelError by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dateMs)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                Button(onClick = {
                    datePickerState.selectedDateMillis?.let { dateMs = it }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add Holiday" else "Edit Holiday") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = {
                        label = it
                        labelError = false
                    },
                    label = { Text("Label") },
                    isError = labelError,
                    supportingText = if (labelError) {
                        { Text("Label cannot be empty") }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Date: ${longDateFormat.format(Date(dateMs))}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(onClick = { showDatePicker = true }) {
                        Text("Pick date")
                    }
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Recurring yearly", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = recurringYearly,
                        onCheckedChange = { recurringYearly = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (label.isBlank()) {
                    labelError = true
                } else {
                    onSave(label.trim(), dateMs, recurringYearly)
                }
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

package com.tuapp.fintrack.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.tuapp.fintrack.R
import com.tuapp.fintrack.ui.common.StartingBalanceDialog
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onExportData: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val requireCategory by viewModel.requireCategory.collectAsState()
    val startingBalanceCents by viewModel.startingBalanceCents.collectAsState()
    val startingBalanceSetAt by viewModel.startingBalanceSetAt.collectAsState()
    val periodStartMs by viewModel.periodStartMs.collectAsState()
    val smsEnabled by viewModel.smsAutoEnabled.collectAsState()
    val smsIdentifier by viewModel.smsIdentifierText.collectAsState()
    val smsAmountPrefix by viewModel.smsAmountPrefix.collectAsState()
    val smsType by viewModel.smsTransactionType.collectAsState()
    val smsCategoryId by viewModel.smsDefaultCategoryId.collectAsState()
    val smsDescription by viewModel.smsDescriptionTemplate.collectAsState()
    val hasNotificationAccess by viewModel.hasNotificationAccess.collectAsState()
    val categories by viewModel.categories.collectAsState()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshNotificationAccess()
        }
    }

    var showIdentifierDialog by remember { mutableStateOf(false) }
    var showAmountPrefixDialog by remember { mutableStateOf(false) }
    var showDescriptionDialog by remember { mutableStateOf(false) }
    var showNotificationAccessDialog by remember { mutableStateOf(false) }
    var showStartingBalanceDialog by remember { mutableStateOf(false) }
    var showPeriodDatePickerDialog by remember { mutableStateOf(false) }

    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale.US) }
    val setAtFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val periodDateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding
        ) {
            item {
                Text(
                    text = "Budget period",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                )
            }
            item {
                val periodText = if (periodStartMs == 0L) "All time"
                    else "Started ${periodDateFormat.format(Date(periodStartMs))}"
                ListItem(
                    headlineContent = { Text("Period start date") },
                    supportingContent = { Text(periodText) },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPeriodDatePickerDialog = true }
                )
                HorizontalDivider()
            }
            item {
                val balanceText = currencyFormat.format(startingBalanceCents / 100.0)
                val supporting = if (startingBalanceSetAt > 0L) {
                    "$balanceText · ${stringResource(R.string.starting_balance_set_on, setAtFormat.format(Date(startingBalanceSetAt)))}"
                } else {
                    balanceText
                }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.starting_balance_title)) },
                    supportingContent = { Text(supporting) },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showStartingBalanceDialog = true }
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    headlineContent = { Text("Require category") },
                    supportingContent = { Text("All transactions must have a category") },
                    trailingContent = {
                        Switch(
                            checked = requireCategory,
                            onCheckedChange = viewModel::setRequireCategory
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    headlineContent = { Text("Export data") },
                    supportingContent = { Text("Export transactions as CSV or JSON") },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onExportData)
                )
                HorizontalDivider()
            }

            // SMS Auto-Transaction section
            item {
                Text(
                    text = stringResource(R.string.sms_section_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.sms_enabled_title)) },
                    supportingContent = { Text(stringResource(R.string.sms_enabled_subtitle)) },
                    trailingContent = {
                        Switch(
                            checked = smsEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled && !hasNotificationAccess) {
                                    showNotificationAccessDialog = true
                                } else {
                                    viewModel.setSmsAutoEnabled(enabled)
                                }
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                HorizontalDivider()
            }
            if (smsEnabled) {
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.sms_identifier_title)) },
                        supportingContent = {
                            Text(smsIdentifier.ifBlank { stringResource(R.string.sms_identifier_subtitle) })
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showIdentifierDialog = true }
                    )
                    HorizontalDivider()
                }
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.sms_amount_prefix_title)) },
                        supportingContent = {
                            Text(smsAmountPrefix.ifBlank { stringResource(R.string.sms_amount_prefix_subtitle) })
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAmountPrefixDialog = true }
                    )
                    HorizontalDivider()
                }
                item {
                    val typeOptions = listOf("EXPENSE" to stringResource(R.string.sms_type_expense), "RESERVE" to stringResource(R.string.sms_type_reserve), "INCOME" to stringResource(R.string.sms_type_income))
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.sms_type_title)) },
                        supportingContent = {
                            SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(top = 8.dp)) {
                                typeOptions.forEachIndexed { idx, (value, label) ->
                                    SegmentedButton(
                                        selected = smsType == value,
                                        onClick = { viewModel.setSmsTransactionType(value) },
                                        shape = SegmentedButtonDefaults.itemShape(idx, typeOptions.size),
                                        label = { Text(label) }
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    HorizontalDivider()
                }
                item {
                    val selectedCategory = categories.find { it.id == smsCategoryId }
                    var expanded by remember { mutableStateOf(false) }
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.sms_category_title)) },
                        supportingContent = {
                            ExposedDropdownMenuBox(
                                expanded = expanded,
                                onExpandedChange = { expanded = it }
                            ) {
                                OutlinedTextField(
                                    value = selectedCategory?.name ?: stringResource(R.string.sms_category_none),
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                                    modifier = Modifier
                                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                )
                                ExposedDropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.sms_category_none)) },
                                        onClick = {
                                            viewModel.setSmsDefaultCategoryId(null)
                                            expanded = false
                                        }
                                    )
                                    categories.forEach { category ->
                                        DropdownMenuItem(
                                            text = { Text(category.name) },
                                            onClick = {
                                                viewModel.setSmsDefaultCategoryId(category.id)
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    HorizontalDivider()
                }
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.sms_description_title)) },
                        supportingContent = {
                            Text(smsDescription.ifBlank { stringResource(R.string.sms_description_subtitle) })
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDescriptionDialog = true }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (showNotificationAccessDialog) {
        AlertDialog(
            onDismissRequest = { showNotificationAccessDialog = false },
            title = { Text(stringResource(R.string.sms_notification_access_required)) },
            text = { Text(stringResource(R.string.sms_notification_access_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showNotificationAccessDialog = false
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }) {
                    Text(stringResource(R.string.sms_open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotificationAccessDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showIdentifierDialog) {
        TextInputDialog(
            title = stringResource(R.string.sms_identifier_title),
            currentValue = smsIdentifier,
            onConfirm = { viewModel.setSmsIdentifierText(it) },
            onDismiss = { showIdentifierDialog = false }
        )
    }

    if (showAmountPrefixDialog) {
        TextInputDialog(
            title = stringResource(R.string.sms_amount_prefix_title),
            currentValue = smsAmountPrefix,
            onConfirm = { viewModel.setSmsAmountPrefix(it) },
            onDismiss = { showAmountPrefixDialog = false }
        )
    }

    if (showDescriptionDialog) {
        TextInputDialog(
            title = stringResource(R.string.sms_description_title),
            currentValue = smsDescription,
            onConfirm = { viewModel.setSmsDescriptionTemplate(it) },
            onDismiss = { showDescriptionDialog = false }
        )
    }

    if (showPeriodDatePickerDialog) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = if (periodStartMs > 0L) periodStartMs else System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showPeriodDatePickerDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { viewModel.setPeriodStartDate(it) }
                    showPeriodDatePickerDialog = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPeriodDatePickerDialog = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showStartingBalanceDialog) {
        StartingBalanceDialog(
            title = stringResource(R.string.starting_balance_edit_title),
            message = stringResource(R.string.starting_balance_edit_message),
            initialCents = startingBalanceCents,
            confirmLabel = stringResource(R.string.starting_balance_save),
            skipLabel = stringResource(R.string.starting_balance_cancel),
            dismissOnOutsideTap = true,
            onConfirm = {
                viewModel.setStartingBalance(it)
                showStartingBalanceDialog = false
            },
            onSkip = { showStartingBalanceDialog = false },
            onDismiss = { showStartingBalanceDialog = false }
        )
    }
}

@Composable
private fun TextInputDialog(
    title: String,
    currentValue: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(currentValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(text)
                onDismiss()
            }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

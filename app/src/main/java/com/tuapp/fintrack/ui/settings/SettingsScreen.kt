package com.tuapp.fintrack.ui.settings

import com.tuapp.fintrack.domain.util.Dates
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.selection.toggleable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
    onSmsRules: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val requireCategory by viewModel.requireCategory.collectAsStateWithLifecycle()
    val startingBalanceCents by viewModel.startingBalanceCents.collectAsStateWithLifecycle()
    val startingBalanceSetAt by viewModel.startingBalanceSetAt.collectAsStateWithLifecycle()
    val periodStartMs by viewModel.periodStartMs.collectAsStateWithLifecycle()
    val smsEnabled by viewModel.smsAutoEnabled.collectAsStateWithLifecycle()
    val smsRules by viewModel.smsRules.collectAsStateWithLifecycle()
    val hasNotificationAccess by viewModel.hasNotificationAccess.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshNotificationAccess()
        }
    }

    var showResetDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val dataDeletedMessage = stringResource(R.string.reset_done)
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = innerPadding
        ) {
            item {
                Text(
                    text = stringResource(R.string.settings_budget_period),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                        .semantics { heading() }
                )
            }
            item {
                val periodText = if (periodStartMs == 0L) stringResource(R.string.filter_all_time)
                    else stringResource(R.string.settings_period_started, periodDateFormat.format(Date(periodStartMs)))
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_period_start)) },
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
                    headlineContent = { Text(stringResource(R.string.settings_require_category)) },
                    supportingContent = { Text(stringResource(R.string.settings_require_category_desc)) },
                    trailingContent = {
                        Switch(checked = requireCategory, onCheckedChange = null)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = requireCategory,
                            role = Role.Switch,
                            onValueChange = viewModel::setRequireCategory
                        )
                )
                HorizontalDivider()
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_export_data)) },
                    supportingContent = { Text(stringResource(R.string.settings_export_data_desc)) },
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
                    modifier = Modifier
                        .padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
                        .semantics { heading() }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.sms_enabled_title)) },
                    supportingContent = { Text(stringResource(R.string.sms_enabled_subtitle)) },
                    trailingContent = {
                        Switch(checked = smsEnabled, onCheckedChange = null)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = smsEnabled, role = Role.Switch) { enabled ->
                            if (enabled && !hasNotificationAccess) {
                                showNotificationAccessDialog = true
                            } else {
                                viewModel.setSmsAutoEnabled(enabled)
                            }
                        }
                )
                HorizontalDivider()
            }
            item {
                val active = smsRules.count { it.enabled }
                ListItem(
                    headlineContent = { Text(stringResource(R.string.sms_rules_title)) },
                    supportingContent = {
                        Text(
                            if (smsRules.isEmpty()) stringResource(R.string.sms_rules_none_configured)
                            else stringResource(R.string.sms_rules_summary, smsRules.size, active)
                        )
                    },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onSmsRules)
                )
                HorizontalDivider()
            }

            // Data / reset section
            item {
                Text(
                    text = stringResource(R.string.reset_section_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
                        .semantics { heading() }
                )
            }
            item {
                ListItem(
                    headlineContent = {
                        Text(stringResource(R.string.reset_title), color = MaterialTheme.colorScheme.error)
                    },
                    supportingContent = { Text(stringResource(R.string.reset_subtitle)) },
                    leadingContent = {
                        Icon(
                            Icons.Filled.DeleteForever,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showResetDialog = true }
                )
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
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showResetDialog) {
        ResetDataDialog(
            onDismiss = { showResetDialog = false },
            onExportFirst = {
                showResetDialog = false
                onExportData()
            },
            onConfirm = { everything ->
                showResetDialog = false
                viewModel.deleteData(everything) {
                    scope.launch { snackbarHostState.showSnackbar(dataDeletedMessage) }
                }
            }
        )
    }

    if (showPeriodDatePickerDialog) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = Dates.localMillisToPickerMillis(
                if (periodStartMs > 0L) periodStartMs else System.currentTimeMillis()
            )
        )
        DatePickerDialog(
            onDismissRequest = { showPeriodDatePickerDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        viewModel.setPeriodStartDate(Dates.pickerMillisToLocalStartOfDay(it))
                    }
                    showPeriodDatePickerDialog = false
                }) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPeriodDatePickerDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
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
private fun ResetDataDialog(
    onDismiss: () -> Unit,
    onExportFirst: () -> Unit,
    onConfirm: (everything: Boolean) -> Unit
) {
    var everything by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    val confirmWord = stringResource(R.string.reset_confirm_word)
    val confirmed = typed.trim().equals(confirmWord, ignoreCase = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(stringResource(R.string.reset_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                ResetOption(
                    selected = !everything,
                    title = stringResource(R.string.reset_option_transactions),
                    description = stringResource(R.string.reset_option_transactions_desc),
                    onClick = { everything = false }
                )
                ResetOption(
                    selected = everything,
                    title = stringResource(R.string.reset_option_everything),
                    description = stringResource(R.string.reset_option_everything_desc),
                    onClick = { everything = true }
                )
                Text(
                    stringResource(R.string.reset_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text(stringResource(R.string.reset_confirm_label, confirmWord)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = onExportFirst, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(R.string.reset_export_first))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(everything) },
                enabled = confirmed,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.action_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun ResetOption(
    selected: Boolean,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(top = 2.dp))
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

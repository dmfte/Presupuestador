package com.tuapp.fintrack.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tuapp.fintrack.data.model.TransactionType
import com.tuapp.fintrack.ui.MainViewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddTransaction: () -> Unit,
    onViewTransactions: () -> Unit,
    onViewCategories: () -> Unit = {},
    onViewBudgets: () -> Unit = {},
    onViewPayCycles: () -> Unit = {},
    onViewHolidays: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    mainViewModel: MainViewModel = hiltViewModel()
) {
    val transactions by mainViewModel.transactions.collectAsState()
    val periodSummary by viewModel.periodSummary.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)
    val dateFormat = SimpleDateFormat("MMM d", Locale.US)

    val snackbarHostState = remember { SnackbarHostState() }
    var showPayConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.lastSnackbar) {
        val msg = uiState.lastSnackbar
        if (msg != null) {
            snackbarHostState.showSnackbar(message = msg, duration = SnackbarDuration.Short)
            viewModel.clearSnackbar()
        }
    }

    if (showPayConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showPayConfirmDialog = false },
            title = { Text("Record Pay Event") },
            text = { Text("This will start a new pay period from today. Continue?") },
            confirmButton = {
                Button(onClick = {
                    showPayConfirmDialog = false
                    viewModel.onPaymentRecorded()
                }) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(onClick = { showPayConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("FinTrack") })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddTransaction,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add Transaction") }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Period summary card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (periodSummary != null) {
                        val summary = periodSummary!!
                        val period = summary.period
                        val startStr = dateFormat.format(Date(period.startDateMs))
                        val endStr = dateFormat.format(Date(period.endDateMs))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "$startStr – $endStr",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${period.daysRemaining} days left",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (period.daysRemaining <= 7)
                                    MaterialTheme.colorScheme.error
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (period.daysRemaining <= 7) {
                            LinearProgressIndicator(
                                progress = { (7 - period.daysRemaining) / 7f },
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        val net = summary.netCents
                        Text(
                            text = currencyFormat.format(net / 100.0),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (net >= 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Income",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = currencyFormat.format(summary.totalIncomeCents / 100.0),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color(0xFF2E7D32),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Expenses",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = currencyFormat.format(summary.totalExpensesCents / 100.0),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color(0xFFC62828),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "Current Period",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Loading...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // "I've Been Paid Today" button
            FilledTonalButton(
                onClick = { showPayConfirmDialog = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isPayEventLoading
            ) {
                Text("I've Been Paid Today")
            }

            // Navigation row: Categories | Budgets | Pay Cycles | Holidays
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextButton(onClick = onViewCategories) { Text("Categories") }
                TextButton(onClick = onViewBudgets) { Text("Budgets") }
                TextButton(onClick = onViewPayCycles) { Text("Pay Cycles") }
                TextButton(onClick = onViewHolidays) { Text("Holidays") }
            }

            // Recent transactions header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Transactions (${transactions.size})",
                    style = MaterialTheme.typography.titleMedium
                )
                TextButton(onClick = onViewTransactions) {
                    Text("View all")
                }
            }

            if (transactions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No transactions yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Tap + to add your first transaction",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                val recent = transactions.take(5)
                recent.forEach { tx ->
                    val amountText = currencyFormat.format(tx.amountCents / 100.0)
                    val sign = if (tx.type == TransactionType.INCOME) "+" else "-"
                    val color = if (tx.type == TransactionType.INCOME) Color(0xFF2E7D32) else Color(0xFFC62828)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (tx.description.isNotBlank()) tx.description else tx.type.name.lowercase(),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "$sign$amountText",
                            style = MaterialTheme.typography.bodyMedium,
                            color = color,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

package com.tuapp.fintrack.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.res.stringResource
import com.tuapp.fintrack.R

@Composable
fun StartingBalanceDialog(
    title: String,
    message: String,
    initialCents: Long,
    confirmLabel: String,
    skipLabel: String?,
    dismissOnOutsideTap: Boolean,
    onConfirm: (cents: Long) -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(if (initialCents == 0L) "" else "%.2f".format(initialCents / 100.0)) }
    var hasError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = dismissOnOutsideTap,
            dismissOnClickOutside = dismissOnOutsideTap
        ),
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        hasError = false
                    },
                    label = { Text(stringResource(R.string.starting_balance_input_label)) },
                    singleLine = true,
                    isError = hasError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                if (hasError) {
                    Text(
                        text = stringResource(R.string.starting_balance_input_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val cents = parseCents(text)
                if (cents == null) {
                    hasError = true
                } else {
                    onConfirm(cents)
                }
            }) {
                Text(confirmLabel)
            }
        },
        dismissButton = skipLabel?.let {
            {
                TextButton(onClick = onSkip) { Text(it) }
            }
        }
    )
}

private fun parseCents(raw: String): Long? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return 0L
    val normalized = trimmed.replace(",", ".")
    val value = normalized.toDoubleOrNull() ?: return null
    return Math.round(value * 100.0)
}

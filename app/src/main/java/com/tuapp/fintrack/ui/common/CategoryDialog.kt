package com.tuapp.fintrack.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tuapp.fintrack.data.model.CategoryApplicability
import com.tuapp.fintrack.ui.categories.CategoriesViewModel.Companion.CATEGORY_COLORS

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CategoryDialog(
    title: String,
    confirmLabel: String,
    initialName: String = "",
    initialApplicability: CategoryApplicability = CategoryApplicability.EXPENSE,
    initialColor: String? = null,
    existingCount: Int = 0,
    onConfirm: (name: String, applicability: CategoryApplicability, color: String) -> Boolean,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var applicability by remember { mutableStateOf(initialApplicability) }
    var selectedColor by remember {
        mutableStateOf(initialColor ?: CATEGORY_COLORS[existingCount % CATEGORY_COLORS.size])
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = null
                    },
                    label = { Text("Category name") },
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    singleLine = true
                )

                val applicabilityOptions = listOf(
                    CategoryApplicability.EXPENSE to "Expense",
                    CategoryApplicability.INCOME to "Income",
                    CategoryApplicability.BOTH to "Both"
                )
                SingleChoiceSegmentedButtonRow {
                    applicabilityOptions.forEachIndexed { idx, (type, label) ->
                        SegmentedButton(
                            selected = applicability == type,
                            onClick = { applicability = type },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = idx,
                                count = applicabilityOptions.size
                            ),
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Text("Color", style = MaterialTheme.typography.labelMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CATEGORY_COLORS.forEach { hex ->
                        val colorInt = remember(hex) {
                            try { android.graphics.Color.parseColor(hex) }
                            catch (e: Exception) { android.graphics.Color.GRAY }
                        }
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(Color(colorInt), CircleShape)
                                .then(
                                    if (hex == selectedColor)
                                        Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    else Modifier
                                )
                                .clickable { selectedColor = hex }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) {
                    nameError = "Name cannot be empty"
                    return@TextButton
                }
                val accepted = onConfirm(name.trim(), applicability, selectedColor)
                if (!accepted) {
                    nameError = "Name already exists"
                } else {
                    onDismiss()
                }
            }) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

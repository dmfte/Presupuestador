package com.tuapp.fintrack.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tuapp.fintrack.R
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
    var nameError by remember { mutableStateOf<Int?>(null) }
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
                    label = { Text(stringResource(R.string.category_name_label)) },
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(stringResource(it), color = MaterialTheme.colorScheme.error) } },
                    singleLine = true
                )

                val applicabilityOptions = CategoryApplicability.entries
                SingleChoiceSegmentedButtonRow {
                    applicabilityOptions.forEachIndexed { idx, type ->
                        SegmentedButton(
                            selected = applicability == type,
                            onClick = { applicability = type },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = idx,
                                count = applicabilityOptions.size
                            ),
                            label = { Text(stringResource(type.labelRes()), style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Text(stringResource(R.string.category_color_label), style = MaterialTheme.typography.labelMedium)
                FlowRow(modifier = Modifier.selectableGroup()) {
                    CATEGORY_COLORS.forEachIndexed { index, hex ->
                        val colorInt = remember(hex) {
                            try { android.graphics.Color.parseColor(hex) }
                            catch (e: Exception) { android.graphics.Color.GRAY }
                        }
                        val selected = hex == selectedColor
                        val label = stringResource(R.string.category_color_option, index + 1)
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .selectable(
                                    selected = selected,
                                    role = Role.RadioButton,
                                    onClick = { selectedColor = hex }
                                )
                                .semantics { contentDescription = label },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color(colorInt), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) {
                    nameError = R.string.category_name_empty
                    return@TextButton
                }
                val accepted = onConfirm(name.trim(), applicability, selectedColor)
                if (!accepted) {
                    nameError = R.string.category_name_exists
                } else {
                    onDismiss()
                }
            }) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

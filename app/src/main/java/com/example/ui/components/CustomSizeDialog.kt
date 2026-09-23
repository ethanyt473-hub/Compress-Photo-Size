package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun CustomSizeDialog(
    initialBytes: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long, String) -> Unit
) {
    var sizeInput by remember {
        val initialKb = initialBytes / 1024L
        mutableStateOf(if (initialKb > 0) initialKb.toString() else "100")
    }
    var selectedUnit by remember { mutableStateOf("KB") } // "KB" or "MB"
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom Target Size") },
        text = {
            Column {
                Text(
                    text = "Specify the maximum file size you need (e.g., for visa, passport, or job portals). Binary units: 1 KB = 1024 bytes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = sizeInput,
                        onValueChange = {
                            sizeInput = it
                            errorMessage = null
                        },
                        label = { Text("Target Size") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        isError = errorMessage != null,
                        supportingText = errorMessage?.let { { Text(it) } },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("custom_size_input")
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(
                            selected = selectedUnit == "KB",
                            onClick = { selectedUnit = "KB" },
                            label = { Text("KB") },
                            modifier = Modifier.testTag("unit_kb_chip")
                        )
                        FilterChip(
                            selected = selectedUnit == "MB",
                            onClick = { selectedUnit = "MB" },
                            label = { Text("MB") },
                            modifier = Modifier.testTag("unit_mb_chip")
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val value = sizeInput.trim().toDoubleOrNull()
                    when {
                        value == null || value <= 0 -> {
                            errorMessage = "Please enter a valid positive number"
                        }
                        selectedUnit == "KB" && value < 5 -> {
                            errorMessage = "Minimum target is 5 KB"
                        }
                        selectedUnit == "MB" && value < 0.01 -> {
                            errorMessage = "Minimum target is 0.01 MB"
                        }
                        selectedUnit == "MB" && value > 100 -> {
                            errorMessage = "Maximum target is 100 MB"
                        }
                        selectedUnit == "KB" && value > 102400 -> {
                            errorMessage = "Maximum target is 100 MB (102,400 KB)"
                        }
                        else -> {
                            val bytes = if (selectedUnit == "MB") {
                                (value * 1024 * 1024).toLong()
                            } else {
                                (value * 1024).toLong()
                            }
                            val label = "$sizeInput $selectedUnit"
                            onConfirm(bytes, label)
                        }
                    }
                },
                modifier = Modifier.testTag("confirm_custom_size_button")
            ) {
                Text("Set Target")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_custom_size_button")
            ) {
                Text("Cancel")
            }
        }
    )
}

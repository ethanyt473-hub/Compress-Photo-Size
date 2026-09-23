package com.example.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

data class SizePreset(val label: String, val bytes: Long)

val PRESET_SIZES = listOf(
    SizePreset("20 KB", 20 * 1024L),
    SizePreset("50 KB", 50 * 1024L),
    SizePreset("100 KB", 100 * 1024L),
    SizePreset("200 KB", 200 * 1024L),
    SizePreset("500 KB", 500 * 1024L),
    SizePreset("1 MB", 1024 * 1024L)
)

@Composable
fun TargetSizeSelector(
    selectedTargetBytes: Long,
    onTargetSelected: (Long) -> Unit,
    onCustomClick: () -> Unit,
    isCustomSelected: Boolean,
    customLabel: String? = null,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PRESET_SIZES.forEach { preset ->
            val isSelected = !isCustomSelected && selectedTargetBytes == preset.bytes
            FilterChip(
                selected = isSelected,
                onClick = { onTargetSelected(preset.bytes) },
                label = {
                    Text(
                        text = preset.label,
                        style = MaterialTheme.typography.labelLarge
                    )
                },
                leadingIcon = if (isSelected) {
                    {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier.testTag("preset_${preset.label.replace(" ", "_")}")
            )
        }

        // Custom Size Chip
        FilterChip(
            selected = isCustomSelected,
            onClick = onCustomClick,
            label = {
                Text(
                    text = customLabel ?: "Custom",
                    style = MaterialTheme.typography.labelLarge
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = if (isCustomSelected) Icons.Default.Check else Icons.Default.Edit,
                    contentDescription = "Custom Size",
                    modifier = Modifier.padding(end = 4.dp)
                )
            },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
            ),
            modifier = Modifier.testTag("preset_custom")
        )
    }
}

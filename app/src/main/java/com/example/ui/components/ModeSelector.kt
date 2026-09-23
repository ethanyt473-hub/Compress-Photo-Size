package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.CompressionMode
import com.example.domain.OutputFormat

@Composable
fun ModeSelector(
    currentMode: CompressionMode,
    onModeChange: (CompressionMode) -> Unit,
    outputFormat: OutputFormat,
    onOutputFormatChange: (OutputFormat) -> Unit,
    targetBytes: Long,
    modifier: Modifier = Modifier
) {
    val selectedTabIndex = when (currentMode) {
        is CompressionMode.TargetSize -> 0
        is CompressionMode.Quality -> 1
        is CompressionMode.Resize -> 2
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("mode_tab_row")
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { onModeChange(CompressionMode.TargetSize(targetBytes)) },
                    text = { Text("Target Size") },
                    icon = { Icon(Icons.Default.Compress, contentDescription = "Target Size") },
                    modifier = Modifier.testTag("tab_target_size")
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { onModeChange(CompressionMode.Quality(80)) },
                    text = { Text("Quality") },
                    icon = { Icon(Icons.Default.Tune, contentDescription = "Quality") },
                    modifier = Modifier.testTag("tab_quality")
                )
                Tab(
                    selected = selectedTabIndex == 2,
                    onClick = { onModeChange(CompressionMode.Resize(scalePercent = 75, preserveAspectRatio = true)) },
                    text = { Text("Resize") },
                    icon = { Icon(Icons.Default.AspectRatio, contentDescription = "Resize") },
                    modifier = Modifier.testTag("tab_resize")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (currentMode) {
                is CompressionMode.TargetSize -> {
                    Text(
                        text = "Automatically finds the highest quality to guarantee output size is <= selected target.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is CompressionMode.Quality -> {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Compression Quality",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "${currentMode.quality}%",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = currentMode.quality.toFloat(),
                            onValueChange = { onModeChange(CompressionMode.Quality(it.toInt())) },
                            valueRange = 1f..100f,
                            steps = 99,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("quality_slider")
                        )
                        Text(
                            text = "Higher quality preserves details but results in a larger file size.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is CompressionMode.Resize -> {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Scale Dimensions",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "${currentMode.scalePercent}%",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = currentMode.scalePercent.toFloat(),
                            onValueChange = {
                                onModeChange(currentMode.copy(scalePercent = it.toInt()))
                            },
                            valueRange = 10f..100f,
                            steps = 90,
                            modifier = Modifier.testTag("resize_scale_slider")
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Preserve Aspect Ratio",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Switch(
                                checked = currentMode.preserveAspectRatio,
                                onCheckedChange = {
                                    onModeChange(currentMode.copy(preserveAspectRatio = it))
                                },
                                modifier = Modifier.testTag("aspect_ratio_switch")
                            )
                        }
                    }
                }
            }
        }
    }
}

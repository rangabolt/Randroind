package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.AutomationConfig
import com.example.model.AutomationPreset
import com.example.model.TriggerEventLog
import com.example.ui.theme.AmberTrigger
import com.example.ui.theme.CyanSensor
import com.example.ui.theme.EmeraldPulse
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun PresetsLogsTab(
    config: AutomationConfig,
    presets: List<AutomationPreset>,
    logs: List<TriggerEventLog>,
    onSavePreset: (String) -> Unit,
    onLoadPreset: (AutomationPreset) -> Unit,
    onDeletePreset: (String) -> Unit,
    onClearLogs: () -> Unit
) {
    var presetName by remember { mutableStateOf("") }
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Save Current Setup Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Save Current Automation Setup",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Watch ${config.watchSizePx}px @ (${config.watchX},${config.watchY}) • Tap @ (${config.triggerX},${config.triggerY}) • ≥ ${config.thresholdPercent.roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = CyanSensor
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = presetName,
                                onValueChange = { presetName = it },
                                label = { Text("Preset name") },
                                placeholder = { Text("e.g. Auto-Accept Prompt") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("preset_name_input")
                            )
                            Button(
                                onClick = {
                                    onSavePreset(presetName.ifBlank { "Preset ${presets.size + 1}" })
                                    presetName = ""
                                },
                                modifier = Modifier
                                    .height(54.dp)
                                    .testTag("save_preset_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = CyanSensor)
                            ) {
                                Icon(Icons.Rounded.BookmarkAdd, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Save")
                            }
                        }
                    }
                }
            }

            // Saved Presets Section
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.Bookmarks, contentDescription = null, tint = CyanSensor)
                    Text(
                        text = "Saved Presets (${presets.size})",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }

            if (presets.isEmpty()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Bookmarks,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(30.dp)
                            )
                            Text(
                                text = "No saved presets yet",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Position your Watch Square and Trigger Button, then save them here for instant reuse.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(presets, key = { it.id }) { preset ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = preset.name,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "${preset.config.detectionMode.title} • ≥${preset.config.thresholdPercent.roundToInt()}% • Tap (${preset.config.triggerX}, ${preset.config.triggerY})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            OutlinedButton(
                                onClick = { onLoadPreset(preset) },
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("load_preset_${preset.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = EmeraldPulse,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Load")
                            }

                            IconButton(
                                onClick = { onDeletePreset(preset.id) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("delete_preset_${preset.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteOutline,
                                    contentDescription = "Delete preset",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            // Event Log Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.History, contentDescription = null, tint = AmberTrigger)
                        Text(
                            text = "Trigger Activity Log",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    if (logs.isNotEmpty()) {
                        OutlinedButton(
                            onClick = onClearLogs,
                            modifier = Modifier
                                .height(42.dp)
                                .testTag("clear_logs_button")
                        ) {
                            Text("Clear")
                        }
                    }
                }
            }

            if (logs.isEmpty()) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = "No trigger events recorded",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Events appear here whenever your Watch Square meets the threshold or you run a Test Tap.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(logs, key = { it.id }) { log ->
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(
                            1.dp,
                            if (log.tapDispatched) EmeraldPulse.copy(alpha = 0.3f)
                            else AmberTrigger.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (log.tapDispatched) Icons.Rounded.CheckCircle
                                else Icons.Rounded.WarningAmber,
                                contentDescription = null,
                                tint = if (log.tapDispatched) EmeraldPulse else AmberTrigger,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = log.note,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "${timeFormatter.format(Date(log.timestampMs))} • Δ ${String.format("%.1f%%", log.measuredChangePercent)} • Tap (${log.tapX}, ${log.tapY})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                color = (if (log.tapDispatched) EmeraldPulse else AmberTrigger).copy(alpha = 0.14f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (log.tapDispatched) "TAPPED" else "LOGGED",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (log.tapDispatched) EmeraldPulse else AmberTrigger,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

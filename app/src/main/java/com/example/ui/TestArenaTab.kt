package com.example.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdsClick
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.model.OverlayRuntimeState
import com.example.ui.theme.AmberTrigger
import com.example.ui.theme.CyanSensor
import com.example.ui.theme.EmeraldPulse
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun TestArenaTab(
    runtimeState: OverlayRuntimeState,
    canDrawOverlays: Boolean,
    onSnapTargets: (watchX: Int, watchY: Int, watchSizePx: Int, triggerX: Int, triggerY: Int) -> Unit,
    onShowOverlay: () -> Unit,
    onToggleWatch: () -> Unit
) {
    var stimulusActive by remember { mutableStateOf(false) }
    var autoPulse by remember { mutableStateOf(false) }
    var pulseProgress by remember { mutableFloatStateOf(0f) }
    var tapsReceived by remember { mutableIntStateOf(0) }
    var tapBurstActive by remember { mutableStateOf(false) }

    var stimulusBounds by remember {
        mutableStateOf(androidx.compose.ui.geometry.Rect.Zero)
    }
    var targetBounds by remember {
        mutableStateOf(androidx.compose.ui.geometry.Rect.Zero)
    }

    LaunchedEffect(autoPulse) {
        if (!autoPulse) {
            pulseProgress = 0f
            return@LaunchedEffect
        }
        while (autoPulse) {
            val steps = 25
            for (i in 1..steps) {
                if (!autoPulse) break
                pulseProgress = i / steps.toFloat()
                delay(100L)
            }
            if (autoPulse) {
                stimulusActive = !stimulusActive
            }
        }
    }

    LaunchedEffect(tapsReceived) {
        if (tapsReceived > 0) {
            tapBurstActive = true
            delay(220L)
            tapBurstActive = false
        }
    }

    val padColor by animateColorAsState(
        targetValue = if (stimulusActive) CyanSensor else Color(0xFF15202B),
        label = "stimulus_color"
    )
    val padTextColor by animateColorAsState(
        targetValue = if (stimulusActive) Color(0xFF06151C) else Color(0xFFF0F6FC),
        label = "stimulus_text"
    )
    val targetScale by animateFloatAsState(
        targetValue = if (tapBurstActive) 0.95f else 1f,
        animationSpec = spring(),
        label = "target_scale"
    )

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
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
                            text = "Live Automation Test Arena",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "Align your Watch Square over the Stimulus Pad below and set the Adjustable Trigger Button on the Tap Receiver to test real-time detection and tapping.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (canDrawOverlays && !runtimeState.overlayVisible) {
                                        onShowOverlay()
                                    }
                                    if (!stimulusBounds.isEmpty && !targetBounds.isEmpty) {
                                        val size = (minOf(stimulusBounds.width, stimulusBounds.height) * 0.72f)
                                            .roundToInt()
                                            .coerceIn(140, 360)
                                        val wx = (stimulusBounds.center.x - size / 2f).roundToInt()
                                        val wy = (stimulusBounds.center.y - size / 2f).roundToInt()
                                        val tx = targetBounds.center.x.roundToInt()
                                        val ty = targetBounds.center.y.roundToInt()
                                        onSnapTargets(wx, wy, size, tx, ty)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("snap_arena_button"),
                                colors = ButtonDefaults.buttonColors(containerColor = CyanSensor)
                            ) {
                                Icon(Icons.Rounded.AutoFixHigh, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Snap Targets Here")
                            }

                            OutlinedButton(
                                onClick = onToggleWatch,
                                enabled = canDrawOverlays,
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("arena_arm_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = EmeraldPulse
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(if (runtimeState.isWatching) "Pause" else "Arm")
                            }
                        }
                    }
                }
            }

            // Visual Stimulus Pad
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, CyanSensor.copy(alpha = 0.4f)),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CenterFocusStrong,
                                    contentDescription = null,
                                    tint = CyanSensor
                                )
                                Text(
                                    text = "1. Watch Square Stimulus Pad",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = CyanSensor
                                )
                            }
                            Text(
                                text = "Δ ${String.format("%.1f%%", runtimeState.currentChangePercent)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = CyanSensor
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(164.dp)
                                .onGloballyPositioned { coords ->
                                    stimulusBounds = coords.boundsInWindow()
                                }
                                .clip(RoundedCornerShape(18.dp))
                                .background(padColor)
                                .clickable { stimulusActive = !stimulusActive }
                                .testTag("stimulus_pad_box"),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Bolt,
                                    contentDescription = null,
                                    tint = padTextColor,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = if (stimulusActive) "STATE B — HIGH CONTRAST" else "STATE A — STANDBY",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = padTextColor
                                )
                                Text(
                                    text = "Tap pad or enable Auto-Pulse to trigger Watch Square",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = padTextColor.copy(alpha = 0.8f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        if (autoPulse) {
                            LinearProgressIndicator(
                                progress = { pulseProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = CyanSensor
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Auto-Pulse Every 2.5s",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Simulates periodic screen updates",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = autoPulse,
                                onCheckedChange = { autoPulse = it },
                                modifier = Modifier.testTag("auto_pulse_switch")
                            )
                        }
                    }
                }
            }

            // Target Tap Receiver Button
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, AmberTrigger.copy(alpha = 0.45f)),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AdsClick,
                                    contentDescription = null,
                                    tint = AmberTrigger
                                )
                                Text(
                                    text = "2. Trigger Target Receiver",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = AmberTrigger
                                )
                            }

                            OutlinedButton(
                                onClick = { tapsReceived = 0 },
                                modifier = Modifier
                                    .height(40.dp)
                                    .testTag("reset_arena_counter_button"),
                                contentPadding = PaddingValues(horizontal = 10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Reset counter",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Reset")
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(132.dp)
                                .scale(targetScale)
                                .onGloballyPositioned { coords ->
                                    targetBounds = coords.boundsInWindow()
                                }
                                .testTag("arena_target_receiver_button"),
                            onClick = { tapsReceived++ },
                            shape = RoundedCornerShape(18.dp),
                            color = if (tapBurstActive) EmeraldPulse.copy(alpha = 0.22f)
                            else AmberTrigger.copy(alpha = 0.14f),
                            border = BorderStroke(
                                2.dp,
                                if (tapBurstActive) EmeraldPulse else AmberTrigger
                            )
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "$tapsReceived",
                                    style = MaterialTheme.typography.displaySmall,
                                    color = if (tapBurstActive) EmeraldPulse else AmberTrigger
                                )
                                Text(
                                    text = "AUTOMATED TAPS RECEIVED",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Drag the Amber Tap Target onto this pad",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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

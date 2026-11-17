package com.example.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.AdsClick
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CropSquare
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.AutomationConfig
import com.example.model.DetectionMode
import com.example.model.OverlayRuntimeState
import com.example.model.PermissionStatus
import com.example.model.SampleRateMode
import com.example.ui.theme.AmberTrigger
import com.example.ui.theme.CyanSensor
import com.example.ui.theme.EmeraldPulse
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ControlTab(
    config: AutomationConfig,
    runtimeState: OverlayRuntimeState,
    permissions: PermissionStatus,
    onLaunchOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
    onToggleWatch: () -> Unit,
    onToggleEdit: () -> Unit,
    onRebaseline: () -> Unit,
    onTestTap: () -> Unit,
    onThresholdChange: (Float) -> Unit,
    onPixelToleranceChange: (Int) -> Unit,
    onCooldownChange: (Long) -> Unit,
    onDetectionModeChange: (DetectionMode) -> Unit,
    onSampleRateChange: (SampleRateMode) -> Unit,
    onOneShotChange: (Boolean) -> Unit,
    onRebaselineAfterTriggerChange: (Boolean) -> Unit,
    onWatchGeometryChange: (x: Int, y: Int, sizePx: Int) -> Unit,
    onTriggerPositionChange: (x: Int, y: Int) -> Unit,
    onRefreshPermissions: () -> Unit
) {
    val context = LocalContext.current

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        onRefreshPermissions()
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Hero Launch & Live Telemetry Card
            item {
                HeroOverlayCard(
                    config = config,
                    runtimeState = runtimeState,
                    canDrawOverlays = permissions.canDrawOverlays,
                    accessibilityEnabled = permissions.accessibilityEnabled,
                    onLaunchOverlay = onLaunchOverlay,
                    onStopOverlay = onStopOverlay,
                    onToggleWatch = onToggleWatch,
                    onToggleEdit = onToggleEdit,
                    onRebaseline = onRebaseline,
                    onTestTap = onTestTap,
                    onOpenOverlaySettings = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    }
                )
            }

            // 2. Permissions Readiness Card
            item {
                PermissionsCard(
                    permissions = permissions,
                    onGrantOverlay = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    },
                    onGrantAccessibility = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onGrantNotifications = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                )
            }

            // 3. Watch Square & Adjustable Trigger Target Geometry
            item {
                GeometryCard(
                    config = config,
                    screenW = runtimeState.screenWidthPx.coerceAtLeast(720),
                    screenH = runtimeState.screenHeightPx.coerceAtLeast(1280),
                    onWatchGeometryChange = onWatchGeometryChange,
                    onTriggerPositionChange = onTriggerPositionChange,
                    onTestTap = onTestTap
                )
            }

            // 4. Detection Threshold & Mode Card
            item {
                DetectionSettingsCard(
                    config = config,
                    onThresholdChange = onThresholdChange,
                    onPixelToleranceChange = onPixelToleranceChange,
                    onCooldownChange = onCooldownChange,
                    onDetectionModeChange = onDetectionModeChange,
                    onSampleRateChange = onSampleRateChange,
                    onOneShotChange = onOneShotChange,
                    onRebaselineAfterTriggerChange = onRebaselineAfterTriggerChange
                )
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun HeroOverlayCard(
    config: AutomationConfig,
    runtimeState: OverlayRuntimeState,
    canDrawOverlays: Boolean,
    accessibilityEnabled: Boolean,
    onLaunchOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
    onToggleWatch: () -> Unit,
    onToggleEdit: () -> Unit,
    onRebaseline: () -> Unit,
    onTestTap: () -> Unit,
    onOpenOverlaySettings: () -> Unit
) {
    val accentColor by animateColorAsState(
        targetValue = when {
            runtimeState.isWatching -> EmeraldPulse
            runtimeState.overlayVisible -> CyanSensor
            else -> MaterialTheme.colorScheme.primary
        },
        label = "hero_accent"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.14f),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.85f, size.height * 0.12f),
                            radius = size.width * 0.75f
                        )
                    )
                }
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (runtimeState.isWatching) "REAL-TIME MONITOR ARMED"
                        else if (runtimeState.overlayVisible) "OVERLAY ACTIVE ON SCREEN"
                        else "OVERLAY HUD STANDBY",
                        style = MaterialTheme.typography.labelMedium,
                        color = accentColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = runtimeState.statusMessage,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    color = accentColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "${runtimeState.totalTriggers}",
                            style = MaterialTheme.typography.headlineSmall,
                            color = accentColor
                        )
                        Text(
                            text = "TAPS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Live Visual Change Delta Bar
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Watch Square Delta",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format(
                            "%.1f%%  (Trigger ≥ %.0f%%)",
                            runtimeState.currentChangePercent,
                            config.thresholdPercent
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (runtimeState.currentChangePercent >= config.thresholdPercent) {
                            EmeraldPulse
                        } else {
                            CyanSensor
                        }
                    )
                }

                ThresholdMeterBar(
                    currentPercent = runtimeState.currentChangePercent,
                    thresholdPercent = config.thresholdPercent
                )
            }

            if (!accessibilityEnabled && runtimeState.overlayVisible) {
                Surface(
                    color = AmberTrigger.copy(alpha = 0.12f),
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, AmberTrigger.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = AmberTrigger,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Accessibility service is off — threshold detections will log, but automated taps require enabling tapatepa Accessibility.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Primary Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!canDrawOverlays) {
                    Button(
                        onClick = onOpenOverlaySettings,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("grant_overlay_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanSensor)
                    ) {
                        Icon(Icons.Rounded.Layers, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Allow Overlay Permission")
                    }
                } else if (!runtimeState.overlayVisible) {
                    Button(
                        onClick = onLaunchOverlay,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("launch_overlay_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanSensor)
                    ) {
                        Icon(Icons.Rounded.Visibility, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Show Overlay HUD")
                    }
                } else {
                    Button(
                        onClick = onToggleWatch,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("arm_watch_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (runtimeState.isWatching) AmberTrigger else EmeraldPulse
                        )
                    ) {
                        Icon(
                            imageVector = if (runtimeState.isWatching) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (runtimeState.isWatching) "Pause Watch" else "Arm & Watch")
                    }

                    OutlinedButton(
                        onClick = onStopOverlay,
                        modifier = Modifier
                            .height(50.dp)
                            .testTag("hide_overlay_button"),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VisibilityOff,
                            contentDescription = "Hide overlay",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Close", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            AnimatedVisibility(visible = runtimeState.overlayVisible) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onToggleEdit,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("toggle_edit_mode_button")
                    ) {
                        Icon(
                            imageVector = if (runtimeState.editMode) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (runtimeState.editMode) CyanSensor else AmberTrigger
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (runtimeState.editMode) "Editing" else "Locked")
                    }

                    OutlinedButton(
                        onClick = onRebaseline,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("rebaseline_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Baseline")
                    }

                    OutlinedButton(
                        onClick = onTestTap,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("test_tap_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AdsClick,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = AmberTrigger
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Test Tap")
                    }
                }
            }
        }
    }
}

@Composable
private fun ThresholdMeterBar(
    currentPercent: Float,
    thresholdPercent: Float
) {
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val fillColor = if (currentPercent >= thresholdPercent) EmeraldPulse else CyanSensor
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(trackColor)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val fillWidth = (size.width * (currentPercent / 100f).coerceIn(0f, 1f))
            if (fillWidth > 0f) {
                drawRoundRect(
                    color = fillColor,
                    topLeft = Offset.Zero,
                    size = Size(fillWidth, size.height),
                    cornerRadius = CornerRadius(7.dp.toPx(), 7.dp.toPx())
                )
            }
            val markerX = (size.width * (thresholdPercent / 100f).coerceIn(0.01f, 0.99f))
            drawLine(
                color = AmberTrigger,
                start = Offset(markerX, 0f),
                end = Offset(markerX, size.height),
                strokeWidth = 3.dp.toPx()
            )
        }
    }
}

@Composable
private fun PermissionsCard(
    permissions: PermissionStatus,
    onGrantOverlay: () -> Unit,
    onGrantAccessibility: () -> Unit,
    onGrantNotifications: () -> Unit
) {
    val allCoreReady = permissions.canDrawOverlays && permissions.accessibilityEnabled
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "System Permissions",
                    style = MaterialTheme.typography.titleLarge
                )
                Surface(
                    color = if (allCoreReady) EmeraldPulse.copy(alpha = 0.14f)
                    else AmberTrigger.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (allCoreReady) "READY" else "SETUP NEEDED",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (allCoreReady) EmeraldPulse else AmberTrigger,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            PermissionRow(
                icon = Icons.Rounded.Layers,
                title = "Display Over Other Apps",
                subtitle = "Draws the adjustable watch square & tap trigger button",
                granted = permissions.canDrawOverlays,
                tag = "permission_overlay_button",
                onClick = onGrantOverlay
            )

            PermissionRow(
                icon = Icons.Rounded.AccessibilityNew,
                title = "Accessibility Tap Service",
                subtitle = "Performs taps at your adjustable trigger coordinates",
                granted = permissions.accessibilityEnabled,
                tag = "permission_accessibility_button",
                onClick = onGrantAccessibility
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                PermissionRow(
                    icon = Icons.Rounded.NotificationsActive,
                    title = "Monitor Notification",
                    subtitle = "Keeps screen watching active in background",
                    granted = permissions.notificationsGranted,
                    tag = "permission_notification_button",
                    onClick = onGrantNotifications
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    granted: Boolean,
    tag: String,
    onClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(
            1.dp,
            if (granted) EmeraldPulse.copy(alpha = 0.25f) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(
                        if (granted) EmeraldPulse.copy(alpha = 0.15f)
                        else AmberTrigger.copy(alpha = 0.15f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (granted) Icons.Rounded.CheckCircle else icon,
                    contentDescription = null,
                    tint = if (granted) EmeraldPulse else AmberTrigger,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedButton(
                onClick = onClick,
                modifier = Modifier
                    .height(48.dp)
                    .testTag(tag),
                contentPadding = PaddingValues(horizontal = 12.dp)
            ) {
                Text(if (granted) "Manage" else "Enable")
            }
        }
    }
}

@Composable
private fun GeometryCard(
    config: AutomationConfig,
    screenW: Int,
    screenH: Int,
    onWatchGeometryChange: (x: Int, y: Int, sizePx: Int) -> Unit,
    onTriggerPositionChange: (x: Int, y: Int) -> Unit,
    onTestTap: () -> Unit
) {
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Square & Trigger Target",
                style = MaterialTheme.typography.titleLarge
            )

            // Watch Square Size & Position
            Surface(
                color = CyanSensor.copy(alpha = 0.08f),
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, CyanSensor.copy(alpha = 0.25f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
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
                                imageVector = Icons.Rounded.CropSquare,
                                contentDescription = null,
                                tint = CyanSensor,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Watch Square",
                                style = MaterialTheme.typography.titleMedium,
                                color = CyanSensor
                            )
                        }
                        Text(
                            text = "${config.watchSizePx}×${config.watchSizePx} px  @ (${config.watchX}, ${config.watchY})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = "Square Size (${config.watchSizePx} px) — or drag corner handle on overlay",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = config.watchSizePx.toFloat(),
                        onValueChange = { newSize ->
                            onWatchGeometryChange(config.watchX, config.watchY, newSize.roundToInt())
                        },
                        valueRange = 120f..(minOf(screenW, screenH) * 0.75f).coerceAtLeast(300f),
                        modifier = Modifier.testTag("watch_size_slider"),
                        colors = SliderDefaults.colors(
                            thumbColor = CyanSensor,
                            activeTrackColor = CyanSensor
                        )
                    )
                }
            }

            // Adjustable Trigger Target Coordinates
            Surface(
                color = AmberTrigger.copy(alpha = 0.08f),
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(1.dp, AmberTrigger.copy(alpha = 0.28f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
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
                                tint = AmberTrigger,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Trigger Target Point",
                                style = MaterialTheme.typography.titleMedium,
                                color = AmberTrigger
                            )
                        }
                        Text(
                            text = "X: ${config.triggerX}  Y: ${config.triggerY}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Text(
                        text = "Fine-tune X/Y or drag the amber crosshair anywhere on screen",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "X",
                            style = MaterialTheme.typography.labelMedium,
                            color = AmberTrigger
                        )
                        Slider(
                            value = config.triggerX.toFloat().coerceIn(0f, screenW.toFloat()),
                            onValueChange = { onTriggerPositionChange(it.roundToInt(), config.triggerY) },
                            valueRange = 0f..screenW.toFloat(),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("trigger_x_slider"),
                            colors = SliderDefaults.colors(
                                thumbColor = AmberTrigger,
                                activeTrackColor = AmberTrigger
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Y",
                            style = MaterialTheme.typography.labelMedium,
                            color = AmberTrigger
                        )
                        Slider(
                            value = config.triggerY.toFloat().coerceIn(0f, screenH.toFloat()),
                            onValueChange = { onTriggerPositionChange(config.triggerX, it.roundToInt()) },
                            valueRange = 0f..screenH.toFloat(),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("trigger_y_slider"),
                            colors = SliderDefaults.colors(
                                thumbColor = AmberTrigger,
                                activeTrackColor = AmberTrigger
                            )
                        )
                    }

                    OutlinedButton(
                        onClick = onTestTap,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("geometry_test_tap_button"),
                        border = BorderStroke(1.dp, AmberTrigger.copy(alpha = 0.5f))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AdsClick,
                            contentDescription = null,
                            tint = AmberTrigger
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Fire Test Tap at (${config.triggerX}, ${config.triggerY})", color = AmberTrigger)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetectionSettingsCard(
    config: AutomationConfig,
    onThresholdChange: (Float) -> Unit,
    onPixelToleranceChange: (Int) -> Unit,
    onCooldownChange: (Long) -> Unit,
    onDetectionModeChange: (DetectionMode) -> Unit,
    onSampleRateChange: (SampleRateMode) -> Unit,
    onOneShotChange: (Boolean) -> Unit,
    onRebaselineAfterTriggerChange: (Boolean) -> Unit
) {
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = null,
                    tint = CyanSensor
                )
                Text(
                    text = "Visual Trigger Rules",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            // Threshold slider
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Visual Change Threshold",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${config.thresholdPercent.roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = CyanSensor
                    )
                }
                Text(
                    text = "Tap triggers when at least ${config.thresholdPercent.roundToInt()}% of watched pixels shift",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = config.thresholdPercent,
                    onValueChange = onThresholdChange,
                    valueRange = 1f..95f,
                    modifier = Modifier.testTag("threshold_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = CyanSensor,
                        activeTrackColor = CyanSensor
                    )
                )
            }

            // Detection Mode Chips
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Detection Mode",
                    style = MaterialTheme.typography.titleMedium
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DetectionMode.entries.forEach { mode ->
                        val selected = config.detectionMode == mode
                        FilterChip(
                            selected = selected,
                            onClick = { onDetectionModeChange(mode) },
                            label = { Text(mode.title) },
                            modifier = Modifier.testTag("mode_${mode.name.lowercase()}"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyanSensor.copy(alpha = 0.2f),
                                selectedLabelColor = CyanSensor
                            )
                        )
                    }
                }
                Text(
                    text = config.detectionMode.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Pixel Sensitivity Tolerance
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Color Noise Tolerance",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Δ ≥ ${config.pixelTolerance}/255",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Slider(
                    value = config.pixelTolerance.toFloat(),
                    onValueChange = { onPixelToleranceChange(it.roundToInt()) },
                    valueRange = 5f..80f,
                    modifier = Modifier.testTag("tolerance_slider")
                )
            }

            // Cooldown Delay
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Tap Cooldown",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${config.cooldownMs} ms",
                        style = MaterialTheme.typography.labelMedium,
                        color = AmberTrigger
                    )
                }
                Slider(
                    value = config.cooldownMs.toFloat(),
                    onValueChange = { onCooldownChange((it / 50f).roundToInt() * 50L) },
                    valueRange = 150f..5000f,
                    modifier = Modifier.testTag("cooldown_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = AmberTrigger,
                        activeTrackColor = AmberTrigger
                    )
                )
            }

            // Sample Rate
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = "Scan Cadence", style = MaterialTheme.typography.titleMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SampleRateMode.entries.forEach { rate ->
                        FilterChip(
                            selected = config.sampleRateMode == rate,
                            onClick = { onSampleRateChange(rate) },
                            label = { Text(rate.label) },
                            modifier = Modifier.testTag("sample_${rate.name.lowercase()}")
                        )
                    }
                }
            }

            // Switches
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Pause After Single Tap (One-Shot)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "When on, taps once on change and pauses. When off, taps once per visual change and waits to settle.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = config.oneShotMode,
                    onCheckedChange = onOneShotChange,
                    modifier = Modifier.testTag("one_shot_switch"),
                    colors = SwitchDefaults.colors(checkedThumbColor = EmeraldPulse)
                )
            }
        }
    }
}

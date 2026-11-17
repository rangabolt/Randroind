package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.CyanSensor
import com.example.ui.theme.EmeraldPulse

@Composable
fun MainScreen(viewModel: MainViewModel) {
    val config by viewModel.config.collectAsStateWithLifecycle()
    val runtimeState by viewModel.runtimeState.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val presets by viewModel.presets.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "tapatepa",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Visual Watch Square & Auto-Tap",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    val badgeColor = when {
                        runtimeState.isWatching -> EmeraldPulse
                        runtimeState.overlayVisible -> CyanSensor
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Surface(
                        color = badgeColor.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(100.dp),
                        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(badgeColor)
                            )
                            Text(
                                text = when {
                                    runtimeState.isWatching -> "WATCHING"
                                    runtimeState.overlayVisible -> if (runtimeState.editMode) "EDIT HUD" else "LOCKED"
                                    else -> "IDLE"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = badgeColor
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                windowInsets = WindowInsets.navigationBars
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { viewModel.selectTab(0) },
                    icon = { Icon(Icons.Rounded.Tune, contentDescription = "Control") },
                    label = { Text("Control") },
                    modifier = Modifier.testTag("nav_control_tab"),
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CyanSensor,
                        selectedTextColor = CyanSensor,
                        indicatorColor = CyanSensor.copy(alpha = 0.16f)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { viewModel.selectTab(1) },
                    icon = { Icon(Icons.Rounded.Science, contentDescription = "Test Arena") },
                    label = { Text("Test Arena") },
                    modifier = Modifier.testTag("nav_arena_tab"),
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CyanSensor,
                        selectedTextColor = CyanSensor,
                        indicatorColor = CyanSensor.copy(alpha = 0.16f)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { viewModel.selectTab(2) },
                    icon = { Icon(Icons.Rounded.Bookmarks, contentDescription = "Presets and Logs") },
                    label = { Text("Presets & Log") },
                    modifier = Modifier.testTag("nav_presets_tab"),
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = CyanSensor,
                        selectedTextColor = CyanSensor,
                        indicatorColor = CyanSensor.copy(alpha = 0.16f)
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> ControlTab(
                    config = config,
                    runtimeState = runtimeState,
                    permissions = permissions,
                    onLaunchOverlay = viewModel::showOverlay,
                    onStopOverlay = viewModel::stopOverlay,
                    onToggleWatch = viewModel::toggleWatch,
                    onToggleEdit = viewModel::toggleEditMode,
                    onRebaseline = viewModel::rebaseline,
                    onTestTap = viewModel::testTap,
                    onThresholdChange = viewModel::updateThreshold,
                    onPixelToleranceChange = viewModel::updatePixelTolerance,
                    onCooldownChange = viewModel::updateCooldown,
                    onDetectionModeChange = viewModel::updateDetectionMode,
                    onSampleRateChange = viewModel::updateSampleRate,
                    onOneShotChange = viewModel::updateOneShotMode,
                    onRebaselineAfterTriggerChange = viewModel::updateRebaselineAfterTrigger,
                    onWatchGeometryChange = viewModel::updateWatchGeometry,
                    onTriggerPositionChange = viewModel::updateTriggerPosition,
                    onRefreshPermissions = viewModel::refreshPermissions
                )

                1 -> TestArenaTab(
                    runtimeState = runtimeState,
                    canDrawOverlays = permissions.canDrawOverlays,
                    onSnapTargets = viewModel::snapTargetsToArena,
                    onShowOverlay = viewModel::showOverlay,
                    onToggleWatch = viewModel::toggleWatch
                )

                else -> PresetsLogsTab(
                    config = config,
                    presets = presets,
                    logs = logs,
                    onSavePreset = viewModel::savePreset,
                    onLoadPreset = viewModel::loadPreset,
                    onDeletePreset = viewModel::deletePreset,
                    onClearLogs = viewModel::clearLogs
                )
            }
        }
    }
}

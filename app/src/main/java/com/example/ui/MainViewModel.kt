package com.example.ui

import android.app.Application
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.PixelTapRepository
import com.example.model.AutomationConfig
import com.example.model.AutomationPreset
import com.example.model.DetectionMode
import com.example.model.OverlayRuntimeState
import com.example.model.PermissionStatus
import com.example.model.SampleRateMode
import com.example.model.TriggerEventLog
import com.example.service.ScreenMonitorService
import com.example.service.TapAccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PixelTapRepository.getInstance(application)

    val config: StateFlow<AutomationConfig> = repository.config
    val runtimeState: StateFlow<OverlayRuntimeState> = repository.runtimeState
    val logs: StateFlow<List<TriggerEventLog>> = repository.logs
    val presets: StateFlow<List<AutomationPreset>> = repository.presetsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _permissions = MutableStateFlow(readPermissions(application))
    val permissions: StateFlow<PermissionStatus> = combine(
        _permissions,
        repository.accessibilityConnected,
        repository.runtimeState
    ) { base, accConnected, rt ->
        base.copy(
            accessibilityEnabled = accConnected || TapAccessibilityService.isServiceEnabled(getApplication()),
            projectionReady = rt.hasProjectionSession
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _permissions.value)

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    fun selectTab(index: Int) {
        _selectedTab.value = index
    }

    fun refreshPermissions() {
        _permissions.value = readPermissions(getApplication())
    }

    private fun readPermissions(context: Context): PermissionStatus {
        val overlay = Settings.canDrawOverlays(context)
        val accessibility = TapAccessibilityService.isServiceEnabled(context)
        val notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        } else {
            true
        }
        return PermissionStatus(
            canDrawOverlays = overlay,
            accessibilityEnabled = accessibility,
            notificationsGranted = notifications,
            projectionReady = repository.runtimeState.value.hasProjectionSession
        )
    }

    fun showOverlay() {
        ScreenMonitorService.sendAction(getApplication(), ScreenMonitorService.ACTION_SHOW_OVERLAY)
    }

    fun stopOverlay() {
        ScreenMonitorService.sendAction(getApplication(), ScreenMonitorService.ACTION_STOP_OVERLAY)
    }

    fun toggleWatch() {
        ScreenMonitorService.sendAction(getApplication(), ScreenMonitorService.ACTION_TOGGLE_WATCH)
    }

    fun toggleEditMode() {
        if (runtimeState.value.overlayVisible) {
            ScreenMonitorService.sendAction(getApplication(), ScreenMonitorService.ACTION_TOGGLE_EDIT)
        }
    }

    fun rebaseline() {
        if (runtimeState.value.overlayVisible) {
            ScreenMonitorService.sendAction(getApplication(), ScreenMonitorService.ACTION_REBASELINE)
        }
    }

    fun testTap() {
        if (runtimeState.value.overlayVisible) {
            ScreenMonitorService.sendAction(getApplication(), ScreenMonitorService.ACTION_TEST_TAP)
        } else {
            val cfg = config.value
            TapAccessibilityService.instance?.performTap(
                cfg.triggerX.toFloat(),
                cfg.triggerY.toFloat(),
                cfg.tapDurationMs
            )
        }
    }

    fun updateThreshold(percent: Float) {
        updateAndSync { it.copy(thresholdPercent = percent.coerceIn(1f, 95f)) }
    }

    fun updatePixelTolerance(tolerance: Int) {
        updateAndSync { it.copy(pixelTolerance = tolerance.coerceIn(5, 90)) }
    }

    fun updateCooldown(cooldownMs: Long) {
        updateAndSync { it.copy(cooldownMs = cooldownMs.coerceIn(150L, 5000L)) }
    }

    fun updateDetectionMode(mode: DetectionMode) {
        updateAndSync { it.copy(detectionMode = mode) }
    }

    fun updateSampleRate(mode: SampleRateMode) {
        updateAndSync { it.copy(sampleRateMode = mode) }
    }

    fun updateOneShotMode(enabled: Boolean) {
        updateAndSync { it.copy(oneShotMode = enabled) }
    }

    fun updateRebaselineAfterTrigger(enabled: Boolean) {
        updateAndSync { it.copy(rebaselineAfterTrigger = enabled) }
    }

    fun updateWatchGeometry(x: Int, y: Int, sizePx: Int) {
        updateAndSync {
            it.copy(
                watchX = x.coerceAtLeast(0),
                watchY = y.coerceAtLeast(0),
                watchSizePx = sizePx.coerceIn(120, 900)
            )
        }
    }

    fun updateTriggerPosition(x: Int, y: Int) {
        updateAndSync {
            it.copy(
                triggerX = x.coerceAtLeast(0),
                triggerY = y.coerceAtLeast(0)
            )
        }
    }

    fun snapTargetsToArena(
        watchX: Int,
        watchY: Int,
        watchSizePx: Int,
        triggerX: Int,
        triggerY: Int
    ) {
        updateAndSync {
            it.copy(
                watchX = watchX.coerceAtLeast(0),
                watchY = watchY.coerceAtLeast(0),
                watchSizePx = watchSizePx.coerceIn(120, 900),
                triggerX = triggerX.coerceAtLeast(0),
                triggerY = triggerY.coerceAtLeast(0)
            )
        }
    }

    fun savePreset(name: String) {
        viewModelScope.launch {
            repository.savePreset(name)
        }
    }

    fun loadPreset(preset: AutomationPreset) {
        repository.applyConfig(preset.config)
        if (runtimeState.value.overlayVisible) {
            ScreenMonitorService.sendAction(getApplication(), ScreenMonitorService.ACTION_SYNC_CONFIG)
        }
    }

    fun deletePreset(id: String) {
        viewModelScope.launch {
            repository.deletePreset(id)
        }
    }

    fun clearLogs() {
        repository.clearLogs()
    }

    private fun updateAndSync(transform: (AutomationConfig) -> AutomationConfig) {
        repository.updateConfig(transform)
        if (runtimeState.value.overlayVisible) {
            ScreenMonitorService.sendAction(getApplication(), ScreenMonitorService.ACTION_SYNC_CONFIG)
        }
    }
}

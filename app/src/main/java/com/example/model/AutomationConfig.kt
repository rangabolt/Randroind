package com.example.model

enum class DetectionMode(
    val title: String,
    val subtitle: String
) {
    BASELINE_DIFF(
        title = "Reference Delta",
        subtitle = "Compare watch square against an initial captured baseline"
    ),
    FRAME_MOTION(
        title = "Frame Motion",
        subtitle = "Detect live movement or flashes between consecutive frames"
    ),
    BRIGHTNESS_SHIFT(
        title = "Luminance Shift",
        subtitle = "Trigger when overall brightness in the square rises or drops"
    )
}

enum class SampleRateMode(
    val label: String,
    val intervalMs: Long
) {
    FAST("Fast (100ms)", 100L),
    BALANCED("Balanced (200ms)", 200L),
    ECO("Eco (450ms)", 450L)
}

data class AutomationConfig(
    val watchX: Int = 180,
    val watchY: Int = 460,
    val watchSizePx: Int = 220,
    val triggerX: Int = 540,
    val triggerY: Int = 1320,
    val thresholdPercent: Float = 15f,
    val pixelTolerance: Int = 26,
    val cooldownMs: Long = 800L,
    val tapDurationMs: Long = 60L,
    val detectionMode: DetectionMode = DetectionMode.BASELINE_DIFF,
    val sampleRateMode: SampleRateMode = SampleRateMode.BALANCED,
    val oneShotMode: Boolean = false,
    val rebaselineAfterTrigger: Boolean = true
)

data class AutomationPreset(
    val id: String,
    val name: String,
    val config: AutomationConfig,
    val createdAtMs: Long
)

data class TriggerEventLog(
    val id: Long,
    val timestampMs: Long,
    val measuredChangePercent: Float,
    val thresholdPercent: Float,
    val tapX: Int,
    val tapY: Int,
    val tapDispatched: Boolean,
    val note: String
)

data class PermissionStatus(
    val canDrawOverlays: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val notificationsGranted: Boolean = true,
    val projectionReady: Boolean = false
)

data class OverlayRuntimeState(
    val overlayVisible: Boolean = false,
    val editMode: Boolean = true,
    val isWatching: Boolean = false,
    val hasProjectionSession: Boolean = false,
    val currentChangePercent: Float = 0f,
    val totalTriggers: Int = 0,
    val lastTriggerTimestampMs: Long = 0L,
    val statusMessage: String = "Overlay idle",
    val screenWidthPx: Int = 1080,
    val screenHeightPx: Int = 2400
)

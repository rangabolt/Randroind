package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AutomationConfig
import com.example.model.AutomationPreset
import com.example.model.DetectionMode
import com.example.model.OverlayRuntimeState
import com.example.model.SampleRateMode
import com.example.model.TriggerEventLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.pixelTapDataStore: DataStore<Preferences> by preferencesDataStore(name = "pixeltap_prefs")

class PixelTapRepository private constructor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private object Keys {
        val WATCH_X = intPreferencesKey("watch_x")
        val WATCH_Y = intPreferencesKey("watch_y")
        val WATCH_SIZE = intPreferencesKey("watch_size")
        val TRIGGER_X = intPreferencesKey("trigger_x")
        val TRIGGER_Y = intPreferencesKey("trigger_y")
        val THRESHOLD_PERCENT = floatPreferencesKey("threshold_percent")
        val PIXEL_TOLERANCE = intPreferencesKey("pixel_tolerance")
        val COOLDOWN_MS = longPreferencesKey("cooldown_ms")
        val TAP_DURATION_MS = longPreferencesKey("tap_duration_ms")
        val DETECTION_MODE = stringPreferencesKey("detection_mode")
        val SAMPLE_RATE_MODE = stringPreferencesKey("sample_rate_mode")
        val ONE_SHOT_MODE = booleanPreferencesKey("one_shot_mode")
        val REBASELINE_AFTER_TRIGGER = booleanPreferencesKey("rebaseline_after_trigger")
        val PRESETS_JSON = stringPreferencesKey("presets_json")
    }

    private val _config = MutableStateFlow(AutomationConfig())
    val config: StateFlow<AutomationConfig> = _config.asStateFlow()

    private val _runtimeState = MutableStateFlow(OverlayRuntimeState())
    val runtimeState: StateFlow<OverlayRuntimeState> = _runtimeState.asStateFlow()

    private val _accessibilityConnected = MutableStateFlow(false)
    val accessibilityConnected: StateFlow<Boolean> = _accessibilityConnected.asStateFlow()

    private val _logs = MutableStateFlow<List<TriggerEventLog>>(emptyList())
    val logs: StateFlow<List<TriggerEventLog>> = _logs.asStateFlow()

    val presetsFlow: Flow<List<AutomationPreset>> = context.pixelTapDataStore.data.map { prefs ->
        decodePresets(prefs[Keys.PRESETS_JSON])
    }

    init {
        scope.launch {
            val prefs = context.pixelTapDataStore.data.first()
            _config.value = configFromPreferences(prefs)
        }
    }

    fun updateConfig(transform: (AutomationConfig) -> AutomationConfig) {
        val updated = transform(_config.value)
        _config.value = updated
        scope.launch {
            persistConfig(updated)
        }
    }

    fun applyConfig(newConfig: AutomationConfig) {
        _config.value = newConfig
        scope.launch {
            persistConfig(newConfig)
        }
    }

    fun updateRuntimeState(transform: (OverlayRuntimeState) -> OverlayRuntimeState) {
        _runtimeState.update(transform)
    }

    fun setAccessibilityConnected(connected: Boolean) {
        _accessibilityConnected.value = connected
    }

    fun appendLog(entry: TriggerEventLog) {
        _logs.update { current ->
            (listOf(entry) + current).take(50)
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    suspend fun savePreset(name: String) {
        val trimmed = name.trim().ifEmpty { "Automation Preset" }
        val newPreset = AutomationPreset(
            id = UUID.randomUUID().toString(),
            name = trimmed,
            config = _config.value,
            createdAtMs = System.currentTimeMillis()
        )
        context.pixelTapDataStore.edit { prefs ->
            val current = decodePresets(prefs[Keys.PRESETS_JSON]).toMutableList()
            current.add(0, newPreset)
            prefs[Keys.PRESETS_JSON] = encodePresets(current)
        }
    }

    suspend fun deletePreset(id: String) {
        context.pixelTapDataStore.edit { prefs ->
            val current = decodePresets(prefs[Keys.PRESETS_JSON]).filterNot { it.id == id }
            prefs[Keys.PRESETS_JSON] = encodePresets(current)
        }
    }

    private suspend fun persistConfig(cfg: AutomationConfig) {
        context.pixelTapDataStore.edit { prefs ->
            prefs[Keys.WATCH_X] = cfg.watchX
            prefs[Keys.WATCH_Y] = cfg.watchY
            prefs[Keys.WATCH_SIZE] = cfg.watchSizePx
            prefs[Keys.TRIGGER_X] = cfg.triggerX
            prefs[Keys.TRIGGER_Y] = cfg.triggerY
            prefs[Keys.THRESHOLD_PERCENT] = cfg.thresholdPercent
            prefs[Keys.PIXEL_TOLERANCE] = cfg.pixelTolerance
            prefs[Keys.COOLDOWN_MS] = cfg.cooldownMs
            prefs[Keys.TAP_DURATION_MS] = cfg.tapDurationMs
            prefs[Keys.DETECTION_MODE] = cfg.detectionMode.name
            prefs[Keys.SAMPLE_RATE_MODE] = cfg.sampleRateMode.name
            prefs[Keys.ONE_SHOT_MODE] = cfg.oneShotMode
            prefs[Keys.REBASELINE_AFTER_TRIGGER] = cfg.rebaselineAfterTrigger
        }
    }

    private fun configFromPreferences(prefs: Preferences): AutomationConfig {
        val defaults = AutomationConfig()
        val detectionMode = prefs[Keys.DETECTION_MODE]?.let {
            runCatching { DetectionMode.valueOf(it) }.getOrNull()
        } ?: defaults.detectionMode
        val sampleRate = prefs[Keys.SAMPLE_RATE_MODE]?.let {
            runCatching { SampleRateMode.valueOf(it) }.getOrNull()
        } ?: defaults.sampleRateMode

        return AutomationConfig(
            watchX = prefs[Keys.WATCH_X] ?: defaults.watchX,
            watchY = prefs[Keys.WATCH_Y] ?: defaults.watchY,
            watchSizePx = prefs[Keys.WATCH_SIZE] ?: defaults.watchSizePx,
            triggerX = prefs[Keys.TRIGGER_X] ?: defaults.triggerX,
            triggerY = prefs[Keys.TRIGGER_Y] ?: defaults.triggerY,
            thresholdPercent = prefs[Keys.THRESHOLD_PERCENT] ?: defaults.thresholdPercent,
            pixelTolerance = prefs[Keys.PIXEL_TOLERANCE] ?: defaults.pixelTolerance,
            cooldownMs = prefs[Keys.COOLDOWN_MS] ?: defaults.cooldownMs,
            tapDurationMs = prefs[Keys.TAP_DURATION_MS] ?: defaults.tapDurationMs,
            detectionMode = detectionMode,
            sampleRateMode = sampleRate,
            oneShotMode = prefs[Keys.ONE_SHOT_MODE] ?: defaults.oneShotMode,
            rebaselineAfterTrigger = prefs[Keys.REBASELINE_AFTER_TRIGGER]
                ?: defaults.rebaselineAfterTrigger
        )
    }

    private fun encodePresets(list: List<AutomationPreset>): String {
        val arr = JSONArray()
        list.forEach { preset ->
            val cfgObj = JSONObject().apply {
                put("watchX", preset.config.watchX)
                put("watchY", preset.config.watchY)
                put("watchSizePx", preset.config.watchSizePx)
                put("triggerX", preset.config.triggerX)
                put("triggerY", preset.config.triggerY)
                put("thresholdPercent", preset.config.thresholdPercent.toDouble())
                put("pixelTolerance", preset.config.pixelTolerance)
                put("cooldownMs", preset.config.cooldownMs)
                put("tapDurationMs", preset.config.tapDurationMs)
                put("detectionMode", preset.config.detectionMode.name)
                put("sampleRateMode", preset.config.sampleRateMode.name)
                put("oneShotMode", preset.config.oneShotMode)
                put("rebaselineAfterTrigger", preset.config.rebaselineAfterTrigger)
            }
            val obj = JSONObject().apply {
                put("id", preset.id)
                put("name", preset.name)
                put("createdAtMs", preset.createdAtMs)
                put("config", cfgObj)
            }
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun decodePresets(json: String?): List<AutomationPreset> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val cfgObj = obj.getJSONObject("config")
                    val defaults = AutomationConfig()
                    val cfg = AutomationConfig(
                        watchX = cfgObj.optInt("watchX", defaults.watchX),
                        watchY = cfgObj.optInt("watchY", defaults.watchY),
                        watchSizePx = cfgObj.optInt("watchSizePx", defaults.watchSizePx),
                        triggerX = cfgObj.optInt("triggerX", defaults.triggerX),
                        triggerY = cfgObj.optInt("triggerY", defaults.triggerY),
                        thresholdPercent = cfgObj.optDouble(
                            "thresholdPercent",
                            defaults.thresholdPercent.toDouble()
                        ).toFloat(),
                        pixelTolerance = cfgObj.optInt("pixelTolerance", defaults.pixelTolerance),
                        cooldownMs = cfgObj.optLong("cooldownMs", defaults.cooldownMs),
                        tapDurationMs = cfgObj.optLong("tapDurationMs", defaults.tapDurationMs),
                        detectionMode = runCatching {
                            DetectionMode.valueOf(cfgObj.optString("detectionMode"))
                        }.getOrDefault(defaults.detectionMode),
                        sampleRateMode = runCatching {
                            SampleRateMode.valueOf(cfgObj.optString("sampleRateMode"))
                        }.getOrDefault(defaults.sampleRateMode),
                        oneShotMode = cfgObj.optBoolean("oneShotMode", defaults.oneShotMode),
                        rebaselineAfterTrigger = cfgObj.optBoolean(
                            "rebaselineAfterTrigger",
                            defaults.rebaselineAfterTrigger
                        )
                    )
                    add(
                        AutomationPreset(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            config = cfg,
                            createdAtMs = obj.optLong("createdAtMs", 0L)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        @Volatile
        private var instance: PixelTapRepository? = null

        fun getInstance(context: Context): PixelTapRepository {
            return instance ?: synchronized(this) {
                instance ?: PixelTapRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}

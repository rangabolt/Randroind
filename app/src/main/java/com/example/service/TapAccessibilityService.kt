package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityEvent
import com.example.data.PixelTapRepository

class TapAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        PixelTapRepository.getInstance(applicationContext).setAccessibilityConnected(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No UI scraping needed; service is solely used for gesture tap dispatch.
    }

    override fun onInterrupt() {
        // No-op
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) {
            instance = null
        }
        PixelTapRepository.getInstance(applicationContext).setAccessibilityConnected(false)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) {
            instance = null
        }
        PixelTapRepository.getInstance(applicationContext).setAccessibilityConnected(false)
        super.onDestroy()
    }

    fun performTap(
        x: Float,
        y: Float,
        durationMs: Long = 60L,
        onResult: (Boolean) -> Unit = {}
    ) {
        val safeX = x.coerceAtLeast(1f)
        val safeY = y.coerceAtLeast(1f)
        val safeDuration = durationMs.coerceIn(25L, 250L)

        val path = Path().apply {
            moveTo(safeX, safeY)
            lineTo(safeX + 0.5f, safeY + 0.5f)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0L, safeDuration)
        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        val dispatched = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    onResult(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    onResult(false)
                }
            },
            mainHandler
        )
        if (!dispatched) {
            onResult(false)
        }
    }

    companion object {
        @Volatile
        var instance: TapAccessibilityService? = null
            private set

        fun isServiceEnabled(context: Context): Boolean {
            if (instance != null) return true
            val expected = ComponentName(context, TapAccessibilityService::class.java).flattenToString()
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabledServices)
            while (splitter.hasNext()) {
                val componentName = splitter.next()
                if (componentName.equals(expected, ignoreCase = true)) {
                    return true
                }
            }
            return false
        }
    }
}

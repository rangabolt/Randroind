package com.example.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.PointF
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.PixelTapRepository
import com.example.engine.PixelChangeDetector
import com.example.model.TriggerEventLog
import com.example.overlay.ControlDockOverlayView
import com.example.overlay.TriggerTargetOverlayView
import com.example.overlay.WatchSquareOverlayView
import kotlin.math.max
import kotlin.math.roundToInt

class ScreenMonitorService : Service() {

    private lateinit var repository: PixelTapRepository
    private lateinit var windowManager: WindowManager
    private lateinit var mainHandler: Handler

    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private val detector = PixelChangeDetector()

    private var watchView: WatchSquareOverlayView? = null
    private var watchParams: WindowManager.LayoutParams? = null

    private var triggerView: TriggerTargetOverlayView? = null
    private var triggerParams: WindowManager.LayoutParams? = null

    private var dockView: ControlDockOverlayView? = null
    private var dockParams: WindowManager.LayoutParams? = null

    private var screenWidth = 1080
    private var screenHeight = 2400
    private var screenDensityDpi = 420

    private val captureScale = 0.5f
    private var captureWidth = 540
    private var captureHeight = 1200

    @Volatile
    private var cachedWatchRect = Rect(180, 460, 400, 680)

    @Volatile
    private var cachedTapPoint = PointF(540f, 1320f)

    @Volatile
    private var isOverlayShown = false

    @Volatile
    private var isEditable = true

    @Volatile
    private var isWatching = false

    @Volatile
    private var lastTriggerTimeMs = 0L

    @Volatile
    private var skipFramesUntilMs = 0L

    private var logSequence = 0L

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            mainHandler.post {
                stopProjectionOnly("Screen capture stopped by system")
            }
        }
    }

    private val analysisRunnable = object : Runnable {
        override fun run() {
            if (!isWatching || mediaProjection == null) return
            try {
                analyzeLatestFrame()
            } catch (_: Throwable) {
                // Keep capture loop resilient if a transient buffer arrives during rotation/teardown
            }
            val interval = repository.config.value.sampleRateMode.intervalMs.coerceIn(60L, 1000L)
            captureHandler?.postDelayed(this, interval)
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = PixelTapRepository.getInstance(applicationContext)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        mainHandler = Handler(Looper.getMainLooper())
        createNotificationChannel()
        refreshScreenMetrics()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW_OVERLAY -> {
                if (!Settings.canDrawOverlays(this)) {
                    Toast.makeText(
                        this,
                        "Enable Display Over Other Apps permission first",
                        Toast.LENGTH_SHORT
                    ).show()
                    repository.updateRuntimeState {
                        it.copy(overlayVisible = false, statusMessage = "Overlay permission required")
                    }
                    stopSelf()
                    return START_NOT_STICKY
                }
                promoteForeground(hasProjection = mediaProjection != null)
                showOverlaysIfNeeded()
            }

            ACTION_START_PROJECTION -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                @Suppress("DEPRECATION")
                val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }
                if (resultCode == Activity.RESULT_OK && resultData != null) {
                    promoteForeground(hasProjection = true)
                    showOverlaysIfNeeded()
                    startMediaProjection(resultCode, resultData)
                } else {
                    promoteForeground(hasProjection = mediaProjection != null)
                    repository.updateRuntimeState {
                        it.copy(statusMessage = "Screen capture permission declined")
                    }
                }
            }

            ACTION_PROJECTION_DENIED -> {
                repository.updateRuntimeState {
                    it.copy(
                        isWatching = false,
                        statusMessage = "Screen capture permission cancelled"
                    )
                }
                if (!isOverlayShown) stopSelf()
            }

            ACTION_TOGGLE_WATCH -> {
                if (!Settings.canDrawOverlays(this)) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                if (!isOverlayShown) {
                    promoteForeground(hasProjection = mediaProjection != null)
                    showOverlaysIfNeeded()
                }
                toggleWatching()
            }

            ACTION_TOGGLE_EDIT -> {
                if (isOverlayShown) setEditMode(!isEditable) else stopSelf()
            }

            ACTION_REBASELINE -> {
                if (isOverlayShown) {
                    detector.requestRebaseline()
                    repository.updateRuntimeState {
                        it.copy(
                            currentChangePercent = 0f,
                            statusMessage = "Baseline re-captured"
                        )
                    }
                } else {
                    stopSelf()
                }
            }

            ACTION_TEST_TAP -> {
                if (isOverlayShown) dispatchTestTap() else stopSelf()
            }

            ACTION_SYNC_CONFIG -> {
                if (isOverlayShown) syncOverlayGeometryFromConfig() else stopSelf()
            }

            ACTION_STOP_OVERLAY -> {
                stopEverything()
            }
        }
        return START_NOT_STICKY
    }

    private fun refreshScreenMetrics() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            screenWidth = bounds.width().coerceAtLeast(360)
            screenHeight = bounds.height().coerceAtLeast(640)
            screenDensityDpi = resources.configuration.densityDpi
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(dm)
            screenWidth = dm.widthPixels.coerceAtLeast(360)
            screenHeight = dm.heightPixels.coerceAtLeast(640)
            screenDensityDpi = dm.densityDpi
        }
        captureWidth = (screenWidth * captureScale).roundToInt().coerceAtLeast(180)
        captureHeight = (screenHeight * captureScale).roundToInt().coerceAtLeast(320)

        repository.updateRuntimeState {
            it.copy(screenWidthPx = screenWidth, screenHeightPx = screenHeight)
        }
    }

    private fun promoteForeground(hasProjection: Boolean) {
        val notification = buildNotification(watching = isWatching)
        val fgsType = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                if (hasProjection) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                if (hasProjection) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0
            }
            else -> 0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsType)
    }

    private fun showOverlaysIfNeeded() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(
                this,
                "Enable Display Over Other Apps permission first",
                Toast.LENGTH_SHORT
            ).show()
            repository.updateRuntimeState {
                it.copy(overlayVisible = false, statusMessage = "Overlay permission required")
            }
            return
        }
        if (isOverlayShown) return

        refreshScreenMetrics()
        val cfg = repository.config.value

        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // 1. Watch Square Overlay
        val watchSize = cfg.watchSizePx.coerceIn(120, minOf(screenWidth, screenHeight) - 40)
        val watchX = cfg.watchX.coerceIn(0, (screenWidth - watchSize).coerceAtLeast(0))
        val watchY = cfg.watchY.coerceIn(0, (screenHeight - watchSize).coerceAtLeast(0))

        val wView = WatchSquareOverlayView(
            context = this,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            onMove = { newX, newY ->
                watchParams?.let { params ->
                    params.x = newX
                    params.y = newY
                    watchView?.let { v -> windowManager.updateViewLayout(v, params) }
                }
                repository.updateConfig { it.copy(watchX = newX, watchY = newY) }
                refreshCachedCoordinates()
            },
            onResize = { newSize ->
                watchParams?.let { params ->
                    params.width = newSize
                    params.height = newSize
                    watchView?.let { v -> windowManager.updateViewLayout(v, params) }
                }
                repository.updateConfig { it.copy(watchSizePx = newSize) }
                detector.requestRebaseline()
                refreshCachedCoordinates()
            }
        ).apply {
            currentWindowX = watchX
            currentWindowY = watchY
            thresholdPercent = cfg.thresholdPercent
            isEditable = this@ScreenMonitorService.isEditable
            isWatching = this@ScreenMonitorService.isWatching
        }

        val wParams = WindowManager.LayoutParams(
            watchSize,
            watchSize,
            overlayType,
            overlayFlags(touchable = isEditable),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = watchX
            y = watchY
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        // 2. Adjustable Trigger Target Button Overlay
        val tView = TriggerTargetOverlayView(
            context = this,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            onMoveCenter = { cx, cy ->
                triggerParams?.let { params ->
                    val half = (triggerView?.viewSizePx ?: 160) / 2
                    params.x = cx - half
                    params.y = cy - half
                    triggerView?.let { v -> windowManager.updateViewLayout(v, params) }
                }
                repository.updateConfig { it.copy(triggerX = cx, triggerY = cy) }
                refreshCachedCoordinates()
            },
            onTargetTappedInEdit = {
                val pt = cachedTapPoint
                Toast.makeText(
                    this,
                    "Trigger point set at (${pt.x.roundToInt()}, ${pt.y.roundToInt()})",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
        val halfTarget = tView.viewSizePx / 2
        val targetWinX = (cfg.triggerX - halfTarget).coerceIn(-halfTarget, screenWidth - halfTarget)
        val targetWinY = (cfg.triggerY - halfTarget).coerceIn(-halfTarget, screenHeight - halfTarget)
        tView.currentWindowX = targetWinX
        tView.currentWindowY = targetWinY
        tView.isEditable = isEditable
        tView.isWatching = isWatching

        val tParams = WindowManager.LayoutParams(
            tView.viewSizePx,
            tView.viewSizePx,
            overlayType,
            overlayFlags(touchable = isEditable),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = targetWinX
            y = targetWinY
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        // 3. Floating Control Dock Overlay
        val dView = ControlDockOverlayView(
            context = this,
            screenWidth = screenWidth,
            screenHeight = screenHeight,
            onMoveDock = { nx, ny ->
                dockParams?.let { params ->
                    params.x = nx
                    params.y = ny
                    dockView?.let { v -> windowManager.updateViewLayout(v, params) }
                }
            },
            onToggleWatching = { toggleWatching() },
            onToggleEditMode = { setEditMode(!isEditable) },
            onRebaseline = {
                detector.requestRebaseline()
                Toast.makeText(this, "Watch baseline refreshed", Toast.LENGTH_SHORT).show()
            },
            onTestTap = { dispatchTestTap() },
            onOpenApp = {
                val launch = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                startActivity(launch)
            },
            onCloseOverlay = { stopEverything() }
        )
        val density = resources.displayMetrics.density
        val dockX = (24f * density).roundToInt()
        val dockY = (96f * density).roundToInt()
        dView.currentWindowX = dockX
        dView.currentWindowY = dockY

        val dParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dockX
            y = dockY
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        watchView = wView
        watchParams = wParams
        triggerView = tView
        triggerParams = tParams
        dockView = dView
        dockParams = dParams

        windowManager.addView(wView, wParams)
        windowManager.addView(tView, tParams)
        windowManager.addView(dView, dParams)

        isOverlayShown = true
        wView.post { refreshCachedCoordinates() }
        tView.post { refreshCachedCoordinates() }
        updateHudAndState("Overlay ready — position square & tap target")
    }

    private fun overlayFlags(touchable: Boolean): Int {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        if (!touchable) {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        return flags
    }

    private fun setEditMode(editable: Boolean) {
        isEditable = editable
        watchView?.isEditable = editable
        triggerView?.isEditable = editable

        watchParams?.let { params ->
            params.flags = overlayFlags(touchable = editable)
            watchView?.let { v -> windowManager.updateViewLayout(v, params) }
        }
        triggerParams?.let { params ->
            params.flags = overlayFlags(touchable = editable)
            triggerView?.let { v -> windowManager.updateViewLayout(v, params) }
        }
        refreshCachedCoordinates()
        updateHudAndState(
            if (editable) "Edit mode: drag square & tap target"
            else if (isWatching) "Armed & watching"
            else "Pass-through locked"
        )
    }

    private fun toggleWatching() {
        if (isWatching) {
            pauseWatching("Paused by user")
            return
        }
        if (mediaProjection == null) {
            repository.updateRuntimeState {
                it.copy(statusMessage = "Requesting screen capture permission...")
            }
            ProjectionPermissionActivity.launch(this)
            return
        }
        startWatchingLoop()
    }

    private fun startMediaProjection(resultCode: Int, data: Intent) {
        stopProjectionResourcesOnly()
        refreshScreenMetrics()

        val thread = HandlerThread("PixelTapCapture").apply { start() }
        val handler = Handler(thread.looper)
        captureThread = thread
        captureHandler = handler

        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = mgr.getMediaProjection(resultCode, data)
        if (projection == null) {
            repository.updateRuntimeState {
                it.copy(isWatching = false, statusMessage = "Failed to start screen projection")
            }
            return
        }
        projection.registerCallback(projectionCallback, handler)

        val reader = ImageReader.newInstance(
            captureWidth,
            captureHeight,
            PixelFormat.RGBA_8888,
            2
        )
        val vDisplay = projection.createVirtualDisplay(
            "PixelTapWatchDisplay",
            captureWidth,
            captureHeight,
            (screenDensityDpi * captureScale).roundToInt().coerceAtLeast(120),
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            handler
        )

        mediaProjection = projection
        imageReader = reader
        virtualDisplay = vDisplay

        startWatchingLoop()
    }

    private fun startWatchingLoop() {
        if (mediaProjection == null) return
        // Lock overlays so Accessibility taps pass right through to underlying app
        if (isEditable) {
            setEditMode(false)
        }
        detector.reset()
        isWatching = true
        watchView?.isWatching = true
        triggerView?.isWatching = true
        // Wait for Edit HUD tint/badge/knob to finish disappearing from compositor before capturing baseline
        skipFramesUntilMs = System.currentTimeMillis() + 450L

        captureHandler?.removeCallbacks(analysisRunnable)
        captureHandler?.postDelayed(analysisRunnable, 460L)

        updateNotification(watching = true)
        updateHudAndState("Watching square for visual threshold")
    }

    private fun pauseWatching(reason: String) {
        isWatching = false
        captureHandler?.removeCallbacks(analysisRunnable)
        watchView?.isWatching = false
        triggerView?.isWatching = false
        updateNotification(watching = false)
        updateHudAndState(reason)
    }

    private fun stopProjectionOnly(reason: String) {
        pauseWatching(reason)
        stopProjectionResourcesOnly()
        repository.updateRuntimeState {
            it.copy(hasProjectionSession = false, isWatching = false, statusMessage = reason)
        }
    }

    private fun stopProjectionResourcesOnly() {
        captureHandler?.removeCallbacks(analysisRunnable)
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { imageReader?.close() }
        imageReader = null
        runCatching {
            mediaProjection?.unregisterCallback(projectionCallback)
            mediaProjection?.stop()
        }
        mediaProjection = null
        captureThread?.quitSafely()
        captureThread = null
        captureHandler = null
    }

    private fun analyzeLatestFrame() {
        val reader = imageReader ?: return
        val image: Image = reader.acquireLatestImage() ?: return
        val cfg = repository.config.value

        try {
            val now = System.currentTimeMillis()
            if (now < skipFramesUntilMs) return

            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride

            val screenRect = cachedWatchRect
            val left = (screenRect.left * captureScale).roundToInt().coerceIn(0, captureWidth - 2)
            val top = (screenRect.top * captureScale).roundToInt().coerceIn(0, captureHeight - 2)
            val right = (screenRect.right * captureScale).roundToInt().coerceIn(left + 2, captureWidth)
            val bottom = (screenRect.bottom * captureScale).roundToInt().coerceIn(top + 2, captureHeight)

            val regionW = right - left
            val regionH = bottom - top
            val step = max(1, minOf(regionW, regionH) / 40)

            val cols = ((regionW + step - 1) / step).coerceAtLeast(1)
            val rows = ((regionH + step - 1) / step).coerceAtLeast(1)
            val sampled = IntArray(cols * rows)

            var idx = 0
            var y = top
            while (y < bottom && idx < sampled.size) {
                var x = left
                val rowBase = y * rowStride
                while (x < right && idx < sampled.size) {
                    val offset = rowBase + x * pixelStride
                    val r = buffer.get(offset).toInt() and 0xFF
                    val g = buffer.get(offset + 1).toInt() and 0xFF
                    val b = buffer.get(offset + 2).toInt() and 0xFF
                    sampled[idx++] = (r shl 16) or (g shl 8) or b
                    x += step
                }
                y += step
            }

            val eval = detector.evaluateFrame(
                pixels = sampled,
                width = cols,
                height = rows,
                mode = cfg.detectionMode,
                thresholdPercent = cfg.thresholdPercent,
                pixelTolerance = cfg.pixelTolerance,
                nowMs = now
            )

            mainHandler.post {
                watchView?.thresholdPercent = cfg.thresholdPercent
                watchView?.currentChangePercent = eval.changePercent
                dockView?.updateHud(isWatching, isEditable, eval.changePercent)
            }
            repository.updateRuntimeState {
                it.copy(
                    currentChangePercent = eval.changePercent,
                    hasProjectionSession = mediaProjection != null
                )
            }

            if (eval.thresholdMet && (now - lastTriggerTimeMs >= cfg.cooldownMs)) {
                lastTriggerTimeMs = now
                // Update capture-thread state synchronously so no extra frame can fire a duplicate tap
                if (cfg.oneShotMode) {
                    isWatching = false
                } else {
                    detector.enterSettleMode(cooldownMs = cfg.cooldownMs, nowMs = now)
                }

                val tapPt = cachedTapPoint
                val tapX = tapPt.x
                val tapY = tapPt.y

                mainHandler.post {
                    executeAutomatedTap(
                        tapX = tapX,
                        tapY = tapY,
                        tapDurationMs = cfg.tapDurationMs,
                        measuredPercent = eval.changePercent,
                        thresholdPercent = cfg.thresholdPercent,
                        oneShotMode = cfg.oneShotMode
                    )
                }
            }
        } finally {
            image.close()
        }
    }

    private fun executeAutomatedTap(
        tapX: Float,
        tapY: Float,
        tapDurationMs: Long,
        measuredPercent: Float,
        thresholdPercent: Float,
        oneShotMode: Boolean
    ) {
        watchView?.flashTriggered()
        triggerView?.flashTapPulse()

        val accessibility = TapAccessibilityService.instance
        if (accessibility != null) {
            accessibility.performTap(tapX, tapY, tapDurationMs) { success ->
                val note = if (success) "Tap dispatched" else "Tap cancelled by system"
                recordTriggerLog(measuredPercent, thresholdPercent, tapX, tapY, success, note)
            }
        } else {
            recordTriggerLog(
                measuredPercent = measuredPercent,
                thresholdPercent = thresholdPercent,
                tapX = tapX,
                tapY = tapY,
                dispatched = false,
                note = "Accessibility service off — visual trigger logged only"
            )
        }

        if (oneShotMode) {
            pauseWatching("Triggered once (One-Shot complete)")
        }
    }

    private fun dispatchTestTap() {
        val wasEditable = isEditable
        if (wasEditable) {
            setEditMode(false)
        }
        val pt = cachedTapPoint
        val cfg = repository.config.value

        mainHandler.postDelayed({
            triggerView?.flashTapPulse()
            val accessibility = TapAccessibilityService.instance
            if (accessibility != null) {
                accessibility.performTap(pt.x, pt.y, cfg.tapDurationMs) { success ->
                    recordTriggerLog(
                        measuredPercent = 100f,
                        thresholdPercent = cfg.thresholdPercent,
                        tapX = pt.x,
                        tapY = pt.y,
                        dispatched = success,
                        note = if (success) "Manual test tap" else "Test tap failed"
                    )
                }
            } else {
                Toast.makeText(
                    this,
                    "Enable tapatepa Accessibility Service to perform real taps",
                    Toast.LENGTH_SHORT
                ).show()
                recordTriggerLog(
                    measuredPercent = 0f,
                    thresholdPercent = cfg.thresholdPercent,
                    tapX = pt.x,
                    tapY = pt.y,
                    dispatched = false,
                    note = "Test tap skipped (Accessibility disabled)"
                )
            }
            if (wasEditable && !isWatching) {
                mainHandler.postDelayed({
                    if (isOverlayShown && !isWatching) setEditMode(true)
                }, 180L)
            }
        }, if (wasEditable) 80L else 10L)
    }

    private fun recordTriggerLog(
        measuredPercent: Float,
        thresholdPercent: Float,
        tapX: Float,
        tapY: Float,
        dispatched: Boolean,
        note: String
    ) {
        val now = System.currentTimeMillis()
        logSequence++
        repository.appendLog(
            TriggerEventLog(
                id = logSequence,
                timestampMs = now,
                measuredChangePercent = measuredPercent,
                thresholdPercent = thresholdPercent,
                tapX = tapX.roundToInt(),
                tapY = tapY.roundToInt(),
                tapDispatched = dispatched,
                note = note
            )
        )
        repository.updateRuntimeState {
            it.copy(
                totalTriggers = it.totalTriggers + 1,
                lastTriggerTimestampMs = now,
                statusMessage = note
            )
        }
    }

    private fun refreshCachedCoordinates() {
        watchView?.let { wView ->
            if (wView.isAttachedToWindow && wView.width > 0 && wView.height > 0) {
                cachedWatchRect = wView.getSampleScreenRect()
            } else {
                val cfg = repository.config.value
                cachedWatchRect = Rect(
                    cfg.watchX + 12,
                    cfg.watchY + 12,
                    cfg.watchX + cfg.watchSizePx - 12,
                    cfg.watchY + cfg.watchSizePx - 12
                )
            }
        }
        triggerView?.let { tView ->
            if (tView.isAttachedToWindow && tView.width > 0 && tView.height > 0) {
                cachedTapPoint = tView.getTapCenterScreenPoint()
            } else {
                val cfg = repository.config.value
                cachedTapPoint = PointF(cfg.triggerX.toFloat(), cfg.triggerY.toFloat())
            }
        }
    }

    private fun syncOverlayGeometryFromConfig() {
        val cfg = repository.config.value
        watchView?.thresholdPercent = cfg.thresholdPercent

        if (!isOverlayShown) return
        val wSize = cfg.watchSizePx.coerceIn(120, minOf(screenWidth, screenHeight) - 40)
        val wX = cfg.watchX.coerceIn(0, (screenWidth - wSize).coerceAtLeast(0))
        val wY = cfg.watchY.coerceIn(0, (screenHeight - wSize).coerceAtLeast(0))

        watchView?.let { wView ->
            wView.currentWindowX = wX
            wView.currentWindowY = wY
            watchParams?.let { params ->
                params.x = wX
                params.y = wY
                params.width = wSize
                params.height = wSize
                windowManager.updateViewLayout(wView, params)
            }
        }

        triggerView?.let { tView ->
            val half = tView.viewSizePx / 2
            val tWinX = (cfg.triggerX - half).coerceIn(-half, screenWidth - half)
            val tWinY = (cfg.triggerY - half).coerceIn(-half, screenHeight - half)
            tView.currentWindowX = tWinX
            tView.currentWindowY = tWinY
            tView.invalidate()
            triggerParams?.let { params ->
                params.x = tWinX
                params.y = tWinY
                windowManager.updateViewLayout(tView, params)
            }
        }
        mainHandler.post { refreshCachedCoordinates() }
    }

    private fun updateHudAndState(message: String) {
        dockView?.updateHud(
            isWatching = isWatching,
            isEditable = isEditable,
            changePercent = repository.runtimeState.value.currentChangePercent
        )
        repository.updateRuntimeState {
            it.copy(
                overlayVisible = isOverlayShown,
                editMode = isEditable,
                isWatching = isWatching,
                hasProjectionSession = mediaProjection != null,
                statusMessage = message
            )
        }
    }

    private fun stopEverything() {
        isWatching = false
        stopProjectionResourcesOnly()
        removeOverlays()
        repository.updateRuntimeState {
            it.copy(
                overlayVisible = false,
                isWatching = false,
                hasProjectionSession = false,
                currentChangePercent = 0f,
                statusMessage = "Overlay stopped"
            )
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun removeOverlays() {
        watchView?.let { runCatching { windowManager.removeView(it) } }
        triggerView?.let { runCatching { windowManager.removeView(it) } }
        dockView?.let { runCatching { windowManager.removeView(it) } }
        watchView = null
        triggerView = null
        dockView = null
        isOverlayShown = false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(watching: Boolean) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(watching))
    }

    private fun buildNotification(watching: Boolean): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, ScreenMonitorService::class.java).apply {
                action = ACTION_TOGGLE_WATCH
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, ScreenMonitorService::class.java).apply {
                action = ACTION_STOP_OVERLAY
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_hud_target)
            .setContentTitle(
                getString(
                    if (watching) R.string.notification_title_watching
                    else R.string.notification_title_setup
                )
            )
            .setContentText(
                getString(
                    if (watching) R.string.notification_text_watching
                    else R.string.notification_text_setup
                )
            )
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(
                if (watching) R.drawable.ic_hud_pause else R.drawable.ic_hud_play,
                getString(R.string.action_toggle_watch),
                toggleIntent
            )
            .addAction(
                R.drawable.ic_hud_close,
                getString(R.string.action_stop_overlay),
                stopIntent
            )
            .build()
    }

    override fun onDestroy() {
        stopProjectionResourcesOnly()
        removeOverlays()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "pixeltap_monitor_channel"
        private const val NOTIFICATION_ID = 4102

        const val ACTION_SHOW_OVERLAY = "com.example.action.SHOW_OVERLAY"
        const val ACTION_START_PROJECTION = "com.example.action.START_PROJECTION"
        const val ACTION_PROJECTION_DENIED = "com.example.action.PROJECTION_DENIED"
        const val ACTION_TOGGLE_WATCH = "com.example.action.TOGGLE_WATCH"
        const val ACTION_TOGGLE_EDIT = "com.example.action.TOGGLE_EDIT"
        const val ACTION_REBASELINE = "com.example.action.REBASELINE"
        const val ACTION_TEST_TAP = "com.example.action.TEST_TAP"
        const val ACTION_SYNC_CONFIG = "com.example.action.SYNC_CONFIG"
        const val ACTION_STOP_OVERLAY = "com.example.action.STOP_OVERLAY"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        fun sendAction(context: Context, action: String) {
            val intent = Intent(context, ScreenMonitorService::class.java).apply {
                this.action = action
            }
            if (action == ACTION_SHOW_OVERLAY || action == ACTION_TOGGLE_WATCH) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        }
    }
}

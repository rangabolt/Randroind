package com.example.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

@SuppressLint("ViewConstructor")
class WatchSquareOverlayView(
    context: Context,
    private val screenWidth: Int,
    private val screenHeight: Int,
    private val onMove: (x: Int, y: Int) -> Unit,
    private val onResize: (sizePx: Int) -> Unit
) : View(context) {

    private val density = resources.displayMetrics.density
    private val minSizePx = (64f * density).roundToInt()
    private val maxSizePx = (minOf(screenWidth, screenHeight) * 0.85f).roundToInt().coerceAtLeast(minSizePx + 40)
    private val resizeHitRadius = 36f * density
    private val insetPx = (10f * density).roundToInt()

    var isEditable: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    var isWatching: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var currentChangePercent: Float = 0f
        set(value) {
            field = value
            invalidate()
        }

    var thresholdPercent: Float = 15f
        set(value) {
            field = value
            invalidate()
        }

    private var triggerFlashUntilMs: Long = 0L

    fun flashTriggered() {
        triggerFlashUntilMs = System.currentTimeMillis() + 360L
        invalidate()
        postDelayed({ invalidate() }, 380L)
    }

    fun getSampleScreenRect(): Rect {
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        val left = (loc[0] + insetPx).coerceIn(0, screenWidth)
        val top = (loc[1] + insetPx).coerceIn(0, screenHeight)
        val right = (loc[0] + width - insetPx).coerceIn(left + 4, screenWidth)
        val bottom = (loc[1] + height - insetPx).coerceIn(top + 4, screenHeight)
        return Rect(left, top, right, bottom)
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(26, 0, 229, 255)
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = Color.argb(170, 0, 229, 255)
        pathEffect = DashPathEffect(floatArrayOf(8f * density, 6f * density), 0f)
    }

    private val bracketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.rgb(0, 229, 255)
    }

    private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(215, 13, 19, 26)
    }

    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 229, 255)
        textSize = 10.5f * density
        typeface = Typeface.MONOSPACE
    }

    private val handleBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.rgb(0, 229, 255)
    }

    private val handleIconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        strokeCap = Paint.Cap.ROUND
        color = Color.rgb(10, 15, 20)
    }

    private val bracketPath = Path()
    private val badgeRect = RectF()

    private var dragMode = DragMode.NONE
    private var startRawX = 0f
    private var startRawY = 0f
    private var initialWindowX = 0
    private var initialWindowY = 0
    private var initialSizePx = 0

    var currentWindowX = 0
    var currentWindowY = 0

    private enum class DragMode { NONE, MOVE, RESIZE }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEditable) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startRawX = event.rawX
                startRawY = event.rawY
                initialWindowX = currentWindowX
                initialWindowY = currentWindowY
                initialSizePx = width

                val distToBottomRight = hypot(
                    (width - event.x).toDouble(),
                    (height - event.y).toDouble()
                ).toFloat()

                dragMode = if (distToBottomRight <= resizeHitRadius) {
                    DragMode.RESIZE
                } else {
                    DragMode.MOVE
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - startRawX).roundToInt()
                val dy = (event.rawY - startRawY).roundToInt()
                when (dragMode) {
                    DragMode.MOVE -> {
                        val newX = (initialWindowX + dx).coerceIn(0, max(0, screenWidth - width))
                        val newY = (initialWindowY + dy).coerceIn(0, max(0, screenHeight - height))
                        currentWindowX = newX
                        currentWindowY = newY
                        onMove(newX, newY)
                    }
                    DragMode.RESIZE -> {
                        val delta = max(dx, dy)
                        val maxAllowed = minOf(
                            maxSizePx,
                            screenWidth - currentWindowX,
                            screenHeight - currentWindowY
                        ).coerceAtLeast(minSizePx)
                        val newSize = (initialSizePx + delta).coerceIn(minSizePx, maxAllowed)
                        onResize(newSize)
                    }
                    DragMode.NONE -> Unit
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragMode = DragMode.NONE
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val isFlashing = System.currentTimeMillis() < triggerFlashUntilMs
        val activeColor = when {
            isFlashing -> Color.rgb(0, 230, 118)
            isWatching -> Color.rgb(0, 229, 255)
            else -> Color.rgb(0, 229, 255)
        }

        // Only tint interior in Edit Mode; never draw inside the sampled region while watching
        if (isEditable && !isWatching) {
            fillPaint.color = Color.argb(20, 0, 229, 255)
            canvas.drawRect(3f * density, 3f * density, w - 3f * density, h - 3f * density, fillPaint)
        }

        borderPaint.color = if (isFlashing) {
            Color.argb(245, 0, 230, 118)
        } else if (isWatching) {
            Color.argb(145, 0, 229, 255)
        } else {
            Color.argb(190, 0, 229, 255)
        }
        val strokeHalf = 2f * density
        canvas.drawRect(strokeHalf, strokeHalf, w - strokeHalf, h - strokeHalf, borderPaint)

        // Corner brackets strictly within outer 4dp perimeter (outside insetPx sample zone)
        bracketPaint.color = activeColor
        val bracketLen = minOf(w, h) * 0.22f
        val pad = 2f * density
        bracketPath.reset()
        // Top-left
        bracketPath.moveTo(pad, pad + bracketLen)
        bracketPath.lineTo(pad, pad)
        bracketPath.lineTo(pad + bracketLen, pad)
        // Top-right
        bracketPath.moveTo(w - pad - bracketLen, pad)
        bracketPath.lineTo(w - pad, pad)
        bracketPath.lineTo(w - pad, pad + bracketLen)
        // Bottom-left
        bracketPath.moveTo(pad, h - pad - bracketLen)
        bracketPath.lineTo(pad, h - pad)
        bracketPath.lineTo(pad + bracketLen, h - pad)
        // Bottom-right
        if (!isEditable) {
            bracketPath.moveTo(w - pad - bracketLen, h - pad)
            bracketPath.lineTo(w - pad, h - pad)
            bracketPath.lineTo(w - pad, h - pad - bracketLen)
        }
        canvas.drawPath(bracketPath, bracketPaint)

        // Show size badge only while positioning in non-watching mode so watched pixels stay untouched
        if (!isWatching) {
            val label = "WATCH ${width}px"
            badgeTextPaint.color = activeColor
            val textW = badgeTextPaint.measureText(label)
            val badgeH = 18f * density
            val badgePadH = 6f * density
            if (w >= textW + badgePadH * 2 + 8f * density) {
                badgeRect.set(
                    6f * density,
                    6f * density,
                    6f * density + textW + badgePadH * 2,
                    6f * density + badgeH
                )
                canvas.drawRoundRect(badgeRect, 5f * density, 5f * density, badgeBgPaint)
                canvas.drawText(
                    label,
                    badgeRect.left + badgePadH,
                    badgeRect.top + 12.5f * density,
                    badgeTextPaint
                )
            }
        }

        // Resize knob in Edit Mode
        if (isEditable && !isWatching) {
            val knobR = 13f * density
            val cx = w - knobR - 2f * density
            val cy = h - knobR - 2f * density
            canvas.drawCircle(cx, cy, knobR, handleBgPaint)
            val d = 4.5f * density
            canvas.drawLine(cx - d, cy - d, cx + d, cy + d, handleIconPaint)
            canvas.drawLine(cx + d, cy + d, cx + d - 4f * density, cy + d, handleIconPaint)
            canvas.drawLine(cx + d, cy + d, cx + d, cy + d - 4f * density, handleIconPaint)
        }
    }
}

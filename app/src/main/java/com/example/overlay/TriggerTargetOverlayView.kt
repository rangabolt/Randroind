package com.example.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.hypot
import kotlin.math.roundToInt

@SuppressLint("ViewConstructor")
class TriggerTargetOverlayView(
    context: Context,
    private val screenWidth: Int,
    private val screenHeight: Int,
    private val onMoveCenter: (centerX: Int, centerY: Int) -> Unit,
    private val onTargetTappedInEdit: () -> Unit
) : View(context) {

    private val density = resources.displayMetrics.density
    val viewSizePx: Int = (84f * density).roundToInt()
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

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

    var currentWindowX: Int = 0
    var currentWindowY: Int = 0

    private var pulseUntilMs: Long = 0L

    fun flashTapPulse() {
        pulseUntilMs = System.currentTimeMillis() + 380L
        invalidate()
        postDelayed({ invalidate() }, 400L)
    }

    fun getTapCenterScreenPoint(): PointF {
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        return PointF(loc[0] + width / 2f, loc[1] + height / 2f)
    }

    private val outerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        color = Color.rgb(255, 179, 0)
    }

    private val innerFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(48, 255, 179, 0)
    }

    private val centerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.rgb(255, 179, 0)
    }

    private val crosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.8f * density
        color = Color.rgb(255, 213, 79)
    }

    private val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(225, 13, 19, 26)
    }

    private val pillTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 179, 0)
        textSize = 9.5f * density
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    private val pillRect = RectF()

    private var startRawX = 0f
    private var startRawY = 0f
    private var initialWinX = 0
    private var initialWinY = 0
    private var movedBeyondSlop = false

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEditable) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startRawX = event.rawX
                startRawY = event.rawY
                initialWinX = currentWindowX
                initialWinY = currentWindowY
                movedBeyondSlop = false
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - startRawX
                val dy = event.rawY - startRawY
                if (hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                    movedBeyondSlop = true
                }
                val half = viewSizePx / 2
                val newWinX = (initialWinX + dx.roundToInt()).coerceIn(-half, screenWidth - half)
                val newWinY = (initialWinY + dy.roundToInt()).coerceIn(-half, screenHeight - half)
                currentWindowX = newWinX
                currentWindowY = newWinY
                onMoveCenter(newWinX + half, newWinY + half)
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (!movedBeyondSlop) {
                    flashTapPulse()
                    onTargetTappedInEdit()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        if (cx <= 0f || cy <= 0f) return

        val isPulsing = System.currentTimeMillis() < pulseUntilMs
        val primaryColor = if (isPulsing) Color.rgb(0, 230, 118) else Color.rgb(255, 179, 0)

        val ringRadius = 22f * density
        if (isPulsing) {
            outerRingPaint.color = Color.argb(180, 0, 230, 118)
            canvas.drawCircle(cx, cy, ringRadius + 8f * density, outerRingPaint)
        }

        innerFillPaint.color = when {
            isPulsing -> Color.argb(72, 0, 230, 118)
            isEditable -> Color.argb(56, 255, 179, 0)
            else -> Color.argb(24, 255, 179, 0)
        }
        canvas.drawCircle(cx, cy, ringRadius, innerFillPaint)

        outerRingPaint.color = if (isEditable || isPulsing) {
            primaryColor
        } else {
            Color.argb(180, 255, 179, 0)
        }
        canvas.drawCircle(cx, cy, ringRadius, outerRingPaint)

        // Precision crosshair centered on exact tap point
        crosshairPaint.color = primaryColor
        val gap = 5f * density
        val arm = ringRadius + 5f * density
        canvas.drawLine(cx - arm, cy, cx - gap, cy, crosshairPaint)
        canvas.drawLine(cx + gap, cy, cx + arm, cy, crosshairPaint)
        canvas.drawLine(cx, cy - arm, cx, cy - gap, crosshairPaint)
        canvas.drawLine(cx, cy + gap, cx, cy + arm, crosshairPaint)

        centerDotPaint.color = primaryColor
        canvas.drawCircle(cx, cy, 3.2f * density, centerDotPaint)

        // Top TAP label
        val label = if (isEditable) "TAP TARGET" else "TAP"
        val tw = pillTextPaint.measureText(label)
        val ph = 14f * density
        val padH = 5f * density
        pillRect.set(cx - tw / 2f - padH, 2f * density, cx + tw / 2f + padH, 2f * density + ph)
        canvas.drawRoundRect(pillRect, 4f * density, 4f * density, pillBgPaint)
        pillTextPaint.color = primaryColor
        canvas.drawText(label, pillRect.left + padH, pillRect.top + 10.5f * density, pillTextPaint)

        // Bottom coordinate readout in edit mode
        if (isEditable) {
            val centerX = currentWindowX + viewSizePx / 2
            val centerY = currentWindowY + viewSizePx / 2
            val coord = "$centerX,$centerY"
            val cw = pillTextPaint.measureText(coord)
            pillRect.set(
                cx - cw / 2f - padH,
                height - ph - 2f * density,
                cx + cw / 2f + padH,
                height - 2f * density
            )
            canvas.drawRoundRect(pillRect, 4f * density, 4f * density, pillBgPaint)
            canvas.drawText(coord, pillRect.left + padH, pillRect.top + 10.5f * density, pillTextPaint)
        }
    }
}

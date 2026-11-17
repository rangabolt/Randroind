package com.example.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.R
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

@SuppressLint("ViewConstructor")
class ControlDockOverlayView(
    context: Context,
    private val screenWidth: Int,
    private val screenHeight: Int,
    private val onMoveDock: (x: Int, y: Int) -> Unit,
    private val onToggleWatching: () -> Unit,
    private val onToggleEditMode: () -> Unit,
    private val onRebaseline: () -> Unit,
    private val onTestTap: () -> Unit,
    private val onOpenApp: () -> Unit,
    private val onCloseOverlay: () -> Unit
) : LinearLayout(context) {

    private val density = resources.displayMetrics.density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    var currentWindowX: Int = 0
    var currentWindowY: Int = 0

    private val statusDot: View
    private val deltaText: TextView
    private val playPauseBtn: ImageButton
    private val editLockBtn: ImageButton
    private val rebaselineBtn: ImageButton
    private val testTapBtn: ImageButton

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val padH = (10f * density).roundToInt()
        val padV = (6f * density).roundToInt()
        setPadding(padH, padV, padH, padV)

        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 24f * density
            setColor(Color.argb(238, 13, 19, 26))
            setStroke((1.5f * density).roundToInt(), Color.rgb(43, 61, 80))
        }

        // Drag handle area
        val dragHandle = TextView(context).apply {
            text = "⋮⋮"
            setTextColor(Color.rgb(122, 143, 166))
            textSize = 14f
            typeface = Typeface.MONOSPACE
            setPadding((4f * density).roundToInt(), 0, (6f * density).roundToInt(), 0)
            contentDescription = "Drag overlay control bar"
        }
        attachDragTouchListener(dragHandle)
        addView(dragHandle)

        // Status LED
        val dotSize = (9f * density).roundToInt()
        statusDot = View(context).apply {
            layoutParams = LayoutParams(dotSize, dotSize).apply {
                marginEnd = (6f * density).roundToInt()
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(0, 229, 255))
            }
        }
        attachDragTouchListener(statusDot)
        addView(statusDot)

        // Live delta percentage readout
        deltaText = TextView(context).apply {
            text = "EDIT"
            setTextColor(Color.rgb(240, 246, 252))
            textSize = 11.5f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setPadding(0, 0, (6f * density).roundToInt(), 0)
        }
        attachDragTouchListener(deltaText)
        addView(deltaText)

        playPauseBtn = createDockButton(
            iconRes = R.drawable.ic_hud_play,
            tint = Color.rgb(0, 230, 118),
            desc = "Arm or pause visual detection",
            onClick = onToggleWatching
        )
        addView(playPauseBtn)

        editLockBtn = createDockButton(
            iconRes = R.drawable.ic_hud_edit,
            tint = Color.rgb(0, 229, 255),
            desc = "Toggle edit or pass-through lock mode",
            onClick = onToggleEditMode
        )
        addView(editLockBtn)

        rebaselineBtn = createDockButton(
            iconRes = R.drawable.ic_hud_refresh,
            tint = Color.rgb(159, 177, 196),
            desc = "Re-capture baseline reference",
            onClick = onRebaseline
        )
        addView(rebaselineBtn)

        testTapBtn = createDockButton(
            iconRes = R.drawable.ic_hud_target,
            tint = Color.rgb(255, 179, 0),
            desc = "Test tap at trigger target",
            onClick = onTestTap
        )
        addView(testTapBtn)

        val openAppBtn = createDockButton(
            iconRes = R.drawable.ic_hud_tune,
            tint = Color.rgb(159, 177, 196),
            desc = "Open tapatepa settings",
            onClick = onOpenApp
        )
        addView(openAppBtn)

        val closeBtn = createDockButton(
            iconRes = R.drawable.ic_hud_close,
            tint = Color.rgb(255, 82, 82),
            desc = "Close tapatepa overlay",
            onClick = onCloseOverlay
        )
        addView(closeBtn)
    }

    private fun createDockButton(
        iconRes: Int,
        tint: Int,
        desc: String,
        onClick: () -> Unit
    ): ImageButton {
        val btnSize = (36f * density).roundToInt()
        val pad = (6f * density).roundToInt()
        return ImageButton(context).apply {
            layoutParams = LayoutParams(btnSize, btnSize).apply {
                marginStart = (2f * density).roundToInt()
            }
            setPadding(pad, pad, pad, pad)
            setImageDrawable(ContextCompat.getDrawable(context, iconRes)?.mutate())
            setColorFilter(tint)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(40, 255, 255, 255))
            }
            contentDescription = desc
            setOnClickListener { onClick() }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachDragTouchListener(view: View) {
        var startRawX = 0f
        var startRawY = 0f
        var initialX = 0
        var initialY = 0
        var moved = false

        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startRawX = event.rawX
                    startRawY = event.rawY
                    initialX = currentWindowX
                    initialY = currentWindowY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - startRawX
                    val dy = event.rawY - startRawY
                    if (hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                        moved = true
                    }
                    if (moved) {
                        val newX = (initialX + dx.roundToInt()).coerceIn(0, max(0, screenWidth - width))
                        val newY = (initialY + dy.roundToInt()).coerceIn(0, max(0, screenHeight - height))
                        currentWindowX = newX
                        currentWindowY = newY
                        onMoveDock(newX, newY)
                    }
                    true
                }
                else -> true
            }
        }
    }

    fun updateHud(isWatching: Boolean, isEditable: Boolean, changePercent: Float) {
        if (isWatching) {
            playPauseBtn.setImageResource(R.drawable.ic_hud_pause)
            playPauseBtn.setColorFilter(Color.rgb(255, 179, 0))
            (statusDot.background as? GradientDrawable)?.setColor(Color.rgb(0, 230, 118))
            deltaText.text = String.format("%.0f%%", changePercent)
            deltaText.setTextColor(Color.rgb(0, 230, 118))
        } else {
            playPauseBtn.setImageResource(R.drawable.ic_hud_play)
            playPauseBtn.setColorFilter(Color.rgb(0, 230, 118))
            (statusDot.background as? GradientDrawable)?.setColor(
                if (isEditable) Color.rgb(0, 229, 255) else Color.rgb(159, 177, 196)
            )
            deltaText.text = if (isEditable) "EDIT" else "LOCK"
            deltaText.setTextColor(
                if (isEditable) Color.rgb(0, 229, 255) else Color.rgb(159, 177, 196)
            )
        }

        if (isEditable) {
            editLockBtn.setImageResource(R.drawable.ic_hud_edit)
            editLockBtn.setColorFilter(Color.rgb(0, 229, 255))
        } else {
            editLockBtn.setImageResource(R.drawable.ic_hud_lock)
            editLockBtn.setColorFilter(Color.rgb(255, 179, 0))
        }
    }
}

package com.example.engine

import com.example.model.DetectionMode
import kotlin.math.abs

data class FrameEvaluationResult(
    val changePercent: Float,
    val thresholdMet: Boolean,
    val baselineCapturedNow: Boolean,
    val isSettling: Boolean = false
)

/**
 * Pure Kotlin visual change evaluator for the watch square region.
 * Enforces edge-triggered single-tap-per-change behavior: once a change triggers,
 * [enterSettleMode] suppresses further triggers until the cooldown expires and
 * consecutive frames stabilize, then locks the settled image as the new baseline.
 */
class PixelChangeDetector {

    private var referenceFrame: IntArray? = null
    private var previousFrame: IntArray? = null
    private var lastFrameWidth: Int = 0
    private var lastFrameHeight: Int = 0

    private var settling: Boolean = false
    private var settleMinEndTimeMs: Long = 0L
    private var stableFrameCount: Int = 0

    fun reset() {
        referenceFrame = null
        previousFrame = null
        lastFrameWidth = 0
        lastFrameHeight = 0
        settling = false
        settleMinEndTimeMs = 0L
        stableFrameCount = 0
    }

    fun requestRebaseline() {
        referenceFrame = null
        settling = false
        stableFrameCount = 0
    }

    fun enterSettleMode(cooldownMs: Long, nowMs: Long = System.currentTimeMillis()) {
        settling = true
        settleMinEndTimeMs = nowMs + cooldownMs.coerceAtLeast(350L)
        stableFrameCount = 0
        referenceFrame = null
    }

    fun evaluateFrame(
        pixels: IntArray,
        width: Int,
        height: Int,
        mode: DetectionMode,
        thresholdPercent: Float,
        pixelTolerance: Int = 26,
        nowMs: Long = System.currentTimeMillis()
    ): FrameEvaluationResult {
        if (pixels.isEmpty() || width <= 0 || height <= 0) {
            return FrameEvaluationResult(0f, thresholdMet = false, baselineCapturedNow = false)
        }

        val dimensionsChanged = width != lastFrameWidth || height != lastFrameHeight
        if (dimensionsChanged) {
            referenceFrame = null
            previousFrame = null
            lastFrameWidth = width
            lastFrameHeight = height
            settling = false
            stableFrameCount = 0
        }

        if (settling) {
            val prev = previousFrame
            val motion = if (prev != null && prev.size == pixels.size) {
                computeChangedPixelPercent(prev, pixels, pixelTolerance)
            } else {
                100f
            }
            previousFrame = pixels.copyOf()

            if (nowMs >= settleMinEndTimeMs) {
                if (motion <= 2.5f) {
                    stableFrameCount++
                } else {
                    stableFrameCount = 0
                }
                if (stableFrameCount >= 1) {
                    settling = false
                    referenceFrame = pixels.copyOf()
                    return FrameEvaluationResult(
                        changePercent = 0f,
                        thresholdMet = false,
                        baselineCapturedNow = true,
                        isSettling = false
                    )
                }
            }
            return FrameEvaluationResult(
                changePercent = 0f,
                thresholdMet = false,
                baselineCapturedNow = false,
                isSettling = true
            )
        }

        when (mode) {
            DetectionMode.BASELINE_DIFF -> {
                val baseline = referenceFrame
                if (baseline == null || baseline.size != pixels.size) {
                    referenceFrame = pixels.copyOf()
                    previousFrame = pixels.copyOf()
                    return FrameEvaluationResult(
                        changePercent = 0f,
                        thresholdMet = false,
                        baselineCapturedNow = true
                    )
                }
                val percent = computeChangedPixelPercent(baseline, pixels, pixelTolerance)
                previousFrame = pixels.copyOf()
                return FrameEvaluationResult(
                    changePercent = percent,
                    thresholdMet = percent >= thresholdPercent,
                    baselineCapturedNow = false
                )
            }

            DetectionMode.FRAME_MOTION -> {
                val prev = previousFrame
                if (prev == null || prev.size != pixels.size) {
                    previousFrame = pixels.copyOf()
                    if (referenceFrame == null) {
                        referenceFrame = pixels.copyOf()
                    }
                    return FrameEvaluationResult(
                        changePercent = 0f,
                        thresholdMet = false,
                        baselineCapturedNow = true
                    )
                }
                val percent = computeChangedPixelPercent(prev, pixels, pixelTolerance)
                previousFrame = pixels.copyOf()
                return FrameEvaluationResult(
                    changePercent = percent,
                    thresholdMet = percent >= thresholdPercent,
                    baselineCapturedNow = false
                )
            }

            DetectionMode.BRIGHTNESS_SHIFT -> {
                val baseline = referenceFrame
                if (baseline == null || baseline.size != pixels.size) {
                    referenceFrame = pixels.copyOf()
                    previousFrame = pixels.copyOf()
                    return FrameEvaluationResult(
                        changePercent = 0f,
                        thresholdMet = false,
                        baselineCapturedNow = true
                    )
                }
                val percent = computeLuminanceShiftPercent(baseline, pixels)
                previousFrame = pixels.copyOf()
                return FrameEvaluationResult(
                    changePercent = percent,
                    thresholdMet = percent >= thresholdPercent,
                    baselineCapturedNow = false
                )
            }
        }
    }

    companion object {
        fun computeChangedPixelPercent(
            frameA: IntArray,
            frameB: IntArray,
            pixelTolerance: Int
        ): Float {
            val count = minOf(frameA.size, frameB.size)
            if (count == 0) return 0f
            var changed = 0
            val tol = pixelTolerance.coerceIn(1, 255)

            for (i in 0 until count) {
                val a = frameA[i]
                val b = frameB[i]
                val dr = abs(((a ushr 16) and 0xFF) - ((b ushr 16) and 0xFF))
                val dg = abs(((a ushr 8) and 0xFF) - ((b ushr 8) and 0xFF))
                val db = abs((a and 0xFF) - (b and 0xFF))
                val maxDelta = maxOf(dr, maxOf(dg, db))
                if (maxDelta >= tol) {
                    changed++
                }
            }
            return (changed * 100f) / count.toFloat()
        }

        fun computeLuminanceShiftPercent(
            frameA: IntArray,
            frameB: IntArray
        ): Float {
            val count = minOf(frameA.size, frameB.size)
            if (count == 0) return 0f
            var sumA = 0.0
            var sumB = 0.0
            for (i in 0 until count) {
                val a = frameA[i]
                val b = frameB[i]
                val lumA = 0.299 * ((a ushr 16) and 0xFF) +
                    0.587 * ((a ushr 8) and 0xFF) +
                    0.114 * (a and 0xFF)
                val lumB = 0.299 * ((b ushr 16) and 0xFF) +
                    0.587 * ((b ushr 8) and 0xFF) +
                    0.114 * (b and 0xFF)
                sumA += lumA
                sumB += lumB
            }
            val meanA = sumA / count
            val meanB = sumB / count
            return ((abs(meanA - meanB) / 255.0) * 100.0).toFloat().coerceIn(0f, 100f)
        }
    }
}

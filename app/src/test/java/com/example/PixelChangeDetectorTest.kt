package com.example

import com.example.engine.PixelChangeDetector
import com.example.model.DetectionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelChangeDetectorTest {

    @Test
    fun baselineDiff_neverTriggersWhenImageDoesNotChange() {
        val detector = PixelChangeDetector()
        val staticFrame = IntArray(100) { 0x00102030 }

        val first = detector.evaluateFrame(staticFrame, 10, 10, DetectionMode.BASELINE_DIFF, 15f, nowMs = 100L)
        assertTrue(first.baselineCapturedNow)
        assertFalse(first.thresholdMet)

        for (step in 1..10) {
            val eval = detector.evaluateFrame(
                staticFrame.copyOf(),
                10,
                10,
                DetectionMode.BASELINE_DIFF,
                15f,
                nowMs = 100L + step * 200L
            )
            assertEquals(0f, eval.changePercent, 0.01f)
            assertFalse(eval.thresholdMet)
        }
    }

    @Test
    fun baselineDiff_tapsOnlyOncePerChangeAndSettlesBeforeNextTrigger() {
        val detector = PixelChangeDetector()
        val stateA = IntArray(100) { 0x00102030 }
        val stateB = IntArray(100) { 0x0000E5FF }

        detector.evaluateFrame(stateA, 10, 10, DetectionMode.BASELINE_DIFF, 20f, nowMs = 1000L)

        // First visual change -> triggers once
        val triggerEval = detector.evaluateFrame(stateB, 10, 10, DetectionMode.BASELINE_DIFF, 20f, nowMs = 1200L)
        assertTrue(triggerEval.thresholdMet)
        detector.enterSettleMode(cooldownMs = 800L, nowMs = 1200L)

        // While staying in stateB during and after cooldown -> settles and does NOT tap again
        val midCooldown = detector.evaluateFrame(stateB, 10, 10, DetectionMode.BASELINE_DIFF, 20f, nowMs = 1500L)
        assertFalse(midCooldown.thresholdMet)
        assertTrue(midCooldown.isSettling)

        val settled = detector.evaluateFrame(stateB, 10, 10, DetectionMode.BASELINE_DIFF, 20f, nowMs = 2100L)
        assertFalse(settled.thresholdMet)
        assertTrue(settled.baselineCapturedNow)

        // Staying in stateB afterwards -> 0% change, no tap
        val stillB = detector.evaluateFrame(stateB, 10, 10, DetectionMode.BASELINE_DIFF, 20f, nowMs = 2400L)
        assertEquals(0f, stillB.changePercent, 0.01f)
        assertFalse(stillB.thresholdMet)

        // Only when image changes AGAIN (back to stateA) does it trigger once more
        val nextChange = detector.evaluateFrame(stateA, 10, 10, DetectionMode.BASELINE_DIFF, 20f, nowMs = 2600L)
        assertTrue(nextChange.thresholdMet)
    }

    @Test
    fun frameMotion_comparesConsecutiveFrames() {
        val detector = PixelChangeDetector()
        val dark = IntArray(16) { 0x00000000 }
        val bright = IntArray(16) { 0x00FFFFFF }

        val initResult = detector.evaluateFrame(dark, 4, 4, DetectionMode.FRAME_MOTION, 50f)
        assertFalse(initResult.thresholdMet)

        val motionResult = detector.evaluateFrame(bright, 4, 4, DetectionMode.FRAME_MOTION, 50f)
        assertEquals(100f, motionResult.changePercent, 0.01f)
        assertTrue(motionResult.thresholdMet)

        // Holding steady on bright frame -> 0% motion
        val settledResult = detector.evaluateFrame(bright, 4, 4, DetectionMode.FRAME_MOTION, 50f)
        assertEquals(0f, settledResult.changePercent, 0.01f)
        assertFalse(settledResult.thresholdMet)
    }

    @Test
    fun brightnessShift_detectsMeanLuminanceChange() {
        val detector = PixelChangeDetector()
        val black = IntArray(25) { 0x00000000 }
        val white = IntArray(25) { 0x00FFFFFF }

        detector.evaluateFrame(black, 5, 5, DetectionMode.BRIGHTNESS_SHIFT, 30f)
        val result = detector.evaluateFrame(white, 5, 5, DetectionMode.BRIGHTNESS_SHIFT, 30f)
        assertEquals(100f, result.changePercent, 0.5f)
        assertTrue(result.thresholdMet)
    }

    @Test
    fun resizeWatchSquare_resetsBaselineSafely() {
        val detector = PixelChangeDetector()
        val frameSmall = IntArray(9) { 0x00112233 }
        val frameLarge = IntArray(25) { 0x00FFFFFF }

        detector.evaluateFrame(frameSmall, 3, 3, DetectionMode.BASELINE_DIFF, 10f)
        val resized = detector.evaluateFrame(frameLarge, 5, 5, DetectionMode.BASELINE_DIFF, 10f)

        assertTrue(resized.baselineCapturedNow)
        assertFalse(resized.thresholdMet)
        assertEquals(0f, resized.changePercent, 0.01f)
    }
}

package com.attachdesign.kern.ui.editor

import org.junit.Assert.*
import org.junit.Test

class FoldGestureRecognizerTest {
    private val recognizer = FoldGestureRecognizer()
    private var time = 0L
    private var commits = 0
    private fun hold(angle: Float, duration: Long) {
        repeat((duration / 50).toInt()) {
            if (recognizer.onAngle(angle, time)) commits++
            time += 50
        }
    }
    private fun gesture() { hold(180f, 400); hold(145f, 200); hold(180f, 200) }

    @Test fun `two deliberate gestures commit once each after cooldown`() {
        gesture()
        assertEquals(1, commits)
        hold(180f, 1200)
        gesture()
        assertEquals(2, commits)
    }
    @Test fun `gentle bend works when a flat hinge reports less than 175 degrees`() {
        hold(173f, 500)
        hold(158f, 200)
        hold(165f, 1500)
        hold(173f, 200)
        assertEquals(1, commits)
    }
    @Test fun `initial unfolding and noise near flat never toggle`() {
        hold(100f, 500); hold(180f, 500)
        repeat(8) { hold(171f, 100); hold(178f, 100) }
        assertEquals(0, commits)
    }
    @Test fun `brief jitter at bend threshold does not arm`() {
        hold(180f, 400)
        repeat(5) { hold(155f, 50); hold(165f, 50) }
        hold(180f, 200)
        assertEquals(0, commits)
    }
    @Test fun `sustained book posture cancels until a fresh baseline`() {
        hold(180f, 400); hold(140f, 3000); hold(180f, 200)
        assertEquals(0, commits)
        hold(180f, 500); hold(145f, 200); hold(180f, 200)
        assertEquals(1, commits)
    }
    @Test fun `full closure does not toggle on reopen`() {
        hold(180f, 400); hold(140f, 200); hold(20f, 300); hold(180f, 600)
        assertEquals(0, commits)
    }
    @Test fun `lifecycle reset discards an armed gesture`() {
        hold(180f, 400); hold(140f, 200); recognizer.reset(); hold(180f, 600)
        assertEquals(0, commits)
    }
    @Test fun `stale and invalid readings cancel recognition`() {
        hold(180f, 400); hold(140f, 200); time += 1000; hold(180f, 200)
        assertEquals(0, commits)
        hold(180f, 400); hold(140f, 200); hold(Float.NaN, 50); hold(180f, 200)
        assertEquals(0, commits)
    }
    @Test fun `reopening must settle and cooldown suppresses repeats`() {
        hold(180f, 400); hold(145f, 200); hold(180f, 50); hold(165f, 100)
        assertEquals(0, commits)
        hold(180f, 200)
        assertEquals(1, commits)
        hold(145f, 200); hold(180f, 200)
        assertEquals(1, commits)
    }
    @Test fun `split requires readable panes and excludes horizontal folds`() {
        assertTrue(CompanionLayoutPolicy.canSplit(800f))
        assertFalse(CompanionLayoutPolicy.canSplit(600f))
        assertFalse(CompanionLayoutPolicy.canSplit(800f, 300f, 480f))
        assertFalse(CompanionLayoutPolicy.canSplit(800f, fontScale = 1.5f))
        assertFalse(CompanionLayoutPolicy.canSplit(900f, horizontalFold = true))
    }
}

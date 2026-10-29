package com.attachdesign.kern.ui.editor

enum class FoldShortcutAction(val label: String) {
    METRICS("Reader metrics (Hemingway)"),
    SECOND_EDITOR("Second editor");

    companion object {
        // Existing installations retain their second-editor shortcut.
        fun fromSetting(value: String?): FoldShortcutAction = entries.firstOrNull { it.name == value } ?: SECOND_EDITOR
    }
}

/** Event-driven hinge gesture, aligned with Study Companion's proven physical interaction. */
class FoldGestureRecognizer {
    companion object {
        private const val FLAT_START_ANGLE = 162f
        private const val CREASE_MIN_ANGLE = 95f
        private const val CREASE_MAX_ANGLE = 158f
        const val FLAT_ANGLE = 165f
        private const val ABORT_ANGLE = 80f
        private const val MIN_FLEX_MS = 150L
        private const val MAX_FLEX_MS = 4500L
        private const val COOLDOWN_MS = 1000L
    }

    private var hasStartedFlat = false
    private var flexStartedAt = -1L
    private var lastTriggerAt = -1L
    private var previousTime = -1L
    var bendProgress: Float = 0f
        private set

    fun reset() {
        cancelGesture()
        lastTriggerAt = -1L
        previousTime = -1L
    }

    private fun cancelGesture() {
        hasStartedFlat = false
        flexStartedAt = -1L
        bendProgress = 0f
    }

    /** Each actual sensor event advances recognition; stationary samples are unnecessary. */
    fun onAngle(angle: Float, timeMillis: Long): Boolean {
        if (timeMillis < 0 || (previousTime >= 0 && timeMillis < previousTime)) {
            reset()
            return false
        }
        previousTime = timeMillis
        if (!angle.isFinite() || angle !in 0f..180f || angle < ABORT_ANGLE) {
            cancelGesture()
            return false
        }

        if (flexStartedAt < 0) {
            if (angle >= FLAT_START_ANGLE) {
                hasStartedFlat = true
            } else if (hasStartedFlat && angle in CREASE_MIN_ANGLE..CREASE_MAX_ANGLE) {
                flexStartedAt = timeMillis
            }
        } else {
            val elapsed = timeMillis - flexStartedAt
            if (elapsed > MAX_FLEX_MS) {
                cancelGesture()
                hasStartedFlat = angle >= FLAT_START_ANGLE
            } else if (angle >= FLAT_ANGLE) {
                val trigger = elapsed >= MIN_FLEX_MS &&
                    (lastTriggerAt < 0 || timeMillis - lastTriggerAt >= COOLDOWN_MS)
                flexStartedAt = -1L
                hasStartedFlat = true
                bendProgress = 0f
                if (trigger) lastTriggerAt = timeMillis
                return trigger
            }
        }
        bendProgress = if (flexStartedAt >= 0) {
            ((FLAT_ANGLE - angle) / (FLAT_ANGLE - CREASE_MAX_ANGLE)).coerceIn(0f, 1f)
        } else 0f
        return false
    }
}

/** Geometry uses local available space, rather than the device model or screen's physical size. */
object CompanionLayoutPolicy {
    fun canSplit(widthDp: Float, firstPaneDp: Float = widthDp / 2, secondPaneDp: Float = widthDp / 2,
                 fontScale: Float = 1f, horizontalFold: Boolean = false): Boolean {
        val minimumPane = 340f * fontScale.coerceAtLeast(1f)
        return !horizontalFold && widthDp >= 720f && firstPaneDp >= minimumPane && secondPaneDp >= minimumPane
    }
}

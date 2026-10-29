package com.attachdesign.kern.ui.editor

enum class FoldShortcutAction(val label: String) {
    METRICS("Reader metrics (Hemingway)"),
    SECOND_EDITOR("Second editor");

    companion object {
        // Existing installations retain their second-editor shortcut.
        fun fromSetting(value: String?): FoldShortcutAction = entries.firstOrNull { it.name == value } ?: SECOND_EDITOR
    }
}

/** A deliberate bend-and-return command, separate from posture-driven layout. Times are monotonic. */
class FoldGestureRecognizer {
    companion object {
        const val FLAT_ANGLE = 170f
        private const val BEND_ANGLE = 160f
    }

    private enum class Phase { BASELINE, READY, BENDING, ARMED, REOPENING, COOLDOWN }
    private var phase = Phase.BASELINE
    private var since = -1L
    private var armedAt = -1L
    private var previousTime = -1L
    var bendProgress: Float = 0f
        private set

    fun reset() {
        phase = Phase.BASELINE
        since = -1L
        armedAt = -1L
        previousTime = -1L
        bendProgress = 0f
    }

    /** Returns true once, only after flat -> brief bend -> stable flat. */
    fun onAngle(angle: Float, timeMillis: Long): Boolean {
        if (!angle.isFinite() || angle !in 0f..180f ||
            (previousTime >= 0 && (timeMillis < previousTime || timeMillis - previousTime > 500))
        ) {
            reset()
        }
        previousTime = timeMillis
        if (!angle.isFinite() || angle !in 90f..180f) {
            reset()
            return false
        }
        bendProgress = if (phase in setOf(Phase.READY, Phase.BENDING, Phase.ARMED, Phase.REOPENING)) {
            ((FLAT_ANGLE - angle) / (FLAT_ANGLE - BEND_ANGLE)).coerceIn(0f, 1f)
        } else 0f
        if (phase in setOf(Phase.ARMED, Phase.REOPENING) && timeMillis - armedAt > 2500) {
            reset()
            return false
        }
        when (phase) {
            Phase.BASELINE -> if (angle >= FLAT_ANGLE) {
                if (since < 0) since = timeMillis
                if (timeMillis - since >= 300) phase = Phase.READY
            } else since = -1L
            Phase.READY -> if (angle <= BEND_ANGLE) {
                phase = Phase.BENDING
                since = timeMillis
            }
            Phase.BENDING -> if (angle > BEND_ANGLE) {
                phase = Phase.READY
            } else if (timeMillis - since >= 100) {
                phase = Phase.ARMED
                armedAt = since
            }
            Phase.ARMED -> if (angle >= FLAT_ANGLE) {
                phase = Phase.REOPENING
                since = timeMillis
            }
            Phase.REOPENING -> if (angle < FLAT_ANGLE) {
                phase = Phase.ARMED
            } else if (timeMillis - since >= 100) {
                phase = Phase.COOLDOWN
                since = timeMillis
                bendProgress = 0f
                return true
            }
            Phase.COOLDOWN -> if (timeMillis - since >= 700) {
                phase = Phase.BASELINE
                since = if (angle >= FLAT_ANGLE) timeMillis else -1L
            }
        }
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

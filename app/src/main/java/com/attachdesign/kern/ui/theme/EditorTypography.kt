package com.attachdesign.kern.ui.theme

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** Document metrics are independent of the application's denser interface typography. */
object EditorTypography {
    const val DEFAULT_SCALE = 1f
    const val MIN_SCALE = 0.8f
    const val MAX_SCALE = 1.4f

    private val mediumFontSize = 16.sp
    private const val LINE_HEIGHT_RATIO = 1.6f

    fun normalizeScale(settingValue: String?): Float =
        normalizeScale(settingValue?.toFloatOrNull() ?: DEFAULT_SCALE)

    fun normalizeScale(scale: Float): Float =
        if (scale.isFinite()) scale.coerceIn(MIN_SCALE, MAX_SCALE) else DEFAULT_SCALE

    fun metrics(scale: Float = DEFAULT_SCALE): EditorTextMetrics {
        val fontSize = mediumFontSize * normalizeScale(scale)
        return EditorTextMetrics(fontSize = fontSize, lineHeight = fontSize * LINE_HEIGHT_RATIO)
    }
}

data class EditorTextMetrics(val fontSize: TextUnit, val lineHeight: TextUnit)

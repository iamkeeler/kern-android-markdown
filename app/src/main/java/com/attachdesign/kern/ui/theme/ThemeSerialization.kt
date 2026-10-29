package com.attachdesign.kern.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.util.Locale

/** Exports declared palette values, rather than Compose's packed internal color storage. */
fun AppColorTheme.toThemeJson(): AppThemeJson = AppThemeJson(
    name = name,
    isDark = isDark,
    backgroundHex = background.toThemeHex(),
    surfaceHex = surface.toThemeHex(),
    textPrimaryHex = textPrimary.toThemeHex(),
    textMutedHex = textMuted.toThemeHex(),
    accentHex = accent.toThemeHex(),
    codeBackgroundHex = codeBackground.toThemeHex(),
    dangerHex = danger.toThemeHex(),
    warningHex = warning.toThemeHex(),
    infoHex = info.toThemeHex(),
    successHex = success.toThemeHex(),
    editorFontFamily = editorFontFamily,
    headerDividerHex = headerDivider.toThemeHex()
)

private fun Color.toThemeHex(): String {
    val argb = toArgb()
    return if ((argb ushr 24) == 0xFF) {
        String.format(Locale.ROOT, "#%06X", argb and 0xFFFFFF)
    } else {
        String.format(Locale.ROOT, "#%08X", argb)
    }
}

package com.attachdesign.kern.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Runtime presentation only: none of these roles are serialized or written to preset rows. */
internal data class AccessibleThemeRoles(
    val background: Color,
    val surface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textAccent: Color,
    val textDanger: Color,
    val controlAccent: Color,
    val onControlAccent: Color,
    val indicator: Color,
    val inverseSurface: Color,
    val inverseOnSurface: Color,
    val inversePrimary: Color,
)

internal fun accessibleThemeRoles(theme: AppColorTheme): AccessibleThemeRoles {
    // Resolve translucent imports against a deterministic app backing before testing contrast.
    val background = theme.background.compositeOver(if (theme.isDark) Color.Black else Color.White)
    val surface = theme.surface.compositeOver(background)
    val surfaces = listOf(background, surface)
    val primary = readableColor(theme.textPrimary, surfaces, 4.5)
    val accent = readableColor(theme.accent, surfaces, 4.5)
    // A fill must be opaque so its on-color also works when a control reverses the pair
    // (for example the checked switch icon and the primary-container foreground).
    val controlAccent = readableColor(theme.accent.copy(alpha = 1f), surfaces, 4.5)
    val inverseSurface = primary.compositeOver(background)
    return AccessibleThemeRoles(
        background = background,
        surface = surface,
        textPrimary = primary,
        textSecondary = readableColor(theme.textMuted, surfaces, 4.5),
        textAccent = accent,
        textDanger = readableColor(theme.danger, surfaces, 4.5),
        // Material primary is also used for text buttons, so it needs the stronger text ratio.
        controlAccent = controlAccent,
        onControlAccent = readableColor(background, listOf(controlAccent), 4.5),
        indicator = readableColor(theme.textMuted, surfaces, 3.0),
        inverseSurface = inverseSurface,
        inverseOnSurface = readableColor(background, listOf(inverseSurface), 4.5),
        inversePrimary = readableColor(theme.accent, listOf(inverseSurface), 4.5),
    )
}

/** WCAG contrast after compositing both layers; [backdrop] is the opaque host backing. */
internal fun contrastRatio(
    foreground: Color,
    background: Color,
    backdrop: Color = Color.White,
): Double {
    val renderedBackground = background.compositeOver(backdrop)
    val renderedForeground = foreground.compositeOver(renderedBackground)
    val first = relativeLuminance(renderedForeground)
    val second = relativeLuminance(renderedBackground)
    return (max(first, second) + 0.05) / (min(first, second) + 0.05)
}

private fun relativeLuminance(color: Color): Double {
    fun linear(channel: Float): Double {
        val value = channel.toDouble()
        return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * linear(color.red) + 0.7152 * linear(color.green) + 0.0722 * linear(color.blue)
}

/**
 * Retain a passing token exactly; otherwise move only as far toward black/white as necessary.
 * Alpha participates in the adjustment, so an invisible imported token cannot pass by its RGB.
 * Arbitrary custom palettes can contain mutually incompatible surfaces. In that case this
 * returns the strongest available neutral endpoint, without altering the imported palette.
 */
internal fun readableColor(seed: Color, surfaces: List<Color>, minimum: Double): Color {
    fun minimumContrast(color: Color) = surfaces.minOf { contrastRatio(color, it) }
    if (minimumContrast(seed) >= minimum) return seed

    // A small margin protects the threshold when the GPU converts float channels to 8-bit.
    val targetRatio = minimum + 0.04
    fun toward(target: Color, fraction: Float) = Color(
        red = seed.red + (target.red - seed.red) * fraction,
        green = seed.green + (target.green - seed.green) * fraction,
        blue = seed.blue + (target.blue - seed.blue) * fraction,
        alpha = seed.alpha + (1f - seed.alpha) * fraction,
    )

    val candidates = listOf(Color.Black, Color.White).mapNotNull { target ->
        // Search also handles mixed light/dark imported surfaces where neither endpoint passes.
        val firstPassing = (1..256).firstOrNull {
            minimumContrast(toward(target, it / 256f)) >= targetRatio
        } ?: return@mapNotNull null
        var low = (firstPassing - 1) / 256f
        var high = firstPassing / 256f
        repeat(20) {
            val middle = (low + high) / 2f
            if (minimumContrast(toward(target, middle)) >= targetRatio) high = middle else low = middle
        }
        toward(target, high)
    }
    return candidates.minByOrNull { candidate ->
        abs(candidate.red - seed.red) + abs(candidate.green - seed.green) +
            abs(candidate.blue - seed.blue) + abs(candidate.alpha - seed.alpha)
    } ?: listOf(Color.Black, Color.White).maxBy { minimumContrast(it) }
}

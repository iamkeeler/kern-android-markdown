package com.attachdesign.kern.ui.theme

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * JVM unit tests checking WCAG 2.0 compliance for contrast ratios of the built-in themes.
 */
class ThemeAccessibilityTest {

    // Relative luminance calculation based on WCAG 2.0 formula
    private fun calculateLuminance(hexColor: String): Double {
        val color = hexColor.trim().removePrefix("#")
        val r = color.substring(0, 2).toInt(16) / 255.0
        val g = color.substring(2, 4).toInt(16) / 255.0
        val b = color.substring(4, 6).toInt(16) / 255.0

        val rL = if (r <= 0.03928) r / 12.92 else ((r + 0.055) / 1.055).pow(2.4)
        val gL = if (g <= 0.03928) g / 12.92 else ((g + 0.055) / 1.055).pow(2.4)
        val bL = if (b <= 0.03928) b / 12.92 else ((b + 0.055) / 1.055).pow(2.4)

        return 0.2126 * rL + 0.7152 * gL + 0.0722 * bL
    }

    private fun calculateContrastRatio(color1: String, color2: String): Double {
        val l1 = calculateLuminance(color1)
        val l2 = calculateLuminance(color2)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    @Test
    fun testDefaultLightThemeContrast() {
        val theme = ThemeEngine.DefaultLight
        
        // Text Primary vs Background (WCAG AA: minimum 4.5:1)
        val textBgContrast = calculateContrastRatio(theme.textPrimaryHex, theme.backgroundHex)
        assertTrue("Light theme text vs bg contrast ($textBgContrast) is below 4.5:1", textBgContrast >= 4.5)

        // Text Primary vs Surface (WCAG AA: minimum 4.5:1)
        val textSurfContrast = calculateContrastRatio(theme.textPrimaryHex, theme.surfaceHex)
        assertTrue("Light theme text vs surface contrast ($textSurfContrast) is below 4.5:1", textSurfContrast >= 4.5)

        // Accent vs Background (WCAG: minimum 3.0:1 for active UI controls & focus indicators)
        val accentBgContrast = calculateContrastRatio(theme.accentHex, theme.backgroundHex)
        assertTrue("Light theme accent vs bg contrast ($accentBgContrast) is below 3.0:1", accentBgContrast >= 3.0)
    }

    @Test
    fun testDefaultDarkThemeContrast() {
        val theme = ThemeEngine.DefaultDark
        
        // Text Primary vs Background (WCAG AA: minimum 4.5:1)
        val textBgContrast = calculateContrastRatio(theme.textPrimaryHex, theme.backgroundHex)
        assertTrue("Dark theme text vs bg contrast ($textBgContrast) is below 4.5:1", textBgContrast >= 4.5)

        // Text Primary vs Surface (WCAG AA: minimum 4.5:1)
        val textSurfContrast = calculateContrastRatio(theme.textPrimaryHex, theme.surfaceHex)
        assertTrue("Dark theme text vs surface contrast ($textSurfContrast) is below 4.5:1", textSurfContrast >= 4.5)

        // Accent vs Background (WCAG: minimum 3.0:1 for active UI controls & focus indicators)
        val accentBgContrast = calculateContrastRatio(theme.accentHex, theme.backgroundHex)
        assertTrue("Dark theme accent vs bg contrast ($accentBgContrast) is below 3.0:1", accentBgContrast >= 3.0)
    }

    @Test
    fun activeThemeDrivesAccessibleSystemBarIcons() {
        val light = systemBarUsesDarkIcons(ThemeEngine.DefaultLight.isDark)
        val dark = systemBarUsesDarkIcons(ThemeEngine.DefaultDark.isDark)

        assertTrue("Light system bars need dark icons", light)
        assertTrue("Dark system bars need light icons", !dark)
    }
}

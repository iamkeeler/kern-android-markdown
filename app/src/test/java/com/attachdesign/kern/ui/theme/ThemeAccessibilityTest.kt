package com.attachdesign.kern.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests declared palette contracts separately from the roles actually rendered by controls. */
class ThemeAccessibilityTest {
    private val themes = listOf(ThemeEngine.DefaultLight, ThemeEngine.DefaultDark).map { it.toColorTheme() }

    @Test
    fun builtInDeclaredPrimaryAndInteractiveAccentRetainContrast() {
        themes.forEach { theme ->
            listOf(theme.background, theme.surface).forEach { surface ->
                assertContrast(theme.textPrimary, surface, 4.5)
                assertContrast(theme.accent, surface, 3.0)
            }
        }
    }

    @Test
    fun builtInSmallTextRolesMeetAAOnCanvasAndSurface() {
        themes.forEach { theme ->
            listOf(theme.textPrimary, theme.textSecondary, theme.textAccent, theme.textDanger).forEach { text ->
                listOf(theme.background, theme.surface).forEach { surface ->
                    assertContrast(text, surface, 4.5)
                }
            }
        }
    }

    @Test
    fun indicatorsAndControlForegroundsMeetTheirActualSurfaceRequirements() {
        themes.forEach { theme ->
            listOf(theme.background, theme.surface).forEach { surface ->
                assertContrast(theme.controlAccent, surface, 3.0)
                assertContrast(theme.indicator, surface, 3.0)
                assertContrast(theme.onControlAccent, theme.controlAccent.compositeOver(surface), 4.5)
            }
        }
    }

    @Test
    fun translucentTextIsMeasuredAfterCompositing() {
        assertEquals(1.0, contrastRatio(Color.White.copy(alpha = 0f), Color.Black), 0.0001)
        // Compose's sRGB color stores alpha at 8-bit precision: 0.5 becomes 128/255.
        assertEquals(5.3172, contrastRatio(Color.White.copy(alpha = 0.5f), Color.Black), 0.001)
        assertEquals(21.0, contrastRatio(Color.White, Color.Black), 0.0001)
        val translucentSurface = Color.Black.copy(alpha = 0.5f)
        assertEquals(
            contrastRatio(Color.White, translucentSurface.compositeOver(Color.White)),
            contrastRatio(Color.White, translucentSurface, Color.White),
            0.0001,
        )
    }

    @Test
    fun transparentImportedTokensProduceVisibleRolesOnResolvedSurfaces() {
        themes.forEach { base ->
            val theme = base.copy(
                background = base.background.copy(alpha = 0.8f),
                surface = base.surface.copy(alpha = 0.5f),
                textPrimary = base.textPrimary.copy(alpha = 0f),
                textMuted = base.textMuted.copy(alpha = 0.1f),
                accent = base.accent.copy(alpha = 0f),
                danger = base.danger.copy(alpha = 0.15f),
            )
            val roles = theme.accessibleRoles
            listOf(roles.background, roles.surface).forEach { surface ->
                listOf(roles.textPrimary, theme.textSecondary, theme.textAccent, theme.textDanger).forEach {
                    assertContrast(it, surface, 4.5)
                }
                assertContrast(theme.indicator, surface, 3.0)
                assertContrast(theme.onControlAccent, theme.controlAccent.compositeOver(surface), 4.5)
            }
        }
    }

    @Test
    fun readableTokensRemainExactAndHelperMetricsDoNotEnlargeTinyOrBody() {
        themes.forEach { theme ->
            assertEquals(theme.textPrimary, theme.accessibleRoles.textPrimary)
            assertEquals(theme.background, theme.accessibleRoles.background)
            assertEquals(theme.surface, theme.accessibleRoles.surface)
        }
        val sizes = AppTypographySizes()
        assertEquals(11.sp, sizes.tiny)
        assertEquals(13.sp, sizes.body)
        assertEquals(13.sp, sizes.helper)
        assertEquals(18.sp, sizes.helperLineHeight)
    }

    @Test
    fun activeThemeDrivesAccessibleSystemBarIcons() {
        assertTrue("Light system bars need dark icons", systemBarUsesDarkIcons(ThemeEngine.DefaultLight.isDark))
        assertTrue("Dark system bars need light icons", !systemBarUsesDarkIcons(ThemeEngine.DefaultDark.isDark))
    }

    private fun assertContrast(foreground: Color, background: Color, minimum: Double) {
        val ratio = contrastRatio(foreground, background)
        assertTrue("$foreground on $background: $ratio must be at least $minimum", ratio >= minimum)
    }
}

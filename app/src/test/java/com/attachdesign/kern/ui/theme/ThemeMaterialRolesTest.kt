package com.attachdesign.kern.ui.theme

import androidx.compose.ui.graphics.Color
import com.attachdesign.kern.theme.toMaterialColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeMaterialRolesTest {
    private val themes = listOf(ThemeEngine.DefaultLight, ThemeEngine.DefaultDark).map { it.toColorTheme() }

    @Test
    fun menusAndDialogsUseThemeSurfacesAndReadableTextInsteadOfFallbackTones() {
        themes.forEach { theme ->
            val scheme = theme.toMaterialColorScheme()
            val surfaces = listOf(scheme.background, scheme.surface, scheme.surfaceVariant,
                scheme.surfaceContainer, scheme.surfaceContainerLow, scheme.surfaceContainerHigh,
                scheme.surfaceContainerHighest)
            surfaces.forEach { surface ->
                assertTrue(surface == theme.background || surface == theme.surface)
                listOf(scheme.onSurface, scheme.onSurfaceVariant, scheme.primary, scheme.error).forEach {
                    assertContrast(it, surface, 4.5)
                }
            }
            assertEquals(theme.surface, scheme.surfaceTint)
            assertEquals(theme.textSecondary, scheme.onSurfaceVariant)
            assertEquals(theme.textDanger, scheme.error)
        }
    }

    @Test
    fun materialControlPairsCoverSwitchThumbTrackOutlineAndTextButtons() {
        themes.forEach { theme ->
            val scheme = theme.toMaterialColorScheme()
            assertEquals(theme.controlAccent, scheme.primary)
            assertEquals(theme.onControlAccent, scheme.onPrimary)
            // Default checked switch: onPrimary thumb over primary track; optional icon uses
            // onPrimaryContainer. Unchecked: outline thumb/border over surfaceContainerHighest.
            assertContrast(scheme.onPrimary, scheme.primary, 4.5)
            assertContrast(scheme.onPrimaryContainer, scheme.onPrimary, 3.0)
            assertContrast(scheme.outline, scheme.surfaceContainerHighest, 3.0)
            assertContrast(scheme.onSurfaceVariant, scheme.surfaceContainerHighest, 3.0)
            listOf(scheme.background, scheme.surface).forEach { surface ->
                assertContrast(scheme.primary, surface, 4.5)
                assertContrast(scheme.outline, surface, 3.0)
            }
            assertContrast(scheme.onPrimaryContainer, scheme.primaryContainer, 4.5)
            assertContrast(scheme.onSecondary, scheme.secondary, 4.5)
            assertContrast(scheme.onSecondaryContainer, scheme.secondaryContainer, 4.5)
            assertContrast(scheme.onError, scheme.error, 4.5)
            assertContrast(scheme.onErrorContainer, scheme.errorContainer, 4.5)
        }
    }

    @Test
    fun inverseSnackbarContentAndActionRemainReadableInBothThemes() {
        themes.forEach { theme ->
            val scheme = theme.toMaterialColorScheme()
            assertContrast(scheme.inverseOnSurface, scheme.inverseSurface, 4.5)
            assertContrast(scheme.inversePrimary, scheme.inverseSurface, 4.5)
            assertEquals(theme.accessibleRoles.inverseSurface, scheme.inverseSurface)
        }
    }

    @Test
    fun translucentImportedAccentKeepsBothDirectionsOfTheMaterialControlPairReadable() {
        themes.forEach { base ->
            listOf(0f, 0.1f, 0.5f).forEach { alpha ->
                val theme = base.copy(accent = base.accent.copy(alpha = alpha))
                val declaredAlpha = theme.accent.alpha
                val scheme = theme.toMaterialColorScheme()
                assertEquals(declaredAlpha, theme.accent.alpha, 0f)
                assertEquals(1f, scheme.primary.alpha, 0.0001f)
                assertContrast(scheme.onPrimary, scheme.primary, 4.5)
                assertContrast(scheme.onPrimaryContainer, scheme.onPrimary, 4.5)
                assertContrast(scheme.onPrimaryContainer, scheme.primaryContainer, 4.5)
                assertContrast(scheme.inversePrimary, scheme.inverseSurface, 4.5)
                listOf(scheme.background, scheme.surface).forEach { surface ->
                    assertContrast(scheme.primary, surface, 4.5)
                }
            }
        }
    }

    private fun assertContrast(foreground: Color, background: Color, minimum: Double) {
        val ratio = contrastRatio(foreground, background)
        assertTrue("$foreground on $background: $ratio must be at least $minimum", ratio >= minimum)
    }
}

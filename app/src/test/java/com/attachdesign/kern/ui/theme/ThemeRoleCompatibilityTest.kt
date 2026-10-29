package com.attachdesign.kern.ui.theme

import androidx.compose.ui.graphics.Color
import com.attachdesign.kern.theme.toMaterialColorScheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ThemeRoleCompatibilityTest {
    @Test
    fun oldBuiltInPresetRowsKeepTheirDeclaredPalettesWhileGainingPresentationRoles() {
        // Existing rows contain this palette schema, rather than a reliable built-in identity.
        val rows = listOf(
            """{"name":"Cream & Charcoal","isDark":false,"backgroundHex":"#F7F3EB","surfaceHex":"#EDE8DC","textPrimaryHex":"#1C1C1A","textMutedHex":"#7A7060","accentHex":"#C8541A","codeBackgroundHex":"#E8E2D6","editorFontFamily":"serif"}""",
            """{"name":"Inky Charcoal","isDark":true,"backgroundHex":"#1C1C1A","surfaceHex":"#242420","textPrimaryHex":"#F5F0E8","textMutedHex":"#A89F8C","accentHex":"#C8541A","codeBackgroundHex":"#2A2925","editorFontFamily":"serif"}""",
        )
        rows.forEach { row ->
            val parsed = ThemeEngine.deserialize(row)
            assertNotNull(parsed)
            val declared = parsed!!.toColorTheme()
            val before = declared.toThemeJson()
            declared.toMaterialColorScheme()
            declared.textSecondary
            declared.textAccent
            declared.textDanger
            assertEquals(before, declared.toThemeJson())
            assertEquals(parsed, ThemeEngine.deserialize(ThemeEngine.serialize(parsed)))
            assertEquals(Color(0xFFC8541A), declared.accent)
            assertNotEquals(declared.accent, declared.textAccent)
        }
    }

    @Test
    fun customPaletteWithBuiltInNameAndAlphaIsNeverReplacedOrExportedAsDerivedRoles() {
        val custom = ThemeEngine.DefaultLight.copy(
            name = "Inky Charcoal", // A display name is not provenance or permission to migrate.
            textPrimaryHex = "#80223344",
            textMutedHex = "#10234567",
            accentHex = "#20345678",
            dangerHex = "#30456789",
            warningHex = "#4056789A",
            infoHex = "#506789AB",
            successHex = "#60789ABC",
            headerDividerHex = "#7089ABCD",
        )
        val theme = custom.toColorTheme()
        theme.toMaterialColorScheme()
        assertEquals(custom, theme.toThemeJson())
        assertEquals(theme, ThemeEngine.deserialize(ThemeEngine.serialize(theme.toThemeJson()))!!.toColorTheme())
        assertNotEquals(theme.accent, theme.controlAccent)
        assertNotEquals(theme.textMuted, theme.textSecondary)
    }

    @Test
    fun copiedThemeRecomputesRolesWithoutKeepingTheOriginalPaletteCache() {
        val light = ThemeEngine.DefaultLight.toColorTheme()
        light.textAccent
        val changed = light.copy(accent = Color.Blue)
        assertEquals(Color.Blue, changed.textAccent)
        assertNotEquals(light.controlAccent, changed.controlAccent)
        assertEquals(Color.Blue, changed.toThemeJson().toColorTheme().accent)
    }
}

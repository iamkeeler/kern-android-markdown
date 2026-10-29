package com.attachdesign.kern.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ThemeSerializationTest {
    @Test
    fun `export converts packed compose colors to the actual RGB values`() {
        val exported = ThemeEngine.DefaultLight.toColorTheme().toThemeJson()

        assertEquals("#F7F3EB", exported.backgroundHex)
        assertEquals("#1C1C1A", exported.textPrimaryHex)
        assertEquals("#C8541A", exported.accentHex)
    }

    @Test
    fun `export preserves every custom semantic color and alpha`() {
        val theme = ThemeEngine.DefaultDark.toColorTheme().copy(
            name = "Custom palette",
            danger = Color(0xFFB12345),
            warning = Color(0xFFC98765),
            info = Color(0xFF2468AB),
            success = Color(0xFF357924),
            headerDivider = Color(0x80123456),
            editorFontFamily = "monospace"
        )

        val exported = theme.toThemeJson()
        val restored = ThemeEngine.deserialize(ThemeEngine.serialize(exported))!!.toColorTheme()

        assertEquals("#80123456", exported.headerDividerHex)
        assertEquals(theme, restored)
    }

    @Test
    fun `both built in palettes retain all effective values through export and import`() {
        listOf(ThemeEngine.DefaultLight, ThemeEngine.DefaultDark).forEach { preset ->
            val original = preset.toColorTheme()
            val restored = ThemeEngine.deserialize(ThemeEngine.serialize(original.toThemeJson()))
            assertNotNull(restored)
            assertEquals(original, restored!!.toColorTheme())
        }
    }

    @Test
    fun `legacy JSON without semantic colors remains compatible`() {
        val legacy = """{"name":"Legacy","isDark":false,"backgroundHex":"#F7F3EB","surfaceHex":"#EDE8DC","textPrimaryHex":"#1C1C1A","textMutedHex":"#7A7060","accentHex":"#C8541A","codeBackgroundHex":"#E8E2D6"}"""
        val restored = ThemeEngine.deserialize(legacy)

        assertNotNull(restored)
        assertEquals("serif", restored!!.editorFontFamily)
        assertEquals(restored.textPrimaryHex, restored.toColorTheme().toThemeJson().headerDividerHex)
        assertEquals(null, ThemeEngine.deserialize("invalid JSON"))
    }
}

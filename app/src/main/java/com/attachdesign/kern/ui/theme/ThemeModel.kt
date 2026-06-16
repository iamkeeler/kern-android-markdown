package com.attachdesign.kern.ui.theme

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class AppThemeJson(
    val name: String,
    val isDark: Boolean,
    val backgroundHex: String,
    val surfaceHex: String,
    val textPrimaryHex: String,
    val textMutedHex: String,
    val accentHex: String,
    val codeBackgroundHex: String,
    val editorFontFamily: String = "Monospace"
) {
    fun toColorTheme(): AppColorTheme {
        return AppColorTheme(
            name = name,
            isDark = isDark,
            background = Color(android.graphics.Color.parseColor(backgroundHex)),
            surface = Color(android.graphics.Color.parseColor(surfaceHex)),
            textPrimary = Color(android.graphics.Color.parseColor(textPrimaryHex)),
            textMuted = Color(android.graphics.Color.parseColor(textMutedHex)),
            accent = Color(android.graphics.Color.parseColor(accentHex)),
            codeBackground = Color(android.graphics.Color.parseColor(codeBackgroundHex)),
            editorFontFamily = editorFontFamily
        )
    }
}

data class AppColorTheme(
    val name: String,
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val accent: Color,
    val codeBackground: Color,
    val editorFontFamily: String
)

object ThemeEngine {
    
    val DefaultLight = AppThemeJson(
        name = "Cream & Charcoal",
        isDark = false,
        backgroundHex = "#F7F3EB",
        surfaceHex = "#EDE8DC",
        textPrimaryHex = "#1C1C1A",
        textMutedHex = "#7A7060",
        accentHex = "#C8541A",
        codeBackgroundHex = "#E8E2D6",
        editorFontFamily = "Monospace"
    )

    val DefaultDark = AppThemeJson(
        name = "Inky Charcoal",
        isDark = true,
        backgroundHex = "#1C1C1A",
        surfaceHex = "#242420",
        textPrimaryHex = "#F5F0E8",
        textMutedHex = "#A89F8C",
        accentHex = "#C8541A",
        codeBackgroundHex = "#2A2925",
        editorFontFamily = "Monospace"
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun serialize(theme: AppThemeJson): String {
        return json.encodeToString(AppThemeJson.serializer(), theme)
    }

    fun deserialize(jsonString: String): AppThemeJson? {
        return try {
            json.decodeFromString(AppThemeJson.serializer(), jsonString)
        } catch (e: Exception) {
            null
        }
    }
}

val AppColorTheme.appFontFamily: androidx.compose.ui.text.font.FontFamily
    get() = when (this.editorFontFamily.lowercase()) {
        "serif" -> androidx.compose.ui.text.font.FontFamily.Serif
        "sans-serif", "sansserif" -> androidx.compose.ui.text.font.FontFamily.SansSerif
        "monospace" -> androidx.compose.ui.text.font.FontFamily.Monospace
        else -> androidx.compose.ui.text.font.FontFamily.Default
    }

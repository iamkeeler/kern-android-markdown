package com.attachdesign.kern.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    val dangerHex: String = "#CC3333",
    val warningHex: String = "#FFC04D",
    val infoHex: String = "#5CD6D6",
    val successHex: String = "#D65CD6", // using existing purple for passive, wait let's use more semantic ones for hemingway if needed, or just specific hemingway colors
    val editorFontFamily: String = "serif",
    val headerDividerHex: String? = null
) {
    fun toColorTheme(): AppColorTheme {
        val resolvedTextPrimary = parseHexColor(textPrimaryHex, Color(0xFF1C1C1A))
        val resolvedHeaderDivider = headerDividerHex?.let { parseHexColor(it, resolvedTextPrimary) } ?: resolvedTextPrimary
        return AppColorTheme(
            name = name,
            isDark = isDark,
            background = parseHexColor(backgroundHex, Color(0xFFF7F3EB)),
            surface = parseHexColor(surfaceHex, Color(0xFFEDE8DC)),
            textPrimary = resolvedTextPrimary,
            textMuted = parseHexColor(textMutedHex, Color(0xFF6E6A5F)),
            accent = parseHexColor(accentHex, Color(0xFFC85A32)),
            codeBackground = parseHexColor(codeBackgroundHex, Color(0xFFE8E2D6)),
            danger = parseHexColor(dangerHex, Color(0xFFCC3333)),
            warning = parseHexColor(warningHex, Color(0xFFFFC04D)),
            info = parseHexColor(infoHex, Color(0xFF5CD6D6)),
            success = parseHexColor(successHex, Color(0xFFD65CD6)),
            headerDivider = resolvedHeaderDivider,
            editorFontFamily = editorFontFamily,
            dimensions = AppDimensions(),
            typography = AppTypographySizes()
        )
    }
}

private fun parseHexColor(hex: String, fallback: Color): Color {
    val clean = hex.removePrefix("#").trim()
    return try {
        when (clean.length) {
            6 -> Color(0xFF000000 or clean.toLong(16))
            8 -> Color(clean.toLong(16))
            3 -> {
                val r = clean[0].digitToInt(16)
                val g = clean[1].digitToInt(16)
                val b = clean[2].digitToInt(16)
                Color(0xFF000000 or ((r * 17) shl 16).toLong() or ((g * 17) shl 8).toLong() or (b * 17).toLong())
            }
            else -> fallback
        }
    } catch (_: Exception) {
        fallback
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
    val danger: Color,
    val warning: Color,
    val info: Color,
    val success: Color,
    val headerDivider: Color,
    val editorFontFamily: String,
    val dimensions: AppDimensions,
    val typography: AppTypographySizes

)


data class AppDimensions(
    val spacingTiny: Dp = 2.dp,
    val spacingSmall: Dp = 4.dp,
    val spacingMedium: Dp = 8.dp,
    val spacingLarge: Dp = 12.dp,
    val spacingExtraLarge: Dp = 16.dp,
    val spacingHuge: Dp = 24.dp,
    val spacingEnormous: Dp = 32.dp,
    val spacingMassive: Dp = 40.dp,
    val spacingGiant: Dp = 48.dp,
    val spacingTitan: Dp = 70.dp,

    val paddingTiny: Dp = 4.dp,
    val paddingSmall: Dp = 8.dp,
    val paddingMedium: Dp = 12.dp,
    val paddingLarge: Dp = 16.dp,
    val paddingExtraLarge: Dp = 24.dp,

    val cornerRadiusSmall: Dp = 4.dp,
    val cornerRadiusMedium: Dp = 8.dp,
    val cornerRadiusLarge: Dp = 12.dp,

    val elevationSmall: Dp = 2.dp,
    val elevationMedium: Dp = 6.dp,
    val elevationLarge: Dp = 8.dp,

    val iconSmall: Dp = 16.dp,
    val iconMedium: Dp = 20.dp,
    val iconLarge: Dp = 28.dp,
    val iconHuge: Dp = 48.dp,

    val buttonHeight: Dp = 48.dp,
    val borderWidth: Dp = 1.dp,
    val dividerThickness: Dp = 1.dp,

    val dualPaneBreakpoint: Dp = 600.dp,
    val largeScreenBreakpoint: Dp = 720.dp,
    val sidebarWidth: Dp = 320.dp,
    val popupMaxHeight: Dp = 400.dp,
    val maxTextLineWidth: Dp = 680.dp,
    val editorBottomPadding: Dp = 120.dp,
    val swipeActionWidth: Dp = 72.dp,
    val swipeActionRevealWidth: Dp = 216.dp,
    val themeInputHeight: Dp = 100.dp
)

data class AppTypographySizes(
    val tiny: TextUnit = 11.sp,
    val small: TextUnit = 12.sp,
    val body: TextUnit = 13.sp,
    val bodyLarge: TextUnit = 15.sp,
    val subtitle: TextUnit = 16.sp,
    val title: TextUnit = 18.sp,
    val h1: TextUnit = 24.sp,
    val h2: TextUnit = 20.sp,
    val h3: TextUnit = 18.sp,
    val h4: TextUnit = 16.sp,
    val h5: TextUnit = 13.sp,
    val h6: TextUnit = 12.sp,
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
        editorFontFamily = "serif"
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
        editorFontFamily = "serif"
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

package com.attachdesign.kern.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.attachdesign.kern.ui.theme.AppColorTheme

private val DarkColorScheme = darkColorScheme(
  primary = KernDefaultAccent,
  background = KernDarkBackground,
  surface = KernDarkSurface,
  onPrimary = KernDarkBackground,
  onBackground = KernDarkTextPrimary,
  onSurface = KernDarkTextPrimary,
)

private val LightColorScheme = lightColorScheme(
  primary = KernDefaultAccent,
  background = KernLightBackground,
  surface = KernLightSurface,
  onPrimary = KernLightBackground,
  onBackground = KernLightTextPrimary,
  onSurface = KernLightTextPrimary,
)

private fun AppColorTheme.toColorScheme() = if (isDark) {
  darkColorScheme(
    primary = accent,
    background = background,
    surface = surface,
    onPrimary = background,
    onBackground = textPrimary,
    onSurface = textPrimary,
  )
} else {
  lightColorScheme(
    primary = accent,
    background = background,
    surface = surface,
    onPrimary = background,
    onBackground = textPrimary,
    onSurface = textPrimary,
  )
}

@Composable
fun ModernAndroidMarkdownEditorTheme(
  activeTheme: AppColorTheme? = null,
  darkTheme: Boolean = activeTheme?.isDark ?: isSystemInDarkTheme(),
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      activeTheme != null -> activeTheme.toColorScheme()
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

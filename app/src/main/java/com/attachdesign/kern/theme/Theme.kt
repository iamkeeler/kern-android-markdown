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
import com.attachdesign.kern.ui.theme.ThemeEngine
import com.attachdesign.kern.ui.theme.readableColor
import androidx.compose.ui.graphics.compositeOver

private val DarkColorScheme = ThemeEngine.DefaultDark.toColorTheme().toMaterialColorScheme()

private val LightColorScheme = ThemeEngine.DefaultLight.toColorTheme().toMaterialColorScheme()

/** Roles used by the app's native menus, dialogs, switches, fields, buttons and snackbars. */
internal fun AppColorTheme.toMaterialColorScheme() = accessibleRoles.let { roles ->
  val base = if (isDark) darkColorScheme() else lightColorScheme()
  base.copy(
    primary = roles.controlAccent,
    onPrimary = roles.onControlAccent,
    // A checked switch's optional icon uses onPrimaryContainer on its onPrimary thumb.
    primaryContainer = roles.onControlAccent,
    onPrimaryContainer = roles.controlAccent,
    secondary = roles.textSecondary,
    onSecondary = readableColor(roles.background, listOf(roles.textSecondary.compositeOver(roles.surface)), 4.5),
    secondaryContainer = roles.surface,
    onSecondaryContainer = roles.textPrimary,
    background = roles.background,
    onBackground = roles.textPrimary,
    surface = roles.surface,
    onSurface = roles.textPrimary,
    surfaceVariant = roles.surface,
    onSurfaceVariant = roles.textSecondary,
    // Keep elevated menu/dialog surfaces neutral instead of adding a fallback purple tint.
    surfaceTint = roles.surface,
    surfaceBright = roles.surface,
    surfaceDim = roles.background,
    surfaceContainer = roles.surface,
    surfaceContainerHigh = roles.surface,
    surfaceContainerHighest = roles.surface,
    surfaceContainerLow = roles.surface,
    surfaceContainerLowest = roles.background,
    outline = roles.indicator,
    outlineVariant = roles.indicator,
    error = roles.textDanger,
    onError = readableColor(roles.background, listOf(roles.textDanger.compositeOver(roles.surface)), 4.5),
    errorContainer = roles.surface,
    onErrorContainer = roles.textDanger,
    inverseSurface = roles.inverseSurface,
    inverseOnSurface = roles.inverseOnSurface,
    inversePrimary = roles.inversePrimary,
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
      activeTheme != null -> activeTheme.toMaterialColorScheme()
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

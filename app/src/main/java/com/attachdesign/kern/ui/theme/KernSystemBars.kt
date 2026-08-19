package com.attachdesign.kern.ui.theme

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

data class KernSystemBarAppearance(
    val color: androidx.compose.ui.graphics.Color,
    val usesDarkIcons: Boolean
)

fun systemBarUsesDarkIcons(isDarkTheme: Boolean) = !isDarkTheme

fun AppColorTheme.systemBarAppearance() = KernSystemBarAppearance(
    color = background,
    usesDarkIcons = systemBarUsesDarkIcons(isDark)
)

/** Keeps Android system chrome in sync with the active Kern theme on every screen. */
@Composable
fun ApplyKernSystemBars(theme: AppColorTheme) {
    val view = LocalView.current
    if (view.isInEditMode) return

    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        val appearance = theme.systemBarAppearance()
        window.statusBarColor = appearance.color.toArgb()
        window.navigationBarColor = appearance.color.toArgb()
        controller.isAppearanceLightStatusBars = appearance.usesDarkIcons
        controller.isAppearanceLightNavigationBars = appearance.usesDarkIcons
    }
}

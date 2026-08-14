package com.attachdesign.kern.ui.theme

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** Keeps Android system chrome in sync with the active Kern theme on every screen. */
@Composable
fun ApplyKernSystemBars(theme: AppColorTheme) {
    val view = LocalView.current
    if (view.isInEditMode) return

    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, view)
        window.statusBarColor = theme.background.toArgb()
        window.navigationBarColor = theme.background.toArgb()
        controller.isAppearanceLightStatusBars = !theme.isDark
        controller.isAppearanceLightNavigationBars = !theme.isDark
    }
}

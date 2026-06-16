package com.attachdesign.kern.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.attachdesign.kern.ui.theme.AppColorTheme
import com.attachdesign.kern.ui.theme.appFontFamily

@Composable
fun MinimalOutlinedButton(
    text: String,
    onClick: () -> Unit,
    theme: AppColorTheme,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false
) {
    val appFont = theme.appFontFamily
    val bgColor = if (isPrimary) theme.accent else Color.Transparent
    val textColor = if (isPrimary) theme.background else theme.textPrimary
    val borderColor = if (isPrimary) theme.accent else theme.textMuted.copy(alpha = 0.3f)

    Box(
        modifier = modifier
            .border(border = BorderStroke(1.dp, borderColor), shape = RoundedCornerShape(4.dp))
            .background(bgColor, RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 14.sp,
            fontFamily = appFont,
            fontWeight = if (isPrimary) FontWeight.Medium else FontWeight.Normal
        )
    }
}

@Composable
fun MinimalOutlinedButtonSmall(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    theme: AppColorTheme,
    modifier: Modifier = Modifier
) {
    val border    = if (selected) theme.accent else theme.textMuted.copy(alpha = 0.3f)
    val bg        = if (selected) theme.accent.copy(alpha = 0.15f) else Color.Transparent
    val textColor = if (selected) theme.accent else theme.textMuted

    Box(
        modifier = modifier
            .border(border = BorderStroke(1.dp, border), shape = RoundedCornerShape(4.dp))
            .background(bg, RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium
        )
    }
}

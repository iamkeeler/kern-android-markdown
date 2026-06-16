package com.attachdesign.kern.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.attachdesign.kern.ui.theme.AppColorTheme
import com.attachdesign.kern.ui.theme.appFontFamily
import kotlinx.coroutines.launch

val SWIPE_REVEAL_WIDTH = 200.dp

@Composable
fun SwipeAction(label: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(66.dp)
            .background(color)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SwipeableListItem(
    title: String,
    subtitle: String,
    icon: String,
    details: String,
    isBold: Boolean,
    theme: AppColorTheme,
    onClick: () -> Unit,
    swipeActions: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier
) {
    val appFont = theme.appFontFamily
    val density = LocalDensity.current
    val revealWidthPx = with(density) { SWIPE_REVEAL_WIDTH.toPx() }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (offsetX.value < -revealWidthPx / 2)
                                offsetX.animateTo(-revealWidthPx, spring(stiffness = Spring.StiffnessMediumLow))
                            else
                                offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                        }
                    },
                    onDragCancel = { scope.launch { offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) } },
                    onHorizontalDrag = { _, delta ->
                        scope.launch {
                            offsetX.snapTo((offsetX.value + delta).coerceIn(-revealWidthPx, 0f))
                        }
                    }
                )
            }
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(SWIPE_REVEAL_WIDTH)
                .fillMaxHeight(),
            horizontalArrangement = Arrangement.End
        ) {
            swipeActions()
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { androidx.compose.ui.unit.IntOffset(offsetX.value.toInt(), 0) }
                .background(theme.background)
                .clickable {
                    if (offsetX.value != 0f) {
                        scope.launch { offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                    } else {
                        onClick()
                    }
                }
                .padding(vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                    Text(icon, fontSize = 14.sp, modifier = Modifier.padding(bottom = 1.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.alignByBaseline()) {
                        Text(
                            text       = title,
                            color      = theme.textPrimary,
                            fontSize   = 14.sp,
                            fontFamily = appFont,
                            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis
                        )
                        if (subtitle.isNotEmpty()) {
                            Text(
                                text = subtitle,
                                color = theme.textMuted,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text     = " . ".repeat(50),
                        color    = theme.textMuted.copy(alpha = 0.4f),
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.weight(1f).alignByBaseline()
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(details, color = theme.textMuted, fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace, modifier = Modifier.alignByBaseline())
            }
        }
    }
}

package com.attachdesign.kern.ui.common

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AdaptiveMetricPairTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun metricsStackWhenPaneOrFontScaleNeedsMoreRoom() {
        val width = mutableStateOf(360.dp)
        val fontScale = mutableStateOf(1f)
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale.value)) {
                Box(Modifier.width(width.value)) {
                    AdaptiveMetricPair(
                        first = { Text("123456789 words", it.testTag("first")) },
                        second = { Text("987654321 characters", it.testTag("second")) }
                    )
                }
            }
        }
        fun bounds(tag: String) = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        assertTrue(bounds("first").right <= bounds("second").left)
        rule.runOnIdle { width.value = 280.dp }
        assertTrue(bounds("first").bottom <= bounds("second").top)
        rule.runOnIdle { width.value = 360.dp; fontScale.value = 2f }
        assertTrue(bounds("first").bottom <= bounds("second").top)
    }
}

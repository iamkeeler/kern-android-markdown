package com.attachdesign.kern.ui.editor

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.attachdesign.kern.ui.theme.ThemeEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class EditorChromeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val themes = listOf(ThemeEngine.DefaultLight, ThemeEngine.DefaultDark).map { it.toColorTheme() }

    @Test
    fun headerPaintsAnOpaqueBackgroundInBothThemes() {
        val theme = mutableStateOf(themes.first())
        compose.setContent {
            Box(Modifier.fillMaxSize().background(Color.Magenta)) {
                EditorHeader("note.md", theme.value, {}, {}, {}, {})
            }
        }
        themes.forEach { active ->
            compose.runOnIdle { theme.value = active }
            val image = compose.onNodeWithTag("Editor header").captureToImage().toPixelMap()
            assertEquals(active.background.toArgb(), image[image.width / 2, image.height - 2].toArgb())
        }
    }

    @Test
    fun elasticScrollCannotDrawDocumentOverTheHeader() {
        val theme = mutableStateOf(themes.first())
        lateinit var scroll: ElasticOverscrollState
        compose.setContent {
            scroll = rememberElasticOverscrollState()
            Column(Modifier.fillMaxSize().background(theme.value.background).testTag("Chrome")) {
                EditorHeader("note.md", theme.value, {}, {}, {}, {})
                ElasticOverscrollContainer(scroll, theme.value.accent, Modifier.weight(1f).fillMaxWidth()) {
                    // A solid document exposes any pixel escaping its viewport.
                    Box(Modifier.fillMaxSize().background(Color.Magenta))
                }
            }
        }
        themes.forEach { active ->
            compose.runOnIdle {
                theme.value = active
                runBlocking { scroll.overscrollOffset.snapTo(-300f) }
            }
            val header = compose.onNodeWithTag("Editor header").fetchSemanticsNode().boundsInRoot
            val image = compose.onNodeWithTag("Chrome").captureToImage().toPixelMap()
            assertEquals(active.background.toArgb(), image[image.width / 2, header.bottom.toInt() - 2].toArgb())
            assertEquals(Color.Magenta.toArgb(), image[image.width / 2, header.bottom.toInt() + 10].toArgb())
        }
    }
}

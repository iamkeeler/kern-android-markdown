package com.attachdesign.kern.ui.theme

import androidx.compose.ui.graphics.Color
import com.attachdesign.kern.parser.MarkdownBlockType
import com.attachdesign.kern.parser.MarkdownElementType
import com.attachdesign.kern.ui.editor.MarkdownSpanStyleProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class EditorTypographyTest {
    @Test
    fun `preset document sizes have proportional body line heights`() {
        val expected = listOf(
            Triple(0.8f, 12.8f, 20.48f),
            Triple(1f, 16f, 25.6f),
            Triple(1.2f, 19.2f, 30.72f),
            Triple(1.4f, 22.4f, 35.84f)
        )
        expected.forEach { (scale, fontSize, lineHeight) ->
            val metrics = EditorTypography.metrics(scale)
            assertEquals(fontSize, metrics.fontSize.value, 0.0001f)
            assertEquals(lineHeight, metrics.lineHeight.value, 0.0001f)
        }
        assertEquals(16f, EditorTypography.metrics().fontSize.value, 0f)
        assertEquals(13f, AppTypographySizes().body.value, 0f)
    }

    @Test
    fun `missing malformed and nonfinite settings use Medium`() {
        listOf(null, "", "invalid", "NaN", "Infinity", "-Infinity", "1e1000").forEach { value ->
            assertEquals("setting=$value", 1f, EditorTypography.normalizeScale(value), 0f)
        }
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { value ->
            assertEquals(1f, EditorTypography.normalizeScale(value), 0f)
            assertEquals(16f, EditorTypography.metrics(value).fontSize.value, 0f)
        }
    }

    @Test
    fun `finite settings clamp at limits without snapping continuous values`() {
        listOf(
            -Float.MAX_VALUE to 0.8f,
            -1f to 0.8f,
            0f to 0.8f,
            0.799f to 0.8f,
            0.8f to 0.8f,
            0.91f to 0.91f,
            1.1f to 1.1f,
            1.37f to 1.37f,
            1.4f to 1.4f,
            1.401f to 1.4f,
            Float.MAX_VALUE to 1.4f
        ).forEach { (value, expected) ->
            assertEquals(expected, EditorTypography.normalizeScale(value), 0f)
            assertEquals(expected, EditorTypography.normalizeScale(value.toString()), 0f)
            assertEquals(16f * expected, EditorTypography.metrics(value).fontSize.value, 0.0001f)
        }
    }

    @Test
    fun `document presets preserve rendered heading and code proportions`() {
        val headingRatios = listOf(
            MarkdownBlockType.HEADER_1 to 1.75f,
            MarkdownBlockType.HEADER_2 to 1.55f,
            MarkdownBlockType.HEADER_3 to 1.35f,
            MarkdownBlockType.HEADER_4 to 1.15f,
            MarkdownBlockType.HEADER_5 to 1.15f,
            MarkdownBlockType.HEADER_6 to 1.15f
        )
        listOf(0.8f, 1f, 1.2f, 1.4f).forEach { scale ->
            val bodySize = EditorTypography.metrics(scale).fontSize
            val provider = MarkdownSpanStyleProvider(Color.Gray, Color.LightGray, bodySize)
            headingRatios.forEach { (heading, ratio) ->
                assertEquals(bodySize.value * ratio, provider.styleForBlock(heading)!!.fontSize.value, 0.0001f)
            }
            assertEquals(bodySize.value * 0.9f,
                provider.styleForBlock(MarkdownBlockType.CODE_BLOCK)!!.fontSize.value, 0.0001f)
            assertEquals(bodySize.value * 0.9f,
                provider.styleForElement(MarkdownElementType.INLINE_CODE)!!.fontSize.value, 0.0001f)
        }
    }
}

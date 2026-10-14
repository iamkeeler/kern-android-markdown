package com.attachdesign.kern.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.attachdesign.kern.parser.MarkdownBlockType
import com.attachdesign.kern.parser.MarkdownElementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MarkdownSpanStyleProviderTest {

    private val tokenColor = Color(0xFF6E6A5F)
    private val codeBackground = Color(0xFFE8E2D6)
    private val bodySize = 16.sp

    @Test
    fun testBlockStylesWithBodySize() {
        val provider = MarkdownSpanStyleProvider(tokenColor, codeBackground, bodySize)

        val h1 = provider.styleForBlock(MarkdownBlockType.HEADER_1)
        assertNotNull(h1)
        assertEquals(FontWeight.Bold, h1?.fontWeight)
        assertEquals(16.sp * 1.75f, h1?.fontSize)

        val h2 = provider.styleForBlock(MarkdownBlockType.HEADER_2)
        assertNotNull(h2)
        assertEquals(FontWeight.Bold, h2?.fontWeight)
        assertEquals(16.sp * 1.55f, h2?.fontSize)

        val h3 = provider.styleForBlock(MarkdownBlockType.HEADER_3)
        assertNotNull(h3)
        assertEquals(FontWeight.SemiBold, h3?.fontWeight)
        assertEquals(16.sp * 1.35f, h3?.fontSize)

        val h4 = provider.styleForBlock(MarkdownBlockType.HEADER_4)
        assertNotNull(h4)
        assertEquals(FontWeight.SemiBold, h4?.fontWeight)
        assertEquals(16.sp * 1.15f, h4?.fontSize)

        val codeBlock = provider.styleForBlock(MarkdownBlockType.CODE_BLOCK)
        assertNotNull(codeBlock)
        assertEquals(FontFamily.Monospace, codeBlock?.fontFamily)
        assertEquals(codeBackground, codeBlock?.background)
        assertEquals(16.sp * 0.90f, codeBlock?.fontSize)

        val blockquote = provider.styleForBlock(MarkdownBlockType.BLOCKQUOTE)
        assertNotNull(blockquote)
        assertEquals(tokenColor, blockquote?.color)
        assertEquals(FontStyle.Italic, blockquote?.fontStyle)

        assertNull(provider.styleForBlock(MarkdownBlockType.PARAGRAPH))
    }

    @Test
    fun testBlockStylesWithoutBodySize() {
        val provider = MarkdownSpanStyleProvider(tokenColor, codeBackground, null)

        val h1 = provider.styleForBlock(MarkdownBlockType.HEADER_1)
        assertNotNull(h1)
        assertEquals(TextUnit.Unspecified, h1?.fontSize)

        val codeBlock = provider.styleForBlock(MarkdownBlockType.CODE_BLOCK)
        assertNotNull(codeBlock)
        assertEquals(TextUnit.Unspecified, codeBlock?.fontSize)
    }

    @Test
    fun testElementStyles() {
        val provider = MarkdownSpanStyleProvider(tokenColor, codeBackground, bodySize)

        val bold = provider.styleForElement(MarkdownElementType.BOLD)
        assertEquals(FontWeight.Bold, bold?.fontWeight)

        val italic = provider.styleForElement(MarkdownElementType.ITALIC)
        assertEquals(FontStyle.Italic, italic?.fontStyle)

        val strikethrough = provider.styleForElement(MarkdownElementType.STRIKETHROUGH)
        assertEquals(TextDecoration.LineThrough, strikethrough?.textDecoration)

        val inlineCode = provider.styleForElement(MarkdownElementType.INLINE_CODE)
        assertEquals(FontFamily.Monospace, inlineCode?.fontFamily)
        assertEquals(codeBackground, inlineCode?.background)

        val link = provider.styleForElement(MarkdownElementType.LINK)
        assertEquals(tokenColor, link?.color)
        assertEquals(TextDecoration.Underline, link?.textDecoration)

        val image = provider.styleForElement(MarkdownElementType.IMAGE)
        assertEquals(tokenColor, image?.color)
        assertEquals(FontStyle.Italic, image?.fontStyle)
        assertEquals(TextDecoration.Underline, image?.textDecoration)

        val tokenHeader = provider.styleForElement(MarkdownElementType.TOKEN_HEADER)
        assertEquals(tokenColor, tokenHeader?.color)
        assertEquals(FontWeight.Normal, tokenHeader?.fontWeight)

        assertNull(provider.styleForElement(MarkdownElementType.HEADER_1))
    }

    @Test
    fun testSpecialHelperStyles() {
        val provider = MarkdownSpanStyleProvider(tokenColor, codeBackground, bodySize)

        val bulletBold = provider.styleForTokenBulletBold()
        assertEquals(tokenColor, bulletBold.color)
        assertEquals(FontWeight.Bold, bulletBold.fontWeight)

        val checkedTask = provider.styleForCheckedTaskItem()
        assertEquals(TextDecoration.LineThrough, checkedTask.textDecoration)

        val rawCode = provider.styleForRawCodeBlock()
        assertEquals(FontFamily.Monospace, rawCode.fontFamily)
        assertEquals(codeBackground, rawCode.background)

        val monospaceRaw = provider.styleForMonospaceRaw()
        assertEquals(FontFamily.Monospace, monospaceRaw.fontFamily)
    }
}

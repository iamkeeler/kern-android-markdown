package com.example.modernandroidmarkdowneditor

import com.example.modernandroidmarkdowneditor.parser.IndexRange
import com.example.modernandroidmarkdowneditor.parser.IndexTransformationMatrix
import com.example.modernandroidmarkdowneditor.parser.MarkdownBlockType
import com.example.modernandroidmarkdowneditor.parser.MarkdownElementType
import com.example.modernandroidmarkdowneditor.parser.MarkdownParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    @Test
    fun testSplitDocument() {
        val document = """
            # Header
            
            First paragraph with some text.
            Second line of first paragraph.
            
            ```kotlin
            fun main() {
                println("Hello")
                
                // This blank line shouldn't cause a split!
            }
            ```
            
            Last paragraph.
        """.trimIndent()

        val blocks = MarkdownParser.splitDocument(document)
        assertEquals(4, blocks.size)
        assertEquals("# Header", blocks[0])
        assertEquals("First paragraph with some text.\nSecond line of first paragraph.", blocks[1])
        assertTrue(blocks[2].startsWith("```kotlin"))
        assertTrue(blocks[2].endsWith("```"))
        assertEquals("Last paragraph.", blocks[3])
    }

    @Test
    fun testParseHeadersAndBlocks() {
        val h1 = MarkdownParser.parseParagraph("# Hello World")
        assertEquals(MarkdownBlockType.HEADER_1, h1.blockType)
        assertEquals(1, h1.elements.size)
        assertEquals(MarkdownElementType.TOKEN_HEADER, h1.elements[0].type)
        assertEquals(0, h1.elements[0].start)
        assertEquals(2, h1.elements[0].end) // "# " is length 2

        val quote = MarkdownParser.parseParagraph("> Quote text")
        assertEquals(MarkdownBlockType.BLOCKQUOTE, quote.blockType)
        assertEquals(MarkdownElementType.TOKEN_BLOCKQUOTE, quote.elements[0].type)
        assertEquals(0, quote.elements[0].start)
        assertEquals(2, quote.elements[0].end)

        val bullet = MarkdownParser.parseParagraph("- List item")
        assertEquals(MarkdownBlockType.UNORDERED_LIST, bullet.blockType)
        assertEquals(MarkdownElementType.TOKEN_LIST_BULLET, bullet.elements[0].type)

        val ordered = MarkdownParser.parseParagraph("12. Ordered list item")
        assertEquals(MarkdownBlockType.ORDERED_LIST, ordered.blockType)
        assertEquals(MarkdownElementType.TOKEN_LIST_BULLET, ordered.elements[0].type)
        assertEquals(0, ordered.elements[0].start)
        assertEquals(4, ordered.elements[0].end) // "12. " is length 4
    }

    @Test
    fun testParseInlineStyles() {
        val text = "This is **bold** and *italic* and `code` and ~~strike~~ and [link](http://google.com)"
        val paragraph = MarkdownParser.parseParagraph(text)
        
        // Find elements by type
        val bold = paragraph.elements.first { it.type == MarkdownElementType.BOLD }
        val italic = paragraph.elements.first { it.type == MarkdownElementType.ITALIC }
        val code = paragraph.elements.first { it.type == MarkdownElementType.INLINE_CODE }
        val strike = paragraph.elements.first { it.type == MarkdownElementType.STRIKETHROUGH }
        val link = paragraph.elements.first { it.type == MarkdownElementType.LINK }

        assertEquals("bold", text.substring(bold.start, bold.end))
        assertEquals("italic", text.substring(italic.start, italic.end))
        assertEquals("code", text.substring(code.start, code.end))
        assertEquals("strike", text.substring(strike.start, strike.end))
        assertEquals("link", text.substring(link.start, link.end))
        assertEquals("http://google.com", link.extra)
    }

    @Test
    fun testIndexTransformationMatrix() {
        // Raw string: "This is **bold** text" (length 21)
        // Stripped:    "This is bold text" (length 17)
        // Tokens stripped: ** at 8..10 and ** at 14..16
        val strippedRanges = listOf(
            IndexRange(8, 10),
            IndexRange(14, 16)
        )
        val matrix = IndexTransformationMatrix(strippedRanges)

        // Test originalToTransformed
        assertEquals(0, matrix.originalToTransformed(0))
        assertEquals(5, matrix.originalToTransformed(5))
        assertEquals(8, matrix.originalToTransformed(8)) // start of first **
        assertEquals(8, matrix.originalToTransformed(9)) // inside first **
        assertEquals(8, matrix.originalToTransformed(10)) // character 'b'
        assertEquals(11, matrix.originalToTransformed(13)) // character 'd'
        assertEquals(12, matrix.originalToTransformed(14)) // start of second **
        assertEquals(12, matrix.originalToTransformed(15)) // inside second **
        assertEquals(12, matrix.originalToTransformed(16)) // space after **
        assertEquals(16, matrix.originalToTransformed(20)) // character 't'

        // Test transformedToOriginal
        assertEquals(0, matrix.transformedToOriginal(0))
        assertEquals(5, matrix.transformedToOriginal(5))
        assertEquals(10, matrix.transformedToOriginal(8)) // maps back to 'b' in original which is index 10
        assertEquals(13, matrix.transformedToOriginal(11)) // maps back to 'd' in original which is index 13
        assertEquals(16, matrix.transformedToOriginal(12)) // maps back to ' ' after second ** in original which is index 16
        assertEquals(20, matrix.transformedToOriginal(16)) // maps back to 't' in original which is index 20
    }

    @Test
    fun testHemingwayAnalyzer() {
        // Simple test text containing an adverb (quickly), a passive voice (was done), and simple words
        val text = "He quickly ran to the store. The work was done. We will utilize this tool to implement it."
        val metrics = com.example.modernandroidmarkdowneditor.analysis.HemingwayAnalyzer.analyze(text)

        assertEquals(18, metrics.wordCount)
        assertEquals(3, metrics.sentenceCount)
        assertEquals(1, metrics.adverbCount)
        assertEquals(1, metrics.passiveVoiceCount)
        
        // Check complex word highlight (utilize and implement)
        val complexHighlights = metrics.highlights.filter { it.type == com.example.modernandroidmarkdowneditor.analysis.HighlightType.COMPLEX_WORD }
        assertEquals(2, complexHighlights.size)
    }
}

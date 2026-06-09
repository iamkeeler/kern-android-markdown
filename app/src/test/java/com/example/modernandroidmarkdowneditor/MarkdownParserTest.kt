package com.example.modernandroidmarkdowneditor

import com.example.modernandroidmarkdowneditor.parser.IndexRange
import com.example.modernandroidmarkdowneditor.parser.IndexTransformationMatrix
import com.example.modernandroidmarkdowneditor.parser.MarkdownBlockType
import com.example.modernandroidmarkdowneditor.parser.MarkdownElementType
import com.example.modernandroidmarkdowneditor.parser.MarkdownParser
import com.example.modernandroidmarkdowneditor.parser.MarkdownEditorEngine
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

    @Test
    fun testListParsingAndSplitting() {
        // Test isListLine directly
        assertTrue(MarkdownParser.isListLine("- Item"))
        assertTrue(MarkdownParser.isListLine("* Item"))
        assertTrue(MarkdownParser.isListLine("+ Item"))
        assertTrue(MarkdownParser.isListLine("  - Item"))
        assertTrue(MarkdownParser.isListLine("1. Item"))
        assertTrue(MarkdownParser.isListLine("12. Item"))
        assertTrue(MarkdownParser.isListLine("  12. Item"))
        assertTrue(!MarkdownParser.isListLine("-Item"))
        assertTrue(!MarkdownParser.isListLine("1.Item"))

        // Test splitDocument with consecutive lists and plain paragraphs
        val doc = """
            First block.
            Still first block.
            
            - List item 1
            - List item 2
            
            Plain block after list.
        """.trimIndent()

        val blocks = MarkdownParser.splitDocument(doc)
        assertEquals(4, blocks.size)
        assertEquals("First block.\nStill first block.", blocks[0])
        assertEquals("- List item 1", blocks[1])
        assertEquals("- List item 2", blocks[2])
        assertEquals("Plain block after list.", blocks[3])

        // Test joinDocument with consecutive list items
        val joined = MarkdownParser.joinDocument(blocks)
        val expectedJoined = """
            First block.
            Still first block.
            
            - List item 1
            - List item 2
            
            Plain block after list.
        """.trimIndent()
        assertEquals(expectedJoined, joined)

        // Test parseParagraph for indented lists
        val indentedItem = MarkdownParser.parseParagraph("  - Nest")
        assertEquals(MarkdownBlockType.UNORDERED_LIST, indentedItem.blockType)
        assertEquals(1, indentedItem.elements.size)
        assertEquals(MarkdownElementType.TOKEN_LIST_BULLET, indentedItem.elements[0].type)
        assertEquals(2, indentedItem.elements[0].start)
        assertEquals(4, indentedItem.elements[0].end)

        // Test parseParagraph for ordered lists
        val orderedItem = MarkdownParser.parseParagraph("  99. Item")
        assertEquals(MarkdownBlockType.ORDERED_LIST, orderedItem.blockType)
        assertEquals(1, orderedItem.elements.size)
        assertEquals(MarkdownElementType.TOKEN_LIST_BULLET, orderedItem.elements[0].type)
        assertEquals(2, orderedItem.elements[0].start)
        assertEquals(6, orderedItem.elements[0].end)
    }

    @Test
    fun testEditorEngineListContinuation() {
        // Test Checklist Continuation
        val checklistRes1 = MarkdownEditorEngine.checkContinuation("  - [x] Task")
        assertTrue(checklistRes1.isContinuation)
        assertEquals("  - [ ] ", checklistRes1.nextLinePrefix)
        assertTrue(!checklistRes1.isExit)

        val checklistResEmpty = MarkdownEditorEngine.checkContinuation("  - [ ] ")
        assertTrue(checklistResEmpty.isContinuation)
        assertEquals("", checklistResEmpty.nextLinePrefix)
        assertTrue(checklistResEmpty.isExit)

        // Test Ordered List Continuation
        val orderedRes1 = MarkdownEditorEngine.checkContinuation("1. Apple")
        assertTrue(orderedRes1.isContinuation)
        assertEquals("2. ", orderedRes1.nextLinePrefix)
        assertTrue(!orderedRes1.isExit)

        val orderedResEmpty = MarkdownEditorEngine.checkContinuation("9. ")
        assertTrue(orderedResEmpty.isContinuation)
        assertEquals("", orderedResEmpty.nextLinePrefix)
        assertTrue(orderedResEmpty.isExit)

        // Test Bullet List Continuation
        val bulletRes1 = MarkdownEditorEngine.checkContinuation("  * Item")
        assertTrue(bulletRes1.isContinuation)
        assertEquals("  * ", bulletRes1.nextLinePrefix)
        assertTrue(!bulletRes1.isExit)

        val bulletResEmpty = MarkdownEditorEngine.checkContinuation("  * ")
        assertTrue(bulletResEmpty.isContinuation)
        assertEquals("", bulletResEmpty.nextLinePrefix)
        assertTrue(bulletResEmpty.isExit)

        // Test Blockquote Continuation
        val quoteRes1 = MarkdownEditorEngine.checkContinuation("> Quote")
        assertTrue(quoteRes1.isContinuation)
        assertEquals("> ", quoteRes1.nextLinePrefix)
        assertTrue(!quoteRes1.isExit)

        val quoteResEmpty = MarkdownEditorEngine.checkContinuation("> ")
        assertTrue(quoteResEmpty.isContinuation)
        assertEquals("", quoteResEmpty.nextLinePrefix)
        assertTrue(quoteResEmpty.isExit)
    }

    @Test
    fun testEditorEngineTextFormatting() {
        // Selection wrapping
        val wrapResult = MarkdownEditorEngine.handleTextChange(
            oldText = "hello", oldSelStart = 0, oldSelEnd = 5,
            newText = "*", newSelStart = 1, newSelEnd = 1,
            autoHeaderSpacing = true
        )
        assertEquals("*hello*", wrapResult.text)
        assertEquals(1, wrapResult.selectionStart)
        assertEquals(6, wrapResult.selectionEnd)

        // Auto-pairing: opening brackets
        val pairBracket = MarkdownEditorEngine.handleTextChange(
            oldText = "", oldSelStart = 0, oldSelEnd = 0,
            newText = "(", newSelStart = 1, newSelEnd = 1,
            autoHeaderSpacing = true
        )
        assertEquals("()", pairBracket.text)
        assertEquals(1, pairBracket.selectionStart)

        // Overtype skipping: closing brackets
        val skipBracket = MarkdownEditorEngine.handleTextChange(
            oldText = "()", oldSelStart = 1, oldSelEnd = 1,
            newText = "())", newSelStart = 2, newSelEnd = 2,
            autoHeaderSpacing = true
        )
        assertEquals("()", skipBracket.text)
        assertEquals(2, skipBracket.selectionStart)

        // Smart Typography: dashes
        val enDash = MarkdownEditorEngine.handleTextChange(
            oldText = "-", oldSelStart = 1, oldSelEnd = 1,
            newText = "--", newSelStart = 2, newSelEnd = 2,
            autoHeaderSpacing = true
        )
        assertEquals("–", enDash.text)
        assertEquals(1, enDash.selectionStart)

        val emDash = MarkdownEditorEngine.handleTextChange(
            oldText = "–", oldSelStart = 1, oldSelEnd = 1,
            newText = "–-", newSelStart = 2, newSelEnd = 2,
            autoHeaderSpacing = true
        )
        assertEquals("—", emDash.text)
        assertEquals(1, emDash.selectionStart)

        // Smart Typography: ellipsis
        val ellipsis = MarkdownEditorEngine.handleTextChange(
            oldText = "..", oldSelStart = 2, oldSelEnd = 2,
            newText = "...", newSelStart = 3, newSelEnd = 3,
            autoHeaderSpacing = true
        )
        assertEquals("…", ellipsis.text)
        assertEquals(1, ellipsis.selectionStart)

        // Smart Typography: quotes
        val openQuote = MarkdownEditorEngine.handleTextChange(
            oldText = "", oldSelStart = 0, oldSelEnd = 0,
            newText = "\"", newSelStart = 1, newSelEnd = 1,
            autoHeaderSpacing = true
        )
        // Quote is converted to smart opening quote “ and paired with closing ”
        assertEquals("“”", openQuote.text)
        assertEquals(1, openQuote.selectionStart)

        val closeQuote = MarkdownEditorEngine.handleTextChange(
            oldText = "“hello", oldSelStart = 6, oldSelEnd = 6,
            newText = "“hello\"", newSelStart = 7, newSelEnd = 7,
            autoHeaderSpacing = true
        )
        assertEquals("“hello”", closeQuote.text)
        assertEquals(7, closeQuote.selectionStart)

        // Auto Header Spacing
        val headerSpace = MarkdownEditorEngine.handleTextChange(
            oldText = "##", oldSelStart = 2, oldSelEnd = 2,
            newText = "##T", newSelStart = 3, newSelEnd = 3,
            autoHeaderSpacing = true
        )
        assertEquals("## T", headerSpace.text)
        assertEquals(4, headerSpace.selectionStart)

        // Auto Header Spacing Disabled
        val headerSpaceDisabled = MarkdownEditorEngine.handleTextChange(
            oldText = "##", oldSelStart = 2, oldSelEnd = 2,
            newText = "##T", newSelStart = 3, newSelEnd = 3,
            autoHeaderSpacing = false
        )
        assertEquals("##T", headerSpaceDisabled.text)
        assertEquals(3, headerSpaceDisabled.selectionStart)

        // Curly Braces Auto-complete Enabled
        val pairBraces = MarkdownEditorEngine.handleTextChange(
            oldText = "", oldSelStart = 0, oldSelEnd = 0,
            newText = "{", newSelStart = 1, newSelEnd = 1,
            autoHeaderSpacing = true, autoCompleteEnabled = true, autoCompleteBraces = true
        )
        assertEquals("{}", pairBraces.text)
        assertEquals(1, pairBraces.selectionStart)

        // Curly Braces Overtype Skipping
        val skipBraces = MarkdownEditorEngine.handleTextChange(
            oldText = "{}", oldSelStart = 1, oldSelEnd = 1,
            newText = "{}}", newSelStart = 2, newSelEnd = 2,
            autoHeaderSpacing = true, autoCompleteEnabled = true, autoCompleteBraces = true
        )
        assertEquals("{}", skipBraces.text)
        assertEquals(2, skipBraces.selectionStart)

        // Curly Braces Disabled
        val disabledBraces = MarkdownEditorEngine.handleTextChange(
            oldText = "", oldSelStart = 0, oldSelEnd = 0,
            newText = "{", newSelStart = 1, newSelEnd = 1,
            autoHeaderSpacing = true, autoCompleteEnabled = true, autoCompleteBraces = false
        )
        assertEquals("{", disabledBraces.text)
        assertEquals(1, disabledBraces.selectionStart)

        // Master Auto-complete Disabled
        val disabledMaster = MarkdownEditorEngine.handleTextChange(
            oldText = "", oldSelStart = 0, oldSelEnd = 0,
            newText = "{", newSelStart = 1, newSelEnd = 1,
            autoHeaderSpacing = true, autoCompleteEnabled = false, autoCompleteBraces = true
        )
        assertEquals("{", disabledMaster.text)
        assertEquals(1, disabledMaster.selectionStart)

        // Quote Auto-complete Disabled
        val disabledQuotes = MarkdownEditorEngine.handleTextChange(
            oldText = "", oldSelStart = 0, oldSelEnd = 0,
            newText = "\"", newSelStart = 1, newSelEnd = 1,
            autoHeaderSpacing = true, autoCompleteEnabled = true, autoCompleteQuotes = false
        )
        // Since quote pairing is disabled, the straight quote gets converted to curly opening quote by smart typography,
        // but it does NOT append the closing curly quote.
        assertEquals("“", disabledQuotes.text)
        assertEquals(1, disabledQuotes.selectionStart)

        // Single Quote Auto-complete Enabled
        val pairSingleQuotes = MarkdownEditorEngine.handleTextChange(
            oldText = "", oldSelStart = 0, oldSelEnd = 0,
            newText = "'", newSelStart = 1, newSelEnd = 1,
            autoHeaderSpacing = true, autoCompleteEnabled = true, autoCompleteSingleQuotes = true
        )
        assertEquals("‘’", pairSingleQuotes.text)
        assertEquals(1, pairSingleQuotes.selectionStart)

        // Single Quote Overtype Skipping
        val skipSingleQuotes = MarkdownEditorEngine.handleTextChange(
            oldText = "‘’", oldSelStart = 1, oldSelEnd = 1,
            newText = "‘’'", newSelStart = 2, newSelEnd = 2,
            autoHeaderSpacing = true, autoCompleteEnabled = true, autoCompleteSingleQuotes = true
        )
        assertEquals("‘’", skipSingleQuotes.text)
        assertEquals(2, skipSingleQuotes.selectionStart)

        // Single Quote Auto-complete Disabled
        val disabledSingleQuotes = MarkdownEditorEngine.handleTextChange(
            oldText = "", oldSelStart = 0, oldSelEnd = 0,
            newText = "'", newSelStart = 1, newSelEnd = 1,
            autoHeaderSpacing = true, autoCompleteEnabled = true, autoCompleteSingleQuotes = false
        )
        assertEquals("‘", disabledSingleQuotes.text)
        assertEquals(1, disabledSingleQuotes.selectionStart)
    }
}


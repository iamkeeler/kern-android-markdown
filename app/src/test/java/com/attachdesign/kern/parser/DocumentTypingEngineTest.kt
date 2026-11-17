package com.attachdesign.kern.parser

import org.junit.Assert.*
import org.junit.Test

class DocumentTypingEngineTest {
    private val defaults = DocumentTypingPreferences()

    private fun edit(source: String, inserted: String, start: Int = source.length, end: Int = start,
        selectionStart: Int = start, selectionEnd: Int = end,
        preferences: DocumentTypingPreferences = defaults): DocumentTypingEdit? =
        DocumentTypingEngine.transform(source, start, end, inserted, selectionStart, selectionEnd, preferences)

    @Test
    fun everyConfiguredPairHonorsItsOwnSwitchAndTheMasterSwitch() {
        listOf(
            '(' to defaults.copy(parens = false), '[' to defaults.copy(brackets = false),
            '{' to defaults.copy(braces = false), '"' to defaults.copy(quotes = false),
            '\'' to defaults.copy(singleQuotes = false)
        ).forEach { (opening, disabled) ->
            assertEquals(defaults.closingFor(opening).toString(), edit("", opening.toString())?.text)
            assertNull(edit("", opening.toString(), preferences = disabled))
            assertNull(edit("", opening.toString(), preferences = defaults.copy(autoCompleteEnabled = false)))
        }
    }

    @Test
    fun pairingDoesNotRewriteContractionsInchesEscapesOrExistingWords() {
        assertNull(edit("can", "'"))
        assertNull(edit("6", "\""))
        assertNull(edit("word", "(", start = 0))
        assertNull(edit("\\", "\""))
        assertEquals(")", edit("before ", "(")?.text)
    }

    @Test
    fun closingDelimiterOvertypeAndEmptyPairBackspaceAreLocalEdits() {
        assertEquals(DocumentTypingEdit(5, 6, "", 6), edit("(item)", ")", 5))
        assertEquals(DocumentTypingEdit(5, 6, "", 6), edit("\"item\"", "\"", 5))
        assertEquals(DocumentTypingEdit(0, 1, "", 0), edit("()", "", 0, 1, 1, 1))
        assertNull(edit("(a)", "", 0, 1, 1, 1))
        assertNull(edit(")", ")", 0))
        assertNull(edit("()", ")", 1, preferences = defaults.copy(parens = false)))
    }

    @Test
    fun wrappingPreservesBothSelectionDirectionsAndTheSelectedSource() {
        assertEquals(DocumentTypingEdit(4, 5, "[two]", 5, 8), edit("one two", "[", 4, 7, 4, 7))
        assertEquals(DocumentTypingEdit(4, 5, "[two]", 8, 5), edit("one two", "[", 4, 7, 7, 4))
        assertNull(edit("one two", "[", 4, 7, 4, 7, defaults.copy(brackets = false)))
        // An IME replacing an earlier word is not a selected-text wrapping gesture.
        assertNull(edit("one two", "[", 0, 3, 7, 7))
    }

    @Test
    fun headingSpacingIsBoundedToValidPrefixesAtTheEditedLine() {
        assertEquals(DocumentTypingEdit(2, 2, " ", 4), edit("##", "t"))
        assertEquals(DocumentTypingEdit(10, 10, " ", 12), edit("Earlier\n##", "t"))
        assertEquals(DocumentTypingEdit(5, 5, " ", 7), edit("   ##", "t"))
        listOf("#######", "    #", "text #", "# ").forEach { assertNull(edit(it, "t")) }
        assertEquals(DocumentTypingEdit(1, 1, " ", 10), edit("#already", "t"))
        assertNull(edit("#", "t", preferences = defaults.copy(autoHeaderSpacing = false)))
        assertNull(edit("#", "#"))
    }

    @Test
    fun fencedAndInlineCodeRemainLiteralIncludingUnclosedCode() {
        listOf("```\n", "~~~kotlin\n", "text `", "text ``x `", "```\n#").forEach {
            assertNull("Code: $it", edit(it, "("))
        }
        assertNull(edit("```\n#", "t"))
        assertEquals(")", edit("`code` ", "(")?.text)
        assertEquals(")", edit("```\ncode\n```\n", "(")?.text)
    }

    @Test
    fun multiCharacterInsertionsAndBulkDeletionsRemainExact() {
        listOf("(text)", "\"quote\"", "pasted\n#text", "日本語 😀").forEach { assertNull(edit("", it)) }
        assertNull(edit("() words", "", 0, 4, 4, 4))
    }
}

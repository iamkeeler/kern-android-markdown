package com.attachdesign.kern.ui.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.insert
import androidx.compose.ui.text.TextRange
import com.attachdesign.kern.parser.MarkdownEditorEngine
import com.attachdesign.kern.parser.DocumentTypingEngine
import com.attachdesign.kern.parser.DocumentTypingPreferences

/** Keeps typing helpers in the same native edit and Undo transaction as the user's input. */
@OptIn(ExperimentalFoundationApi::class)
class MarkdownDocumentInputTransformation(
    private val preferences: DocumentTypingPreferences = DocumentTypingPreferences(),
    private val preserveInput: () -> Boolean = { false },
    private val isComposing: () -> Boolean = { false },
    private val committedComposition: () -> Boolean = { false }
) : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        if (preserveInput() || changes.changeCount != 1) return

        val changedRange = changes.getRange(0)
        val originalRange = changes.getOriginalRange(0)
        val inserted = asCharSequence().subSequence(changedRange.min, changedRange.max).toString()
        val committed = committedComposition()
        // Native Enter is dispatched asynchronously; composing newlines are already preserved above.
        if (isComposing() && !committed && inserted != "\n" && inserted != "\r\n") return
        val typing = DocumentTypingEngine.transform(originalText, originalRange.min, originalRange.max,
            inserted, originalSelection.start, originalSelection.end, preferences, committed,
            selection.start, selection.end)
        if (typing != null) {
            replace(typing.start, typing.end, typing.text)
            selection = TextRange(typing.selectionStart, typing.selectionEnd)
            return
        }
        if (!originalRange.collapsed) return
        val continuation = MarkdownEditorEngine.continueDocumentList(
            source = originalText.toString(),
            newlineOffset = originalRange.min,
            lineBreak = inserted
        ) ?: return

        if (continuation.nextPrefix != null) {
            insert(changedRange.max, continuation.nextPrefix)
            selection = TextRange(changedRange.max + continuation.nextPrefix.length)
        } else {
            val removedLength = continuation.markerEnd - continuation.markerStart
            delete(continuation.markerStart, continuation.markerEnd)
            selection = TextRange(changedRange.max - removedLength)
        }
    }
}

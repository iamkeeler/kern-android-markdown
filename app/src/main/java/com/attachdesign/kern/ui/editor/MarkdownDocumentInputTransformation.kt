package com.attachdesign.kern.ui.editor

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.insert
import androidx.compose.ui.text.TextRange
import com.attachdesign.kern.parser.MarkdownEditorEngine

/** Applies list continuation as one native text-field edit after a single Enter press. */
@OptIn(ExperimentalFoundationApi::class)
class MarkdownDocumentInputTransformation : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        if (changes.changeCount != 1) return

        val changedRange = changes.getRange(0)
        val originalRange = changes.getOriginalRange(0)
        if (!originalRange.collapsed) return

        val lineBreak = asCharSequence().subSequence(changedRange.min, changedRange.max).toString()
        val continuation = MarkdownEditorEngine.continueDocumentList(
            source = originalText.toString(),
            newlineOffset = originalRange.min,
            lineBreak = lineBreak
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

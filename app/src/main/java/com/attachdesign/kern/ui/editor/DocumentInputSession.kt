package com.attachdesign.kern.ui.editor

import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.TextAttribute
import androidx.annotation.RequiresApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.content.ReceiveContentListener
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.PlatformTextInputMethodRequest

/** Observes edit origin; native input, clipboard handling and composition remain delegated. */
@OptIn(ExperimentalFoundationApi::class)
internal class DocumentInputContext {
    private var preserving = 0
    private var batchDepth = 0
    private var preserveBatch = false
    private var committing = 0
    private var commitInBatch = false
    private var transferPending = false
    private var transferGeneration = 0
    private val handler = Handler(Looper.getMainLooper())
    val isCommitting: Boolean get() = committing > 0 || commitInBatch

    val contentReceiver = ReceiveContentListener { content ->
        val generation = ++transferGeneration
        transferPending = true
        handler.post { if (transferGeneration == generation) transferPending = false }
        content
    }

    fun consumePreservedInput(): Boolean {
        val transfer = transferPending
        transferPending = false
        return preserving > 0 || preserveBatch || transfer
    }

    fun <T> preserve(block: () -> T): T {
        preserving++
        if (batchDepth > 0) preserveBatch = true
        return try { block() } finally { preserving-- }
    }

    fun <T> commit(block: () -> T): T {
        committing++
        if (batchDepth > 0) commitInBatch = true
        return try { block() } finally { committing-- }
    }

    fun beginBatch() { batchDepth++ }

    fun endBatch() {
        batchDepth = (batchDepth - 1).coerceAtLeast(0)
        if (batchDepth == 0) { preserveBatch = false; commitInBatch = false }
    }

    fun reset() {
        batchDepth = 0
        preserveBatch = false
        preserving = 0
        committing = 0
        commitInBatch = false
        transferPending = false
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun DocumentInputSession(context: DocumentInputContext, content: @Composable () -> Unit) {
    val interceptor = remember(context) {
        PlatformTextInputInterceptor { request, next ->
            next.startInputMethod(object : PlatformTextInputMethodRequest {
                override fun createInputConnection(outAttributes: EditorInfo): InputConnection {
                    context.reset()
                    val target = request.createInputConnection(outAttributes)
                    return when {
                        Build.VERSION.SDK_INT >= 34 -> DocumentInputConnection34(target, context)
                        Build.VERSION.SDK_INT >= 33 -> DocumentInputConnection33(target, context)
                        else -> DocumentInputConnection(target, context)
                    }
                }
            })
        }
    }
    InterceptPlatformTextInput(interceptor, content)
}

private open class DocumentInputConnection(target: InputConnection, protected val context: DocumentInputContext) :
    InputConnectionWrapper(target, false) {

    override fun beginBatchEdit(): Boolean {
        context.beginBatch()
        return super.beginBatchEdit().also { if (!it) context.endBatch() }
    }

    override fun endBatchEdit(): Boolean = try { super.endBatchEdit() } finally { context.endBatch() }

    override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean =
        context.preserve { super.setComposingText(text, newCursorPosition) }

    override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean =
        context.commit { super.commitText(text, newCursorPosition) }

    override fun setComposingRegion(start: Int, end: Int): Boolean =
        context.preserve { super.setComposingRegion(start, end) }

    override fun performContextMenuAction(id: Int): Boolean =
        if (id == android.R.id.paste || id == android.R.id.pasteAsPlainText) {
            context.preserve { super.performContextMenuAction(id) }
        } else super.performContextMenuAction(id)
}

@RequiresApi(33)
private open class DocumentInputConnection33(target: InputConnection, context: DocumentInputContext) :
    DocumentInputConnection(target, context) {
    override fun setComposingText(text: CharSequence, newCursorPosition: Int, textAttribute: TextAttribute?): Boolean =
        context.preserve { super.setComposingText(text, newCursorPosition, textAttribute) }

    override fun commitText(text: CharSequence, newCursorPosition: Int, textAttribute: TextAttribute?): Boolean =
        context.commit { super.commitText(text, newCursorPosition, textAttribute) }

    override fun setComposingRegion(start: Int, end: Int, textAttribute: TextAttribute?): Boolean =
        context.preserve { super.setComposingRegion(start, end, textAttribute) }
}

@RequiresApi(34)
private class DocumentInputConnection34(target: InputConnection, context: DocumentInputContext) :
    DocumentInputConnection33(target, context) {
    override fun replaceText(start: Int, end: Int, text: CharSequence, newCursorPosition: Int,
        textAttribute: TextAttribute?): Boolean = context.preserve {
        super.replaceText(start, end, text, newCursorPosition, textAttribute)
    }
}

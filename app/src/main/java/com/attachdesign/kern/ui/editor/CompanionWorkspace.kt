package com.attachdesign.kern.ui.editor

import androidx.compose.runtime.Immutable
import com.attachdesign.kern.parser.MarkdownParser
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class ReferenceKey(val projectId: Long, val path: String)

enum class CompanionMode { PREVIEW, REFERENCE }

@Immutable
data class CompanionState(
    val isOpen: Boolean = false,
    val mode: CompanionMode = CompanionMode.PREVIEW,
    val reference: ReferenceKey? = null,
    val blocks: ImmutableParagraphList = ImmutableParagraphList(emptyList<ImmutableParagraphBlock>().toImmutableList()),
    val isLoading: Boolean = false,
    val error: String? = null
)

/** Read-only reference ownership. Never calls the primary editor's load, edit, undo, or save paths. */
class CompanionWorkspace(
    private val scope: CoroutineScope,
    private val read: suspend (ReferenceKey) -> String,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val mutableState = MutableStateFlow(CompanionState())
    val state = mutableState.asStateFlow()
    private var request = 0L
    private var readJob: Job? = null

    fun setOpen(open: Boolean) { mutableState.value = mutableState.value.copy(isOpen = open) }

    fun showPreview() {
        ++request
        readJob?.cancel()
        mutableState.value = mutableState.value.copy(mode = CompanionMode.PREVIEW, isLoading = false, error = null)
    }

    fun showReference(key: ReferenceKey) {
        val generation = ++request
        readJob?.cancel()
        mutableState.value = mutableState.value.copy(
            mode = CompanionMode.REFERENCE, reference = key, isLoading = true, error = null,
            blocks = ImmutableParagraphList(emptyList<ImmutableParagraphBlock>().toImmutableList())
        )
        readJob = scope.launch {
            try {
                val content = read(key)
                val blocks = withContext(dispatcher) {
                    ImmutableParagraphList(MarkdownParser.parseDocument(content).map(::ImmutableParagraphBlock).toImmutableList())
                }
                if (generation == request) mutableState.value = mutableState.value.copy(blocks = blocks, isLoading = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (generation == request) mutableState.value = mutableState.value.copy(
                    isLoading = false, error = "Couldn't open this reference. Check file access or choose another file."
                )
            }
        }
    }

    fun retry() { state.value.reference?.let(::showReference) }
}

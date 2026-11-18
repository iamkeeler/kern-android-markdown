package com.attachdesign.kern.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.ImeAction
import com.attachdesign.kern.search.NoteTextSearch
import com.attachdesign.kern.ui.theme.AppColorTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal data class DocumentFindHighlight(val source: String, val range: IntRange, val style: SpanStyle)

@Stable
internal class DocumentFindSession(open: Boolean, query: String, index: Int) {
    var open by mutableStateOf(open)
        private set
    var query by mutableStateOf(query)
        private set
    var index by mutableIntStateOf(index)
    var requestFocus by mutableStateOf(false)

    fun show() {
        open = true
        requestFocus = true
    }

    fun updateQuery(value: String) {
        if (query.trim() != value.trim()) index = 0
        query = value
    }

    fun close() {
        open = false
        requestFocus = false
    }

    companion object {
        val Saver = listSaver(
            save = { listOf(it.open, it.query, it.index) },
            restore = { DocumentFindSession(it[0] as Boolean, it[1] as String, it[2] as Int) }
        )
    }
}

@Composable
internal fun rememberDocumentFindSession(initialQuery: String): DocumentFindSession =
    rememberSaveable(saver = DocumentFindSession.Saver) {
        DocumentFindSession(open = initialQuery.isNotBlank(), query = initialQuery, index = 0)
    }

@Composable
internal fun findInDocument(source: String, query: String): List<IntRange>? = key(source, query) {
    if (query.isEmpty()) emptyList()
    else produceState<List<IntRange>?>(null) {
        value = withContext(Dispatchers.Default) {
            NoteTextSearch.matches(source, query).onEach { ensureActive() }.toList()
        }
    }.value
}

@Composable
internal fun DocumentFindBar(
    query: String,
    onQueryChange: (String) -> Unit,
    count: Int?,
    index: Int,
    requestFocus: Boolean,
    onFocusRequested: () -> Unit,
    onNavigate: (Int) -> Unit,
    onClose: () -> Unit,
    theme: AppColorTheme
) {
    val requester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(requestFocus) {
        if (requestFocus) {
            requester.requestFocus()
            keyboard?.show()
            onFocusRequested()
        }
    }
    Column(Modifier.fillMaxWidth().background(theme.background)
        .padding(horizontal = theme.dimensions.spacingExtraLarge)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Find in note") },
            trailingIcon = {
                IconButton(onClose) { Icon(Icons.Default.Close, "Close Find", tint = theme.indicator) }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().focusRequester(requester),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = theme.textPrimary,
                unfocusedTextColor = theme.textPrimary,
                focusedBorderColor = theme.controlAccent,
                unfocusedBorderColor = theme.indicator,
                focusedLabelColor = theme.textAccent,
                unfocusedLabelColor = theme.textSecondary,
                cursorColor = theme.controlAccent
            )
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when {
                    query.isBlank() -> ""
                    count == null -> "Finding matches…"
                    count == 0 -> "No matches"
                    else -> "${index + 1} of $count"
                },
                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                color = theme.textSecondary,
                fontSize = theme.typography.helper
            )
            val canNavigate = (count ?: 0) > 1
            IconButton(onClick = { onNavigate(-1) }, enabled = canNavigate) {
                Icon(Icons.Default.KeyboardArrowUp, "Previous match",
                    tint = if (canNavigate) theme.indicator else theme.textMuted.copy(alpha = 0.35f))
            }
            IconButton(onClick = { onNavigate(1) }, enabled = canNavigate) {
                Icon(Icons.Default.KeyboardArrowDown, "Next match",
                    tint = if (canNavigate) theme.indicator else theme.textMuted.copy(alpha = 0.35f))
            }
        }
    }
}

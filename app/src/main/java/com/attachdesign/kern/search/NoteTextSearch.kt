package com.attachdesign.kern.search

import java.io.Reader
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

internal object NoteTextSearch {
    private const val LEADING_CONTEXT = 20
    private const val TRAILING_CONTEXT = 48
    private val whitespace = Regex("\\s+")

    fun matches(source: String, query: String): Sequence<IntRange> = sequence {
        if (query.isEmpty()) return@sequence
        var start = 0
        while (start <= source.length - query.length) {
            val hit = source.indexOf(query, start, ignoreCase = true)
            if (hit < 0) break
            start = hit + query.length
            yield(hit until start)
        }
    }

    suspend fun firstExcerpt(reader: Reader, query: String): String? {
        if (query.isEmpty()) return null
        val buffer = CharArray(8192)
        val window = StringBuilder()
        var hasEarlierText = false
        while (true) {
            coroutineContext.ensureActive()
            val count = reader.read(buffer)
            if (count == -1) return null
            window.append(buffer, 0, count)
            val hit = window.indexOf(query, ignoreCase = true)
            if (hit >= 0) {
                var start = (hit - LEADING_CONTEXT).coerceAtLeast(0)
                var end = (hit + minOf(query.length, 2 * TRAILING_CONTEXT) + TRAILING_CONTEXT)
                    .coerceAtMost(window.length)
                if (start > 0 && window[start].isLowSurrogate()) start--
                if (end < window.length && window[end].isLowSurrogate()) end++
                val excerpt = window.substring(start, end).replace(whitespace, " ").trim()
                return (if (start > 0 || hasEarlierText) "…" else "") + excerpt +
                    if (end < window.length) "…" else ""
            }
            // Retain enough overlap for a match and its leading context across reads.
            val discard = window.length - (query.length - 1 + LEADING_CONTEXT)
            if (discard > 0) {
                window.delete(0, discard)
                hasEarlierText = true
            }
        }
    }
}

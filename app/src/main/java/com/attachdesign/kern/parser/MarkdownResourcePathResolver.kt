package com.attachdesign.kern.parser

/** Resolves a Markdown resource target without depending on Android filesystem APIs. */
object MarkdownResourcePathResolver {
    private val uriScheme = Regex("^[A-Za-z][A-Za-z0-9+.-]*:")

    fun resolve(currentFilePath: String, target: String): String? {
        val cleanTarget = target.trim()
        if (cleanTarget.isEmpty()) return null
        if (uriScheme.containsMatchIn(cleanTarget)) return cleanTarget

        val segments = ArrayDeque<String>()
        if (!cleanTarget.startsWith('/')) {
            currentFilePath.substringBeforeLast('/', "")
                .split('/')
                .filterTo(segments) { it.isNotEmpty() && it != "." }
        }

        cleanTarget.removePrefix("/").split('/').forEach { segment ->
            when (segment) {
                "", "." -> Unit
                ".." -> if (segments.isEmpty()) return null else segments.removeLast()
                else -> segments.addLast(segment)
            }
        }
        return segments.joinToString("/").ifEmpty { null }
    }
}

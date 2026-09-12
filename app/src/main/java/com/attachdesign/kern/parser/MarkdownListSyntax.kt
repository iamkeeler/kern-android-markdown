package com.attachdesign.kern.parser

/** Shared recognition of the list prefixes supported by the editor. */
object MarkdownListSyntax {
    enum class Kind { UNORDERED, ORDERED, TASK }

    data class Marker(
        val kind: Kind,
        val indent: String,
        val marker: Char,
        val spacing: String,
        val end: Int,
        val ordinal: Int? = null,
        val checked: Boolean? = null
    ) {
        val contentStart: Int get() = end

        fun nextItemPrefix(): String = when (kind) {
            Kind.UNORDERED -> "$indent$marker$spacing"
            Kind.ORDERED -> "$indent${saturatingIncrement(ordinal)}$marker$spacing"
            Kind.TASK -> "$indent$marker$spacing[ ] "
        }

        /**
         * L10: the next ordinal, saturating at Int.MAX_VALUE instead of overflowing to
         * Int.MIN_VALUE for absurd inputs like `2147483647. `.
         */
        private fun saturatingIncrement(ordinal: Int?): Long =
            ((ordinal?.toLong() ?: 0L) + 1).coerceAtMost(Int.MAX_VALUE.toLong())
    }

    private val taskPattern = Regex("^([ \\t]*)([-*+])([ \\t]+)\\[([ xX])\\]([ \\t]+|$)")
    private val unorderedPattern = Regex("^([ \\t]*)([-*+])([ \\t]+|$)")
    private val orderedPattern = Regex("^([ \\t]*)(\\d+)([.)])([ \\t]+|$)")

    fun parse(line: String): Marker? {
        taskPattern.find(line)?.let { match ->
            return Marker(
                kind = Kind.TASK,
                indent = match.groupValues[1],
                marker = match.groupValues[2].single(),
                spacing = match.groupValues[3],
                end = match.range.last + 1,
                checked = match.groupValues[4].equals("x", ignoreCase = true)
            )
        }
        unorderedPattern.find(line)?.let { match ->
            return Marker(
                kind = Kind.UNORDERED,
                indent = match.groupValues[1],
                marker = match.groupValues[2].single(),
                spacing = match.groupValues[3],
                end = match.range.last + 1
            )
        }
        orderedPattern.find(line)?.let { match ->
            return Marker(
                kind = Kind.ORDERED,
                indent = match.groupValues[1],
                marker = match.groupValues[3].single(),
                spacing = match.groupValues[4],
                end = match.range.last + 1,
                ordinal = match.groupValues[2].toIntOrNull()
            )
        }
        return null
    }
}

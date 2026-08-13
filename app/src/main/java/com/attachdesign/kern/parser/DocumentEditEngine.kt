package com.attachdesign.kern.parser

/** Pure Markdown document edits shared by the current and state-based editor UIs. */
object DocumentEditEngine {
    sealed interface Command {
        data class Wrap(val opening: String, val closing: String = opening) : Command
        data class SetHeading(val level: Int) : Command
        data object CycleHeading : Command
        data object ToggleChecklist : Command
        data object ToggleBulletList : Command
        data object Indent : Command
        data object Outdent : Command
    }

    data class Result(
        val text: String,
        val selectionStart: Int,
        val selectionEnd: Int
    )

    private data class Replacement(val start: Int, val end: Int, val text: String)

    fun apply(
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
        command: Command,
        stickySelection: Boolean = true
    ): Result {
        val start = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
        val end = maxOf(selectionStart, selectionEnd).coerceIn(start, text.length)
        return when (command) {
            is Command.Wrap -> wrap(text, start, end, command, stickySelection)
            else -> editSelectedLines(text, start, end, command)
        }
    }

    private fun wrap(
        text: String,
        start: Int,
        end: Int,
        command: Command.Wrap,
        stickySelection: Boolean
    ): Result {
        if (start == end && text.startsWith(command.closing, start)) {
            val cursor = start + command.closing.length
            return Result(text, cursor, cursor)
        }

        val isAlreadyWrapped = start >= command.opening.length &&
            end + command.closing.length <= text.length &&
            text.regionMatches(start - command.opening.length, command.opening, 0, command.opening.length) &&
            text.regionMatches(end, command.closing, 0, command.closing.length)
        if (isAlreadyWrapped) {
            val unwrapped = text.removeRange(end, end + command.closing.length)
                .removeRange(start - command.opening.length, start)
            val newStart = start - command.opening.length
            val newEnd = end - command.opening.length
            return Result(unwrapped, newStart, if (stickySelection) newEnd else newStart)
        }

        val selected = text.substring(start, end)
        val replacement = command.opening + selected + command.closing
        val updated = text.replaceRange(start, end, replacement)
        val contentStart = start + command.opening.length
        val contentEnd = contentStart + selected.length
        val newEnd = when {
            start == end -> contentStart
            stickySelection -> contentEnd
            else -> start + replacement.length
        }
        return Result(updated, if (stickySelection || start == end) contentStart else newEnd, newEnd)
    }

    private fun editSelectedLines(
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
        command: Command
    ): Result {
        val lineStarts = selectedLineStarts(text, selectionStart, selectionEnd)
        val replacements = lineStarts.mapNotNull { lineStart ->
            val contentEnd = text.indexOf('\n', lineStart).let { if (it == -1) text.length else it }
            val logicalEnd = if (contentEnd > lineStart && text[contentEnd - 1] == '\r') contentEnd - 1 else contentEnd
            val line = text.substring(lineStart, logicalEnd)
            transformLine(line, command)?.let { transformed ->
                minimalReplacement(lineStart, line, transformed)
            }
        }
        if (replacements.isEmpty()) return Result(text, selectionStart, selectionEnd)

        val updated = StringBuilder(text).apply {
            replacements.asReversed().forEach { replacement ->
                replace(replacement.start, replacement.end, replacement.text)
            }
        }.toString()
        return Result(
            text = updated,
            selectionStart = mapOffset(selectionStart, replacements),
            selectionEnd = mapOffset(selectionEnd, replacements)
        )
    }

    private fun selectedLineStarts(text: String, start: Int, end: Int): List<Int> {
        val first = text.lastIndexOf('\n', start - 1).let { if (it == -1) 0 else it + 1 }
        val inclusiveEnd = if (end > start && end > 0 && text[end - 1] == '\n') end - 1 else end
        val starts = mutableListOf(first)
        var index = text.indexOf('\n', first)
        while (index != -1 && index < inclusiveEnd) {
            val next = index + 1
            if (next <= text.length) starts += next
            index = text.indexOf('\n', next)
        }
        return starts.distinct()
    }

    private fun transformLine(line: String, command: Command): String? {
        if (line.isBlank()) return null
        return when (command) {
            is Command.SetHeading -> setHeading(line, command.level)
            Command.CycleHeading -> cycleHeading(line)
            Command.ToggleChecklist -> toggleChecklist(line)
            Command.ToggleBulletList -> toggleBullet(line)
            Command.Indent -> "    $line"
            Command.Outdent -> outdent(line)
            is Command.Wrap -> null
        }
    }

    private fun setHeading(line: String, level: Int): String {
        val plain = line.replaceFirst(Regex("^#{1,6}\\s+"), "")
        return if (level in 1..6) "${"#".repeat(level)} $plain" else plain
    }

    private fun cycleHeading(line: String): String {
        val match = Regex("^(#{1,6})\\s+").find(line) ?: return "# $line"
        val level = match.groupValues[1].length
        val plain = line.substring(match.range.last + 1)
        return if (level == 6) plain else "${"#".repeat(level + 1)} $plain"
    }

    private fun toggleChecklist(line: String): String {
        val match = Regex("^(\\s*)[-*+]\\s+\\[([ xX])\\]\\s+").find(line)
            ?: return "- [ ] $line"
        val checked = !match.groupValues[2].equals(" ")
        val replacement = "${match.groupValues[1]}- [${if (checked) " " else "x"}] "
        return replacement + line.substring(match.range.last + 1)
    }

    private fun toggleBullet(line: String): String {
        val match = Regex("^(\\s*)[-*+]\\s+").find(line) ?: return "- $line"
        return match.groupValues[1] + line.substring(match.range.last + 1)
    }

    private fun outdent(line: String): String? = when {
        line.startsWith('\t') -> line.substring(1)
        line.startsWith("    ") -> line.substring(4)
        line.startsWith(" ") -> line.dropWhile { it == ' ' }.let { remainder ->
            val removed = (line.length - remainder.length).coerceAtMost(4)
            line.substring(removed)
        }
        else -> null
    }

    private fun minimalReplacement(lineStart: Int, old: String, new: String): Replacement {
        val commonPrefix = old.zip(new).takeWhile { (left, right) -> left == right }.size
        val maxSuffix = minOf(old.length - commonPrefix, new.length - commonPrefix)
        var commonSuffix = 0
        while (
            commonSuffix < maxSuffix &&
            old[old.lastIndex - commonSuffix] == new[new.lastIndex - commonSuffix]
        ) {
            commonSuffix++
        }
        return Replacement(
            start = lineStart + commonPrefix,
            end = lineStart + old.length - commonSuffix,
            text = new.substring(commonPrefix, new.length - commonSuffix)
        )
    }

    private fun mapOffset(offset: Int, replacements: List<Replacement>): Int {
        var delta = 0
        replacements.forEach { replacement ->
            if (offset < replacement.start) return@forEach
            val replacementDelta = replacement.text.length - (replacement.end - replacement.start)
            if (offset <= replacement.end) {
                if (replacement.start == replacement.end) {
                    return replacement.start + delta + replacement.text.length
                }
                val relative = (offset - replacement.start).coerceIn(0, replacement.text.length)
                return replacement.start + delta + relative
            }
            delta += replacementDelta
        }
        return offset + delta
    }
}

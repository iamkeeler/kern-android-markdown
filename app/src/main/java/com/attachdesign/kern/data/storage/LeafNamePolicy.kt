package com.attachdesign.kern.data.storage

/** Naming dialogs accept one leaf name, never a path or a workspace display alias. */
object LeafNamePolicy {
    fun validate(submittedName: String): String {
        require(submittedName.none { it.isISOControl() }) { "Names cannot contain control characters" }
        val name = submittedName.trim()
        require(name.isNotEmpty()) { "Name cannot be empty" }
        require(name != "." && name != "..") { "Name cannot be a dot path" }
        require('/' !in name && '\\' !in name) { "Enter a name, not a path" }
        return name
    }

    fun markdownFileName(submittedName: String): String {
        val name = validate(submittedName)
        return if (name.endsWith(".md", ignoreCase = true)) name else "$name.md"
    }

    internal fun requireRelativePath(path: String) {
        require(path.isEmpty() || path.split('/').all { it.isNotEmpty() && validate(it).isNotEmpty() }) {
            "Invalid relative path"
        }
    }
}

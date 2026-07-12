package com.attachdesign.kern.data.storage

/** Pure import policy for files opened through Android ACTION_VIEW. */
internal object IncomingFileImportPolicy {
    const val MAX_IMPORT_BYTES: Long = 5L * 1024L * 1024L

    private val supportedExtensions = setOf("md", "markdown", "mdown", "txt")
    private val supportedMimeTypes = setOf("text/plain", "text/markdown", "text/x-markdown")
    private val supportedSchemes = setOf("content", "file")

    fun isSupportedScheme(scheme: String?): Boolean = scheme?.lowercase() in supportedSchemes

    fun isSupportedType(fileName: String, mimeTypeHint: String?): Boolean {
        val extension = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        val normalizedMime = mimeTypeHint?.substringBefore(';')?.trim()?.lowercase()
        return extension in supportedExtensions || normalizedMime in supportedMimeTypes
    }

    fun sanitizeFileName(rawName: String): String {
        val leafName = rawName.trim().substringAfterLast('/').substringAfterLast('\\')
        val cleaned = leafName
            .replace(Regex("[\\\\/:*?\"<>|]+"), "-")
            .replace(Regex("\\s+"), " ")
            .trim('.', ' ')
        return if (cleaned.any { it.isLetterOrDigit() }) cleaned else "Opened File.md"
    }

    fun duplicateFileName(preferredName: String, counter: Int): String {
        require(counter >= 2) { "Counter must start at 2" }
        val dotIndex = preferredName.lastIndexOf('.')
        val base = if (dotIndex > 0) preferredName.substring(0, dotIndex) else preferredName
        val extension = if (dotIndex > 0) preferredName.substring(dotIndex) else ""
        return "$base ($counter)$extension"
    }
}

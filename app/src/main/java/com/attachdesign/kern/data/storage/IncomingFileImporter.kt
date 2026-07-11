package com.attachdesign.kern.data.storage

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import com.attachdesign.kern.data.local.AppDatabase
import com.attachdesign.kern.data.local.FileEntity
import com.attachdesign.kern.data.local.ProjectEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val SUPPORTED_EXTENSIONS = setOf("md", "markdown", "mdown", "txt")
private val SUPPORTED_MIME_TYPES = setOf("text/plain", "text/markdown", "text/x-markdown")
private const val OPENED_FILES_PROJECT_NAME = "Opened Files"
private const val OPENED_FILES_PROJECT_PATH = "opened-files"

/** Imports single-document ACTION_VIEW URIs into Kern's local workspace. */
class IncomingFileImporter(
    private val context: Context,
    private val db: AppDatabase,
    private val storageManager: StorageManager
) {
    data class ImportedFile(
        val projectId: Long,
        val filePath: String,
        val fileName: String
    )

    suspend fun import(uri: Uri, mimeTypeHint: String?): ImportedFile = withContext(Dispatchers.IO) {
        val fileName = resolveDisplayName(uri).sanitizeFileName()
        require(isSupported(fileName, mimeTypeHint)) {
            "Unsupported file type: $fileName${mimeTypeHint?.let { " ($it)" } ?: ""}"
        }

        val content = context.contentResolver.openInputStream(uri)?.use { inputStream ->
            inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } ?: throw IllegalArgumentException("Unable to read selected file")

        val project = getOrCreateOpenedFilesProject()
        val targetPath = uniqueFilePath(project, fileName)
        storageManager.writeFile(project, targetPath, content)

        db.fileDao().insertFile(
            FileEntity(
                projectId = project.id,
                name = targetPath.substringAfterLast('/'),
                relativePath = targetPath,
                isDirectory = false,
                lastModified = System.currentTimeMillis(),
                syncState = "PENDING"
            )
        )

        ImportedFile(
            projectId = project.id,
            filePath = targetPath,
            fileName = targetPath.substringAfterLast('/')
        )
    }

    private fun getOrCreateOpenedFilesProject(): ProjectEntity {
        val existing = db.projectDao().getAllProjects().firstOrNull {
            !it.isExternal && it.path == OPENED_FILES_PROJECT_PATH
        }
        if (existing != null) return existing

        val projectId = db.projectDao().insertProject(
            ProjectEntity(
                name = OPENED_FILES_PROJECT_NAME,
                path = OPENED_FILES_PROJECT_PATH,
                isExternal = false,
                isSelected = false
            )
        )
        return ProjectEntity(
            id = projectId,
            name = OPENED_FILES_PROJECT_NAME,
            path = OPENED_FILES_PROJECT_PATH,
            isExternal = false,
            isSelected = false
        )
    }

    private suspend fun uniqueFilePath(project: ProjectEntity, preferredName: String): String {
        val dotIndex = preferredName.lastIndexOf('.')
        val base = if (dotIndex > 0) preferredName.substring(0, dotIndex) else preferredName
        val extension = if (dotIndex > 0) preferredName.substring(dotIndex) else ""

        var candidate = preferredName
        var counter = 2
        while (db.fileDao().getFileByPath(project.id, candidate) != null || storageManager.fileExists(project, candidate)) {
            candidate = "$base ($counter)$extension"
            counter += 1
        }
        return candidate
    }

    private fun isSupported(fileName: String, mimeTypeHint: String?): Boolean {
        val extension = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        val normalizedMime = mimeTypeHint?.substringBefore(';')?.trim()?.lowercase()
        return extension in SUPPORTED_EXTENSIONS || normalizedMime in SUPPORTED_MIME_TYPES
    }

    private fun resolveDisplayName(uri: Uri): String {
        queryDisplayName(uri)?.let { return it }
        uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }?.let { return it }
        return "Opened File.md"
    }

    private fun queryDisplayName(uri: Uri): String? {
        val cursor: Cursor = try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )
        } catch (e: Exception) {
            null
        } ?: return null

        return cursor.use {
            if (!it.moveToFirst()) return@use null
            val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) it.getString(index) else null
        }?.takeIf { it.isNotBlank() }
    }

    private fun String.sanitizeFileName(): String {
        val cleaned = trim()
            .replace(Regex("[\\\\/:*?\"<>|]+"), "-")
            .replace(Regex("\\s+"), " ")
            .trim('.', ' ')
        return cleaned.ifBlank { "Opened File.md" }
    }
}

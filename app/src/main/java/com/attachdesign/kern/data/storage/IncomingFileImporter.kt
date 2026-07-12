package com.attachdesign.kern.data.storage

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import com.attachdesign.kern.data.storage.IncomingFileImportPolicy.MAX_IMPORT_BYTES
import com.attachdesign.kern.data.local.AppDatabase
import com.attachdesign.kern.data.local.FileEntity
import com.attachdesign.kern.data.local.ProjectEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

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
        require(IncomingFileImportPolicy.isSupportedScheme(uri.scheme)) {
            "Unsupported file source"
        }

        val fileName = IncomingFileImportPolicy.sanitizeFileName(resolveDisplayName(uri))
        require(IncomingFileImportPolicy.isSupportedType(fileName, mimeTypeHint)) {
            "Unsupported file type: $fileName${mimeTypeHint?.let { " ($it)" } ?: ""}"
        }

        queryFileSize(uri)?.let { size ->
            require(size <= MAX_IMPORT_BYTES) {
                "File is too large to import. Limit is ${MAX_IMPORT_BYTES / (1024 * 1024)} MB."
            }
        }

        val content = context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var totalBytes = 0L
            while (true) {
                val read = inputStream.read(buffer)
                if (read == -1) break
                totalBytes += read
                require(totalBytes <= MAX_IMPORT_BYTES) {
                    "File is too large to import. Limit is ${MAX_IMPORT_BYTES / (1024 * 1024)} MB."
                }
                output.write(buffer, 0, read)
            }
            output.toString(Charsets.UTF_8.name())
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
        var candidate = preferredName
        var counter = 2
        while (db.fileDao().getFileByPath(project.id, candidate) != null || storageManager.fileExists(project, candidate)) {
            candidate = IncomingFileImportPolicy.duplicateFileName(preferredName, counter)
            counter += 1
        }
        return candidate
    }

    private fun resolveDisplayName(uri: Uri): String {
        queryDisplayName(uri)?.let { return it }
        uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }?.let { return it }
        return "Opened File.md"
    }

    private fun queryFileSize(uri: Uri): Long? {
        val cursor = try {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.SIZE),
                null,
                null,
                null
            )
        } catch (e: Exception) {
            null
        } ?: return null

        return cursor.use {
            if (!it.moveToFirst()) return@use null
            val index = it.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && !it.isNull(index)) it.getLong(index) else null
        }?.takeIf { it >= 0 }
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
}

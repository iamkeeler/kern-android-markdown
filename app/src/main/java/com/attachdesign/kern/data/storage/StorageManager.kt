package com.attachdesign.kern.data.storage

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.attachdesign.kern.data.local.ProjectEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.os.Environment
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.util.UUID
import com.attachdesign.kern.parser.MarkdownResourcePathResolver

class StorageManager(
    private val context: Context,
    /**
     * Test-only override for the Documents/Kern root used by [getProjectRootFile].
     * Null (default) preserves the production path resolution.
     */
    private val sandboxRootOverride: File? = null
) {

    companion object {
        private const val PREFS_NAME = "kern_storage"
        private const val KEY_LEGACY_MIGRATION_DONE = "legacy_migration_completed"
    }

    private fun storagePrefs() =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun resolveMarkdownImage(
        project: ProjectEntity,
        currentFilePath: String,
        target: String
    ): String? = withContext(Dispatchers.IO) {
        val resolved = MarkdownResourcePathResolver.resolve(currentFilePath, target) ?: return@withContext null
        if (resolved == target && Uri.parse(target).scheme != null) return@withContext target
        if (project.path.startsWith("content://")) {
            getDocumentFile(project, resolved)?.uri?.toString()
        } else {
            getAbsoluteFile(project, resolved).takeIf { it.exists() && it.isFile }?.toURI()?.toString()
        }
    }

    private fun getDocumentFile(project: ProjectEntity, relativePath: String): DocumentFile? {
        val treeUri = Uri.parse(project.path)
        var doc = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        if (relativePath.isEmpty()) return doc
        
        val parts = relativePath.split('/')
        for (part in parts) {
            if (part.isEmpty()) continue
            val next = doc.findFile(part) ?: return null
            doc = next
        }
        return doc
    }

    private fun getOrCreateDocumentFile(project: ProjectEntity, relativePath: String, isDirectory: Boolean): DocumentFile? {
        val treeUri = Uri.parse(project.path)
        var doc = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        if (relativePath.isEmpty()) return doc
        
        val parts = relativePath.split('/')
        for (i in parts.indices) {
            val part = parts[i]
            if (part.isEmpty()) continue
            var next = doc.findFile(part)
            if (next == null) {
                next = if (i == parts.lastIndex && !isDirectory) {
                    doc.createFile("text/markdown", part)
                } else {
                    doc.createDirectory(part)
                }
            }
            if (next == null) return null
            doc = next
        }
        return doc
    }


    /**
     * Resolves a child file from a parent directory and ensures the resulting path
     * strictly resides within the parent directory, preventing path traversal vulnerabilities.
     */
    private fun getSafeFile(parent: File, childPath: String): File {
        val resolvedFile = File(parent, childPath)
        val parentCanonicalPath = parent.canonicalPath
        val resolvedCanonicalPath = resolvedFile.canonicalPath

        if (!resolvedCanonicalPath.startsWith(parentCanonicalPath + File.separator) &&
            resolvedCanonicalPath != parentCanonicalPath) {
            throw SecurityException("Path traversal attempt detected. Path: $childPath")
        }
        return resolvedFile
    }

    /**
     * Gets the root java.io.File directory for a project depending on whether it is
     * App-Sandbox (internal) or External Scoped Storage (SAF).
     *
     * The legacy migration from the old app-private root into Documents/Kern runs
     * at most once per install; completion is persisted in SharedPreferences (L4).
     */
    private fun getProjectRootFile(project: ProjectEntity): File {
        val rootDirName = if (project.isExternal) "external_saf" else "sandbox"

        val oldRootDir = File(context.filesDir, rootDirName)
        val newDocumentsDir = sandboxRootOverride
            ?: File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Kern")
        val newRootDir = File(newDocumentsDir, rootDirName)

        var finalRootDir = newRootDir
        if (!newRootDir.exists()) {
            val created = newRootDir.mkdirs()
            if (!created) {
                finalRootDir = oldRootDir
            }
        }

        if (!finalRootDir.exists()) {
            finalRootDir.mkdirs()
        }

        // One-time legacy migration: if the old root still holds files and the new
        // root is empty, copy everything over to preserve user data. Never re-runs.
        if (!storagePrefs().getBoolean(KEY_LEGACY_MIGRATION_DONE, false)) {
            var migrationOk = true
            if (oldRootDir.exists() && oldRootDir.isDirectory && finalRootDir.exists() && finalRootDir != oldRootDir) {
                val oldFiles = oldRootDir.listFiles()
                if (oldFiles != null && oldFiles.isNotEmpty() && finalRootDir.listFiles()?.isEmpty() == true) {
                    try {
                        oldRootDir.copyRecursively(finalRootDir, overwrite = true)
                    } catch (e: Exception) {
                        migrationOk = false
                        e.printStackTrace()
                    }
                }
            }
            if (migrationOk) {
                storagePrefs().edit().putBoolean(KEY_LEGACY_MIGRATION_DONE, true).apply()
            }
        }

        val projectDir = getSafeFile(finalRootDir, project.path)
        if (!projectDir.exists()) {
            projectDir.mkdirs()
        }

        return projectDir
    }

    /**
     * Lists files and folders at a relative path in the project workspace on Dispatchers.IO.
     */
    suspend fun listDirectory(project: ProjectEntity, relativePath: String): List<VfsNode> = withContext(Dispatchers.IO) {
        if (project.path.startsWith("content://")) {
            return@withContext try {
                val doc = getDocumentFile(project, relativePath)
                if (doc == null || !doc.isDirectory) {
                    emptyList()
                } else {
                    doc.listFiles()
                        .mapNotNull { file -> file.toVfsNodeOrNull(relativePath) }
                        .sortedWith(compareBy({ !it.isDirectory }, { it.name }))
                }
            } catch (_: SecurityException) {
                // Persisted SAF grants can be revoked outside the app. Treat an
                // inaccessible linked folder as empty so app startup can continue.
                emptyList()
            }
        }

        val projectRoot = getProjectRootFile(project)
        val targetDir = if (relativePath.isEmpty()) projectRoot else getSafeFile(projectRoot, relativePath)
        
        if (!targetDir.exists() || !targetDir.isDirectory) {
            return@withContext emptyList()
        }
        
        val files = targetDir.listFiles() ?: return@withContext emptyList()
        files.map { file ->
            val fileRelPath = if (relativePath.isEmpty()) file.name else "$relativePath/${file.name}"
            if (file.isDirectory) {
                VfsNode.Directory(file.name, fileRelPath)
            } else {
                VfsNode.File(file.name, fileRelPath, file.length(), file.lastModified())
            }
        }.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
    }

    /**
     * Checks whether a file or folder exists at the relative path.
     */
    suspend fun fileExists(project: ProjectEntity, relativePath: String): Boolean = withContext(Dispatchers.IO) {
        if (project.path.startsWith("content://")) {
            return@withContext getDocumentFile(project, relativePath) != null
        }

        val projectRoot = getProjectRootFile(project)
        val file = getSafeFile(projectRoot, relativePath)
        file.exists()
    }

    /**
     * Reads file content as a UTF-8 string on Dispatchers.IO.
     *
     * @throws FileNotFoundException if the file cannot be found or opened (C1:
     * callers must treat a missing file as a failure, never as empty content).
     */
    suspend fun readFile(project: ProjectEntity, relativePath: String): String = withContext(Dispatchers.IO) {
        if (project.path.startsWith("content://")) {
            val doc = getDocumentFile(project, relativePath)
                ?: throw FileNotFoundException("SAF document not found: $relativePath")
            return@withContext context.contentResolver.openInputStream(doc.uri)?.use { inputStream ->
                inputStream.bufferedReader().use { it.readText() }
            } ?: throw FileNotFoundException("Cannot open SAF document for reading: $relativePath")
        }

        val projectRoot = getProjectRootFile(project)
        val file = getSafeFile(projectRoot, relativePath)
        if (file.exists() && file.isFile) {
            file.readText(Charsets.UTF_8)
        } else {
            throw FileNotFoundException("File not found: $relativePath")
        }
    }

    /**
     * Writes UTF-8 string content to a file on Dispatchers.IO.
     *
     * Writes are atomic (H1): java.io targets go to a temp file which is fsync'd
     * and then renamed over the target; SAF targets are written to a temp document
     * that is renamed into place. Returns false (and leaves the original file
     * untouched) when the write could not be completed.
     */
    suspend fun writeFile(project: ProjectEntity, relativePath: String, content: String): Boolean = withContext(Dispatchers.IO) {
        if (project.path.startsWith("content://")) {
            return@withContext writeSafAtomically(project, relativePath, content.toByteArray(Charsets.UTF_8))
        }

        val projectRoot = getProjectRootFile(project)
        val file = getSafeFile(projectRoot, relativePath)
        writeAtomically(file, content)
    }

    /**
     * Atomic write for java.io: temp file in the same directory + fsync + rename
     * over the target (H1). Never leaves a truncated target behind.
     */
    private fun writeAtomically(target: File, content: String): Boolean {
        val parent = target.parentFile ?: return false
        if (!parent.exists() && !parent.mkdirs()) return false
        val tmp = File(parent, "${target.name}.tmp.${UUID.randomUUID()}")
        return try {
            FileOutputStream(tmp).use { fos ->
                fos.write(content.toByteArray(Charsets.UTF_8))
                fos.flush()
                fos.fd.sync()
            }
            if (!tmp.renameTo(target)) {
                // Cross-filesystem fallback: copy over the target, then drop the temp file.
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            true
        } catch (e: Exception) {
            tmp.delete()
            false
        }
    }

    /**
     * Atomic write for SAF: write to a temp document in the target directory,
     * then delete any existing document at the target path and rename the temp
     * document into place (H1). Returns false without touching the original when
     * any step fails.
     */
    private suspend fun writeSafAtomically(project: ProjectEntity, relativePath: String, bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        try {
            val parentPath = relativePath.substringBeforeLast('/', "")
            val fileName = relativePath.substringAfterLast('/')
            val treeUri = Uri.parse(project.path)
            var parentDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext false
            for (part in parentPath.split('/')) {
                if (part.isEmpty()) continue
                val next = parentDoc.findFile(part) ?: parentDoc.createDirectory(part)
                if (next == null) return@withContext false
                parentDoc = next
            }

            val tmpName = "$fileName.tmp.${UUID.randomUUID()}"
            val tmpDoc = parentDoc.createFile("text/markdown", tmpName) ?: return@withContext false
            try {
                context.contentResolver.openOutputStream(tmpDoc.uri, "rwt")?.use { outputStream ->
                    outputStream.write(bytes)
                    outputStream.flush()
                } ?: run {
                    tmpDoc.delete()
                    return@withContext false
                }
                parentDoc.findFile(fileName)?.delete()
                if (!tmpDoc.renameTo(fileName)) {
                    tmpDoc.delete()
                    return@withContext false
                }
                true
            } catch (e: Exception) {
                tmpDoc.delete()
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Creates a new empty file at the relative path on Dispatchers.IO.
     */
    suspend fun createFile(project: ProjectEntity, relativePath: String): Boolean = withContext(Dispatchers.IO) {
        if (project.path.startsWith("content://")) {
            val doc = getOrCreateDocumentFile(project, relativePath, isDirectory = false)
            return@withContext doc != null
        }

        val projectRoot = getProjectRootFile(project)
        val file = getSafeFile(projectRoot, relativePath)
        val parent = file.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        if (!file.exists()) {
            file.createNewFile()
        } else {
            false
        }
    }

    /**
     * Creates a new directory at the relative path on Dispatchers.IO.
     */
    suspend fun createDirectory(project: ProjectEntity, relativePath: String): Boolean = withContext(Dispatchers.IO) {
        if (project.path.startsWith("content://")) {
            val doc = getOrCreateDocumentFile(project, relativePath, isDirectory = true)
            return@withContext doc != null
        }

        val projectRoot = getProjectRootFile(project)
        val file = getSafeFile(projectRoot, relativePath)
        file.mkdirs()
    }

    /**
     * Deletes a file or directory recursively on Dispatchers.IO.
     * SAF directories are deleted children-first, since `DocumentFile.delete()`
     * refuses non-empty directories (M11).
     */
    suspend fun deleteFile(project: ProjectEntity, relativePath: String): Boolean = withContext(Dispatchers.IO) {
        if (project.path.startsWith("content://")) {
            val doc = getDocumentFile(project, relativePath) ?: return@withContext false
            return@withContext deleteDocumentTree(doc)
        }

        val projectRoot = getProjectRootFile(project)
        val file = getSafeFile(projectRoot, relativePath)
        if (file.exists()) {
            file.deleteRecursively()
        } else {
            false
        }
    }

    /** Recursively deletes a SAF document tree, children before parents. */
    private fun deleteDocumentTree(doc: DocumentFile): Boolean {
        if (doc.isDirectory) {
            for (child in doc.listFiles()) {
                if (!deleteDocumentTree(child)) return false
            }
        }
        return doc.delete()
    }

    /**
     * Renames a file or directory on Dispatchers.IO.
     *
     * On SAF this renames within the same parent directory only (M12/H4: use
     * [moveAcrossPaths] for moves across directories). Returns the actual result
     * of the rename instead of assuming success.
     */
    suspend fun renameFile(project: ProjectEntity, oldPath: String, newPath: String): Boolean = withContext(Dispatchers.IO) {
        if (project.path.startsWith("content://")) {
            val doc = getDocumentFile(project, oldPath) ?: return@withContext false
            val newName = newPath.substringAfterLast('/')
            return@withContext doc.renameTo(newName)
        }
        val projectRoot = getProjectRootFile(project)
        val oldFile = getSafeFile(projectRoot, oldPath)
        val newFile = getSafeFile(projectRoot, newPath)
        if (!oldFile.exists()) return@withContext false
        if (oldFile.renameTo(newFile)) return@withContext true
        // Cross-filesystem fallback: copy then delete; report success only when both succeed.
        return@withContext try {
            if (oldFile.isDirectory) {
                oldFile.copyRecursively(newFile, overwrite = true)
                oldFile.deleteRecursively()
            } else {
                oldFile.copyTo(newFile, overwrite = true)
                oldFile.delete()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Moves a file or directory across paths (e.g. into/out of `.trash`) on
     * Dispatchers.IO.
     *
     * SAF moves are a verified copy-then-delete (H4): read the source tree, write
     * it at the destination, and delete the source only after the copy verified.
     * Never uses `renameTo` across directories.
     */
    suspend fun moveAcrossPaths(project: ProjectEntity, oldPath: String, newPath: String): Boolean = withContext(Dispatchers.IO) {
        if (project.path.startsWith("content://")) {
            return@withContext safMoveCopyThenDelete(project, oldPath, project, newPath)
        }
        renameFile(project, oldPath, newPath)
    }

    /**
     * Verified SAF move: copy the source tree to the destination, then delete the
     * source tree. Returns false (leaving the source untouched) when the copy did
     * not fully succeed (C1/H4).
     */
    private suspend fun safMoveCopyThenDelete(
        fromProject: ProjectEntity,
        fromPath: String,
        toProject: ProjectEntity,
        toPath: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (!safCopyTree(fromProject, fromPath, toProject, toPath)) return@withContext false
        val srcDoc = getDocumentFile(fromProject, fromPath) ?: return@withContext false
        deleteDocumentTree(srcDoc)
    }

    /** Recursively copies a SAF document (file or directory tree) to a new path. */
    private suspend fun safCopyTree(
        fromProject: ProjectEntity,
        fromPath: String,
        toProject: ProjectEntity,
        toPath: String
    ): Boolean {
        val srcDoc = getDocumentFile(fromProject, fromPath) ?: return false
        return safCopyDocument(srcDoc, toProject, toPath)
    }

    private suspend fun safCopyDocument(srcDoc: DocumentFile, toProject: ProjectEntity, toPath: String): Boolean {
        if (srcDoc.isDirectory) {
            if (getOrCreateDocumentFile(toProject, toPath, isDirectory = true) == null) return false
            for (child in srcDoc.listFiles()) {
                val childName = child.name ?: return false
                if (!safCopyDocument(child, toProject, "$toPath/$childName")) return false
            }
            return true
        }
        val bytes = try {
            context.contentResolver.openInputStream(srcDoc.uri)?.use { it.readBytes() } ?: return false
        } catch (e: Exception) {
            return false
        }
        return writeSafAtomically(toProject, toPath, bytes)
    }

    /**
     * Moves a file or directory from one project/path to another project/path on Dispatchers.IO.
     *
     * SAF (or mixed SAF/java.io) moves are verified copy-then-delete (C1): the
     * source is deleted only after the read and the write both succeeded, and the
     * result reports whether the move actually happened.
     */
    suspend fun moveNode(fromProject: ProjectEntity, fromPath: String, toProject: ProjectEntity, toPath: String): Boolean = withContext(Dispatchers.IO) {
        if (fromProject.path.startsWith("content://") || toProject.path.startsWith("content://")) {
            return@withContext try {
                val content = readFile(fromProject, fromPath) // throws FileNotFoundException on failure
                if (!writeFile(toProject, toPath, content)) return@withContext false
                deleteFile(fromProject, fromPath)
            } catch (e: Exception) {
                false
            }
        }

        val srcRoot = getProjectRootFile(fromProject)
        val destRoot = getProjectRootFile(toProject)
        val srcFile = getSafeFile(srcRoot, fromPath)
        val destFile = getSafeFile(destRoot, toPath)

        if (!srcFile.exists()) return@withContext false

        val destParent = destFile.parentFile
        if (destParent != null && !destParent.exists()) {
            destParent.mkdirs()
        }

        if (srcFile.renameTo(destFile)) return@withContext true
        // Cross-filesystem fallback: copy then delete; report success only when both succeed.
        return@withContext try {
            if (srcFile.isDirectory) {
                srcFile.copyRecursively(destFile, overwrite = true)
                srcFile.deleteRecursively()
            } else {
                srcFile.copyTo(destFile, overwrite = true)
                srcFile.delete()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Resolves the absolute java.io.File path for a relative path inside a project.
     * Note: Avoid using this for content:// projects. Use exportToTempFile instead.
     *
     * Suspend with internal IO dispatch (L7): resolving the root can hit the disk.
     */
    suspend fun getAbsoluteFile(project: ProjectEntity, relativePath: String): File = withContext(Dispatchers.IO) {
        getSafeFile(getProjectRootFile(project), relativePath)
    }

    /**
     * Exports a file or directory content to a local temp File.
     */
    suspend fun exportToTempFile(project: ProjectEntity, relativePath: String, destFile: File): Boolean = withContext(Dispatchers.IO) {
        if (project.path.startsWith("content://")) {
            val doc = getDocumentFile(project, relativePath) ?: return@withContext false
            if (doc.isDirectory) return@withContext false
            context.contentResolver.openInputStream(doc.uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            return@withContext true
        } else {
            val srcFile = getAbsoluteFile(project, relativePath)
            if (srcFile.exists() && srcFile.isFile) {
                srcFile.copyTo(destFile, overwrite = true)
                return@withContext true
            }
            return@withContext false
        }
    }

    /**
     * Reads a bundled asset file as a UTF-8 string.
     * Suspend with internal IO dispatch (L7).
     */
    suspend fun readAssetFile(fileName: String): String = withContext(Dispatchers.IO) {
        context.assets.open(fileName).use { inputStream ->
            inputStream.bufferedReader().use { it.readText() }
        }
    }
}

internal fun DocumentFile.toVfsNodeOrNull(relativePath: String): VfsNode? {
    val documentName = name?.takeIf { it.isNotBlank() } ?: return null
    val fileRelativePath = if (relativePath.isEmpty()) documentName else "$relativePath/$documentName"
    return if (isDirectory) {
        VfsNode.Directory(documentName, fileRelativePath)
    } else {
        VfsNode.File(documentName, fileRelativePath, length(), lastModified())
    }
}

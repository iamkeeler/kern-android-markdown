package com.attachdesign.kern.data.storage

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.attachdesign.kern.data.local.ProjectEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.os.Environment
import java.io.File

class StorageManager(private val context: Context) {

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
     */
    private fun getProjectRootFile(project: ProjectEntity): File {
        val rootDirName = if (project.isExternal) "external_saf" else "sandbox"

        // Migration logic: move old files to the new accessible directory
        val oldRootDir = File(context.filesDir, rootDirName)
        val newDocumentsDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Kern")
        val newRootDir = File(newDocumentsDir, rootDirName)

        if (!newRootDir.exists()) {
            newRootDir.mkdirs()
        }

        // If old root exists and new root is empty, copy everything over to preserve user data
        if (oldRootDir.exists() && oldRootDir.isDirectory && newRootDir.exists()) {
            val oldFiles = oldRootDir.listFiles()
            if (oldFiles != null && oldFiles.isNotEmpty() && newRootDir.listFiles()?.isEmpty() == true) {
                try {
                    oldRootDir.copyRecursively(newRootDir, overwrite = true)
                    // Optionally delete old files after successful copy:
                    // oldRootDir.deleteRecursively()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        val projectDir = getSafeFile(newRootDir, project.path)
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
            val doc = getDocumentFile(project, relativePath) ?: return@withContext emptyList()
            if (!doc.isDirectory) return@withContext emptyList()
            val files = doc.listFiles()
            return@withContext files.map { file ->
                val fileRelPath = if (relativePath.isEmpty()) file.name!! else "$relativePath/${file.name}"
                if (file.isDirectory) {
                    VfsNode.Directory(file.name ?: "", fileRelPath)
                } else {
                    VfsNode.File(file.name ?: "", fileRelPath, file.length(), file.lastModified())
                }
            }.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
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
     * Reads file content as a UTF-8 string on Dispatchers.IO.
     */
    suspend fun readFile(project: ProjectEntity, relativePath: String): String = withContext(Dispatchers.IO) {
        if (project.path.startsWith("content://")) {
            val doc = getDocumentFile(project, relativePath) ?: return@withContext ""
            return@withContext context.contentResolver.openInputStream(doc.uri)?.use { inputStream ->
                inputStream.bufferedReader().use { it.readText() }
            } ?: ""
        }

        val projectRoot = getProjectRootFile(project)
        val file = getSafeFile(projectRoot, relativePath)
        if (file.exists() && file.isFile) {
            file.readText(Charsets.UTF_8)
        } else {
            ""
        }
    }

    /**
     * Writes UTF-8 string content to a file on Dispatchers.IO.
     */
    suspend fun writeFile(project: ProjectEntity, relativePath: String, content: String): Unit = withContext(Dispatchers.IO) {
        if (project.path.startsWith("content://")) {
            val doc = getOrCreateDocumentFile(project, relativePath, isDirectory = false) ?: return@withContext
            context.contentResolver.openOutputStream(doc.uri, "rwt")?.use { outputStream ->
                outputStream.bufferedWriter().use { it.write(content) }
            }
            return@withContext
        }

        val projectRoot = getProjectRootFile(project)
        val file = getSafeFile(projectRoot, relativePath)
        val parent = file.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        file.writeText(content, Charsets.UTF_8)
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
     */
    suspend fun deleteFile(project: ProjectEntity, relativePath: String): Boolean = withContext(Dispatchers.IO) {
        if (project.path.startsWith("content://")) {
            val doc = getDocumentFile(project, relativePath) ?: return@withContext false
            return@withContext doc.delete()
        }

        val projectRoot = getProjectRootFile(project)
        val file = getSafeFile(projectRoot, relativePath)
        if (file.exists()) {
            file.deleteRecursively()
        } else {
            false
        }
    }

    /**
     * Renames a file or directory on Dispatchers.IO.
     */
    suspend fun renameFile(project: ProjectEntity, oldPath: String, newPath: String): Boolean = withContext(Dispatchers.IO) {
        val projectRoot = getProjectRootFile(project)
        val oldFile = getSafeFile(projectRoot, oldPath)
        val newFile = getSafeFile(projectRoot, newPath)
        if (oldFile.exists()) {
            oldFile.renameTo(newFile)
        } else {
            false
        }
    }

    /**
     * Moves a file or directory from one project/path to another project/path on Dispatchers.IO.
     */
    suspend fun moveNode(fromProject: ProjectEntity, fromPath: String, toProject: ProjectEntity, toPath: String): Boolean = withContext(Dispatchers.IO) {
        if (fromProject.path.startsWith("content://") || toProject.path.startsWith("content://")) {
            val content = readFile(fromProject, fromPath)
            writeFile(toProject, toPath, content)
            deleteFile(fromProject, fromPath)
            return@withContext true
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
        
        srcFile.renameTo(destFile)
    }

    /**
     * Resolves the absolute java.io.File path for a relative path inside a project.
     * Note: Avoid using this for content:// projects. Use exportToTempFile instead.
     */
    fun getAbsoluteFile(project: ProjectEntity, relativePath: String): File {
        return getSafeFile(getProjectRootFile(project), relativePath)
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
}

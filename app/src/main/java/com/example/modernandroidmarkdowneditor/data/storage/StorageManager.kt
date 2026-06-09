package com.example.modernandroidmarkdowneditor.data.storage

import android.content.Context
import com.example.modernandroidmarkdowneditor.data.local.ProjectEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class StorageManager(private val context: Context) {

    /**
     * Gets the root java.io.File directory for a project depending on whether it is
     * App-Sandbox (internal) or External Scoped Storage (SAF).
     */
    private fun getProjectRootFile(project: ProjectEntity): File {
        val rootDirName = if (project.isExternal) "external_saf" else "sandbox"
        val rootDir = File(context.filesDir, rootDirName)
        if (!rootDir.exists()) {
            rootDir.mkdirs()
        }
        val projectDir = File(rootDir, project.path)
        if (!projectDir.exists()) {
            projectDir.mkdirs()
        }
        return projectDir
    }

    /**
     * Lists files and folders at a relative path in the project workspace on Dispatchers.IO.
     */
    suspend fun listDirectory(project: ProjectEntity, relativePath: String): List<VfsNode> = withContext(Dispatchers.IO) {
        val projectRoot = getProjectRootFile(project)
        val targetDir = if (relativePath.isEmpty()) projectRoot else File(projectRoot, relativePath)
        
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
        val projectRoot = getProjectRootFile(project)
        val file = File(projectRoot, relativePath)
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
        val projectRoot = getProjectRootFile(project)
        val file = File(projectRoot, relativePath)
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
        val projectRoot = getProjectRootFile(project)
        val file = File(projectRoot, relativePath)
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
        val projectRoot = getProjectRootFile(project)
        val file = File(projectRoot, relativePath)
        file.mkdirs()
    }

    /**
     * Deletes a file or directory recursively on Dispatchers.IO.
     */
    suspend fun deleteFile(project: ProjectEntity, relativePath: String): Boolean = withContext(Dispatchers.IO) {
        val projectRoot = getProjectRootFile(project)
        val file = File(projectRoot, relativePath)
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
        val oldFile = File(projectRoot, oldPath)
        val newFile = File(projectRoot, newPath)
        if (oldFile.exists()) {
            oldFile.renameTo(newFile)
        } else {
            false
        }
    }
}

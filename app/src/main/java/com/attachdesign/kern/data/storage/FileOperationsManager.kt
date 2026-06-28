package com.attachdesign.kern.data.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.attachdesign.kern.data.local.AppDatabase
import com.attachdesign.kern.data.local.FileEntity
import com.attachdesign.kern.data.local.ProjectEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class FileOperationsManager(
    private val db: AppDatabase,
    private val storageManager: StorageManager,
    private val context: Context
) {

    suspend fun duplicateNode(project: ProjectEntity, node: VfsNode): Result<VfsNode> = withContext(Dispatchers.IO) {
        try {
            val originalPath = node.relativePath
            val newPath = getDuplicatePath(project, originalPath)
            val newName = newPath.substringAfterLast('/')

            if (node is VfsNode.File) {
                val content = storageManager.readFile(project, originalPath)
                storageManager.writeFile(project, newPath, content)

                val currentFile = db.fileDao().getFileByPath(project.id, originalPath)
                val dupEntity = FileEntity(
                    projectId = project.id,
                    name = newName,
                    relativePath = newPath,
                    isDirectory = false,
                    lastModified = System.currentTimeMillis(),
                    syncState = if (project.isExternal) "SYNCED" else "PENDING",
                    wordCount = currentFile?.wordCount ?: 0,
                    characterCount = currentFile?.characterCount ?: 0,
                    readabilityGrade = currentFile?.readabilityGrade ?: "N/A"
                )
                db.fileDao().insertFile(dupEntity)
                Result.success(VfsNode.File(newName, newPath, dupEntity.characterCount.toLong(), dupEntity.lastModified, dupEntity.syncState))
            } else {
                // Directory duplication
                storageManager.createDirectory(project, newPath)
                val dirEntity = FileEntity(
                    projectId = project.id,
                    name = newName,
                    relativePath = newPath,
                    isDirectory = true,
                    lastModified = System.currentTimeMillis(),
                    syncState = "SYNCED"
                )
                db.fileDao().insertFile(dirEntity)

                // Duplicate all nested child files and folders
                val allProjectFiles = db.fileDao().getFilesForProject(project.id)
                val prefix = "$originalPath/"
                val children = allProjectFiles.filter { it.relativePath.startsWith(prefix) }

                // Write all files on disk first, then batch insert into DB.
                for (child in children) {
                    val suffix = child.relativePath.removePrefix(prefix)
                    val childNewPath = "$newPath/$suffix"
                    if (child.isDirectory) {
                        storageManager.createDirectory(project, childNewPath)
                    } else {
                        val content = try {
                            storageManager.readFile(project, child.relativePath)
                        } catch (e: Exception) {
                            ""
                        }
                        storageManager.writeFile(project, childNewPath, content)
                    }
                }

                // Now batch insert the child entities in a transaction
                db.runInTransaction {
                    for (child in children) {
                        val suffix = child.relativePath.removePrefix(prefix)
                        val childNewPath = "$newPath/$suffix"
                        val childNewName = childNewPath.substringAfterLast('/')
                        db.fileDao().insertFile(
                            FileEntity(
                                projectId = project.id,
                                name = childNewName,
                                relativePath = childNewPath,
                                isDirectory = child.isDirectory,
                                lastModified = System.currentTimeMillis(),
                                syncState = child.syncState,
                                wordCount = child.wordCount,
                                characterCount = child.characterCount,
                                readabilityGrade = child.readabilityGrade
                            )
                        )
                    }
                }
                Result.success(VfsNode.Directory(newName, newPath))
            }
        } catch (e: Exception) {
            Log.e("FileOpsManager", "Failed to duplicate node", e)
            Result.failure(e)
        }
    }

    suspend fun renameNode(project: ProjectEntity, node: VfsNode, newName: String): Result<VfsNode> = withContext(Dispatchers.IO) {
        try {
            val cleanName = newName.trim()
            if (cleanName.isEmpty()) return@withContext Result.failure(IllegalArgumentException("Name cannot be empty"))

            val oldPath = node.relativePath
            val parentDir = if (oldPath.contains('/')) oldPath.substringBeforeLast('/') + "/" else ""
            val newPath = "$parentDir$cleanName"

            val success = storageManager.renameFile(project, oldPath, newPath)
            if (!success) {
                return@withContext Result.failure(Exception("Failed to rename file on disk"))
            }

            if (!node.isDirectory) {
                val currentFile = db.fileDao().getFileByPath(project.id, oldPath)
                if (currentFile != null) {
                    db.runInTransaction {
                        db.fileDao().deleteFile(project.id, oldPath)
                        db.fileDao().insertFile(
                            currentFile.copy(
                                id = 0,
                                name = cleanName,
                                relativePath = newPath,
                                lastModified = System.currentTimeMillis(),
                                syncState = "PENDING"
                            )
                        )
                    }
                }
                Result.success(VfsNode.File(cleanName, newPath, currentFile?.characterCount?.toLong() ?: 0, System.currentTimeMillis()))
            } else {
                // Rename directory in DB and update all nested child paths
                val allProjectFiles = db.fileDao().getFilesForProject(project.id)
                val oldPrefix = "$oldPath/"
                val newPrefix = "$newPath/"
                val childrenToUpdate = allProjectFiles.filter { it.relativePath.startsWith(oldPrefix) }
                val currentDir = db.fileDao().getFileByPath(project.id, oldPath)

                db.runInTransaction {
                    // Update main directory entity
                    if (currentDir != null) {
                        db.fileDao().deleteFile(project.id, oldPath)
                        db.fileDao().insertFile(
                            currentDir.copy(
                                id = 0,
                                name = cleanName,
                                relativePath = newPath,
                                lastModified = System.currentTimeMillis(),
                                syncState = "SYNCED"
                            )
                        )
                    }

                    // Update child paths recursively
                    for (child in childrenToUpdate) {
                        val suffix = child.relativePath.removePrefix(oldPrefix)
                        val childNewPath = "$newPrefix$suffix"
                        db.fileDao().deleteFile(project.id, child.relativePath)
                        db.fileDao().insertFile(
                            child.copy(
                                id = 0,
                                relativePath = childNewPath,
                                lastModified = System.currentTimeMillis()
                            )
                        )
                    }
                }
                Result.success(VfsNode.Directory(cleanName, newPath))
            }
        } catch (e: Exception) {
            Log.e("FileOpsManager", "Failed to rename node", e)
            Result.failure(e)
        }
    }

    suspend fun deleteNode(project: ProjectEntity, node: VfsNode): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val path = node.relativePath
            val success = storageManager.deleteFile(project, path)
            if (!success) {
                // If it fails on disk, we might still want to prune DB, but let's log it or proceed.
            }

            if (!node.isDirectory) {
                db.fileDao().deleteFile(project.id, path)
            } else {
                val allProjectFiles = db.fileDao().getFilesForProject(project.id)
                val prefix = "$path/"
                val childrenToDelete = allProjectFiles.filter { it.relativePath.startsWith(prefix) }

                db.runInTransaction {
                    db.fileDao().deleteFile(project.id, path)
                    for (child in childrenToDelete) {
                        db.fileDao().deleteFile(project.id, child.relativePath)
                    }
                }
            }
            Result.success(true)
        } catch (e: Exception) {
            Log.e("FileOpsManager", "Failed to delete node", e)
            Result.failure(e)
        }
    }

    suspend fun shareNode(project: ProjectEntity, node: VfsNode) = withContext(Dispatchers.IO) {
        val cacheDir = context.cacheDir
        val shareDir = File(cacheDir, "shared_exports")
        
        if (shareDir.exists()) {
            shareDir.listFiles()?.forEach { file ->
                if (file.lastModified() < System.currentTimeMillis() - 3_600_000) {
                    file.delete()
                }
            }
        } else {
            shareDir.mkdirs()
        }

        val uriToShare: Uri? = if (node.isDirectory) {
            val zipFile = File(shareDir, "${node.name}_${System.currentTimeMillis()}.zip")
            try {
                ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                    suspend fun addDirToZip(dirNode: VfsNode, parentPath: String) {
                        val children = storageManager.listDirectory(project, dirNode.relativePath)
                        for (child in children) {
                            val entryName = if (parentPath.isEmpty()) child.name else "$parentPath/${child.name}"
                            if (child.isDirectory) {
                                zos.putNextEntry(ZipEntry("$entryName/"))
                                zos.closeEntry()
                                addDirToZip(child, entryName)
                            } else {
                                zos.putNextEntry(ZipEntry(entryName))
                                val contentBytes = storageManager.readFile(project, child.relativePath).toByteArray(Charsets.UTF_8)
                                zos.write(contentBytes)
                                zos.closeEntry()
                            }
                        }
                    }
                    addDirToZip(node, "")
                }
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
            } catch (e: Exception) {
                Log.e("FileOpsManager", "Failed to zip directory", e)
                null
            }
        } else {
            val shareFile = File(shareDir, node.name)
            val contentStr = storageManager.readFile(project, node.relativePath)
            shareFile.writeText(contentStr, Charsets.UTF_8)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", shareFile)
        }

        uriToShare?.let { uri ->
            withContext(Dispatchers.Main) {
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_STREAM, uri)
                    type = if (node.isDirectory) "application/zip" else "text/plain"
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(shareIntent, "Share ${node.name}").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            }
        }
    }

    private fun getDuplicatePath(project: ProjectEntity, originalPath: String): String {
        val dir = originalPath.substringBeforeLast('/', "")
        val fullName = originalPath.substringAfterLast('/')
        val extension = if (fullName.contains('.')) fullName.substringAfterLast('.') else ""
        val baseName = if (fullName.contains('.')) fullName.substringBeforeLast('.') else fullName

        var counter = 1
        var candidateName = "${baseName}_copy" + if (extension.isNotEmpty()) ".$extension" else ""
        var candidatePath = if (dir.isEmpty()) candidateName else "$dir/$candidateName"

        while (db.fileDao().getFileByPath(project.id, candidatePath) != null) {
            counter++
            candidateName = "${baseName}_copy_$counter" + if (extension.isNotEmpty()) ".$extension" else ""
            candidatePath = if (dir.isEmpty()) candidateName else "$dir/$candidateName"
        }
        return candidatePath
    }
}

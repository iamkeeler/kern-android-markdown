package com.attachdesign.kern.data.storage

import android.content.Context
import android.net.Uri
import android.util.Log
import com.attachdesign.kern.ui.common.SharePayload
import androidx.core.content.FileProvider
import com.attachdesign.kern.data.local.AppDatabase
import com.attachdesign.kern.data.local.FileEntity
import com.attachdesign.kern.data.local.ProjectEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.UUID

class FileOperationsManager(
    private val db: AppDatabase,
    private val storageManager: StorageManager,
    private val context: Context,
    private val newTrashId: () -> String = { UUID.randomUUID().toString() }
) {
    private val mutations = ProjectMutationCoordinator.forDatabase(db)

    private suspend fun <T> projectMutation(project: ProjectEntity, action: suspend () -> T): T =
        withContext(Dispatchers.IO) { mutations.withMutation(project.id, action) }

    suspend fun duplicateNode(project: ProjectEntity, node: VfsNode): Result<VfsNode> = projectMutation(project) {
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
                        } catch (cancelled: CancellationException) {
                            throw cancelled
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
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Log.e("FileOpsManager", "Failed to duplicate node", e)
            Result.failure(e)
        }
    }

    /** Compatibility for existing callers; partial changes are failures carrying their receipt. */
    suspend fun renameNode(project: ProjectEntity, node: VfsNode, newName: String): Result<VfsNode> =
        when (val outcome = renameNodeOutcome(project, node, newName)) {
            is MutationOutcome.Success -> Result.success(outcome.actual.node)
            else -> Result.failure(MutationOutcomeException(outcome))
        }

    suspend fun createFile(project: ProjectEntity, parentPath: String, submittedName: String): MutationOutcome =
        namedMutation(project, null) {
            val name = LeafNamePolicy.markdownFileName(submittedName)
            storageManager.createNamedFile(project, parentPath, name)
        }

    suspend fun createFolder(project: ProjectEntity, parentPath: String, submittedName: String): MutationOutcome =
        namedMutation(project, null) {
            val name = LeafNamePolicy.validate(submittedName)
            storageManager.createNamedDirectory(project, parentPath, name)
        }

    suspend fun renameNodeOutcome(project: ProjectEntity, node: VfsNode, submittedName: String): MutationOutcome =
        namedMutation(project, node.relativePath) {
            LeafNamePolicy.validate(submittedName)
            storageManager.renameNamedNode(project, node.relativePath, submittedName)
        }

    private suspend fun namedMutation(
        project: ProjectEntity,
        originalPath: String?,
        preserveSyncState: Boolean = false,
        partialBelongsToSource: () -> Boolean = { true },
        mutate: suspend () -> MutationOutcome
    ): MutationOutcome = projectMutation(project) {
        try {
            currentCoroutineContext().ensureActive()
            if (db.projectDao().getProjectById(project.id)?.path != project.path) {
                return@projectMutation MutationOutcome.Failed(MutationFailure.Missing)
            }
            // Read metrics/descendants before touching disk. A failed metadata read changes nothing.
            val rows = if (originalPath == null) emptyList() else db.fileDao().getFilesForProject(project.id)
                .filter { it.relativePath == originalPath || it.relativePath.startsWith("$originalPath/") }
            when (val disk = mutate()) {
                is MutationOutcome.Success -> {
                    if (!disk.changed) return@projectMutation disk
                    val receipt = metadataReceipt(project, originalPath, disk.actual, rows, preserveSyncState)
                    try {
                        commitMetadata(receipt)
                        disk
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        MutationOutcome.ReconciliationRequired(
                            actual = disk.actual,
                            originalPath = originalPath,
                            attemptedPath = disk.actual.node.relativePath,
                            returnedUri = disk.actual.documentUri,
                            returnedAbsolutePath = disk.actual.absolutePath,
                            cause = failure,
                            metadata = receipt
                        )
                    }
                }
                is MutationOutcome.ReconciliationRequired -> if (!partialBelongsToSource()) disk
                else disk.copy(metadata = MetadataReconciliation(
                    project.id, project.path, emptyList(), emptyList(), originalPath, rows, preserveSyncState
                ))
                is MutationOutcome.Failed -> disk
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (invalid: IllegalArgumentException) {
            MutationOutcome.Failed(MutationFailure.InvalidName, invalid)
        } catch (failure: Exception) {
            MutationOutcome.Failed(MutationFailure.Metadata, failure)
        }
    }

    /** Retry this Room receipt only after verifying its actual disk identity. Never creates/renames. */
    suspend fun reconcileMetadata(
        project: ProjectEntity,
        partial: MutationOutcome.ReconciliationRequired
    ): MutationOutcome = projectMutation(project) {
        val receipt = partial.metadata ?: return@projectMutation partial
        if (receipt.projectId != project.id || receipt.projectPath != project.path) return@projectMutation partial
        try {
            val verified = partial.actual?.let { storageManager.verifyNamedIdentity(project, it) }
                ?: storageManager.recoverNamedIdentity(project, partial)
            when (verified) {
                is MutationOutcome.Success -> {
                    val mappedReceipt = if (receipt.rowsToInsert.isEmpty()) {
                        metadataReceipt(project, receipt.originalPath, verified.actual, receipt.sourceRows, receipt.preserveSyncState)
                    } else receipt
                    commitMetadata(mappedReceipt)
                    MutationOutcome.Success(verified.actual)
                }
                is MutationOutcome.Failed -> partial.copy(
                    cause = verified.cause ?: IOException("Actual document is unavailable; refresh storage")
                )
                is MutationOutcome.ReconciliationRequired -> partial
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            partial.copy(cause = failure)
        }
    }

    private fun metadataReceipt(
        project: ProjectEntity,
        originalPath: String?,
        actual: StorageIdentity,
        sourceRows: List<FileEntity>,
        preserveSyncState: Boolean = false
    ): MetadataReconciliation {
        val node = actual.node
        val original = sourceRows.firstOrNull { it.relativePath == originalPath }
        val modified = (node as? VfsNode.File)?.lastModified ?: System.currentTimeMillis()
        val rootRow = (original ?: FileEntity(
            projectId = project.id,
            name = node.name,
            relativePath = node.relativePath,
            isDirectory = node.isDirectory,
            lastModified = modified,
            syncState = if (node.isDirectory || project.isExternal) "SYNCED" else "PENDING"
        )).copy(
            name = node.name,
            relativePath = node.relativePath,
            isDirectory = node.isDirectory,
            lastModified = modified,
            syncState = if (preserveSyncState && original != null) original.syncState
                else if (preserveSyncState) if (node.isDirectory || project.isExternal) "SYNCED" else "PENDING"
                else if (originalPath != null && !node.isDirectory) "PENDING"
                else if (node.isDirectory || project.isExternal) "SYNCED" else "PENDING"
        )
        val children = if (node.isDirectory && originalPath != null) sourceRows
            .filter { it.relativePath.startsWith("$originalPath/") }
            .map { it.copy(relativePath = node.relativePath + it.relativePath.removePrefix(originalPath)) }
        else emptyList()
        val targetRows = listOf(rootRow) + children
        return MetadataReconciliation(
            project.id,
            project.path,
            (sourceRows.map { it.relativePath } + targetRows.map { it.relativePath }).distinct(),
            targetRows,
            originalPath,
            sourceRows,
            preserveSyncState
        )
    }

    private fun commitMetadata(receipt: MetadataReconciliation) {
        db.runInTransaction {
            check(db.projectDao().getProjectById(receipt.projectId)?.path == receipt.projectPath) { "Workspace was removed or replaced" }
            receipt.pathsToRemove.forEach { db.fileDao().deleteFile(receipt.projectId, it) }
            receipt.rowsToInsert.forEach { db.fileDao().insertFile(it) }
        }
    }

    suspend fun deleteNode(project: ProjectEntity, node: VfsNode): Result<Boolean> =
        when (val outcome = deleteNodeOutcome(project, node)) {
            is DeleteOutcome.Success -> Result.success(true)
            else -> Result.failure(DeleteOutcomeException(outcome))
        }

    suspend fun deleteNodeOutcome(project: ProjectEntity, node: VfsNode): DeleteOutcome = projectMutation(project) {
        try {
            currentCoroutineContext().ensureActive()
            if (db.projectDao().getProjectById(project.id)?.path != project.path) {
                return@projectMutation DeleteOutcome.Failed(MutationFailure.Missing)
            }
            val paths = db.fileDao().getFilesForProject(project.id)
                .filter { it.relativePath == node.relativePath || it.relativePath.startsWith("${node.relativePath}/") }
                .map { it.relativePath }.plus(node.relativePath).distinct()
            when (val disk = storageManager.deletePermanentlyOutcome(project, node.relativePath)) {
                is DeleteOutcome.Success -> {
                    val receipt = DeleteMetadataReconciliation(project.id, project.path, node.relativePath, paths, disk.deletedIdentity)
                    try {
                        commitDeleteMetadata(receipt)
                        disk
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        DeleteOutcome.ReconciliationRequired(node.relativePath, disk.deletedIdentity, null, true, failure, receipt)
                    }
                }
                else -> disk
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            DeleteOutcome.Failed(MutationFailure.Metadata, failure)
        }
    }

    suspend fun reconcileDeleteMetadata(
        project: ProjectEntity,
        partial: DeleteOutcome.ReconciliationRequired
    ): DeleteOutcome = projectMutation(project) {
        val receipt = partial.metadata ?: return@projectMutation partial
        if (receipt.projectId != project.id || receipt.projectPath != project.path) return@projectMutation partial
        try {
            when (val inspected = storageManager.confirmDeletionAbsence(project, receipt.relativePath, receipt.deletedIdentity)) {
                is DeleteOutcome.Success -> {
                    commitDeleteMetadata(receipt)
                    DeleteOutcome.Success(receipt.relativePath, deletedIdentity = receipt.deletedIdentity)
                }
                is DeleteOutcome.Failed -> partial.copy(
                    remaining = inspected.remaining,
                    absenceConfirmed = false,
                    cause = inspected.cause ?: IOException("Deletion absence could not be confirmed")
                )
                is DeleteOutcome.ReconciliationRequired -> partial
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            partial.copy(cause = failure)
        }
    }

    private fun commitDeleteMetadata(receipt: DeleteMetadataReconciliation) {
        db.runInTransaction {
            check(db.projectDao().getProjectById(receipt.projectId)?.path == receipt.projectPath) { "Workspace was removed or replaced" }
            receipt.pathsToRemove.forEach { db.fileDao().deleteFile(receipt.projectId, it) }
        }
    }

    suspend fun shareNode(project: ProjectEntity, node: VfsNode): Result<SharePayload> = withContext(Dispatchers.IO) {
        try {
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

            var textContent: String? = null
            val mimeType = if (node.isDirectory) {
                "application/zip"
            } else if (node.name.endsWith(".md", ignoreCase = true) || node.name.endsWith(".markdown", ignoreCase = true)) {
                "text/markdown"
            } else {
                "text/plain"
            }

            val uriToShare: Uri? = if (node.isDirectory) {
                val zipFile = File(shareDir, "${node.name}_${System.currentTimeMillis()}.zip")
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
                                storageManager.openInputStream(project, child.relativePath)?.use { inputStream ->
                                    inputStream.copyTo(zos)
                                }
                                zos.closeEntry()
                            }
                        }
                    }
                    addDirToZip(node, "")
                }
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
            } else {
                val shareFile = File(shareDir, node.name)
                val contentStr = storageManager.readFile(project, node.relativePath)
                textContent = contentStr
                shareFile.writeText(contentStr, Charsets.UTF_8)
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", shareFile)
            }

            if (uriToShare != null) {
                Result.success(
                    SharePayload(
                        uri = uriToShare,
                        mimeType = mimeType,
                        textContent = if (!node.isDirectory) textContent else null,
                        title = "Share ${node.name}"
                    )
                )
            } else {
                Result.failure(IllegalStateException("Could not generate URI for ${node.name}"))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Log.e("FileOpsManager", "Failed to share ${node.name}", e)
            Result.failure(e)
        }
    }

    suspend fun moveToTrash(project: ProjectEntity, node: VfsNode): Result<VfsNode> =
        when (val outcome = moveToTrashOutcome(project, node)) {
            is MutationOutcome.Success -> Result.success(outcome.actual.node)
            else -> Result.failure(MutationOutcomeException(outcome))
        }

    suspend fun moveToTrashOutcome(project: ProjectEntity, node: VfsNode): MutationOutcome {
        var sourceMutationStarted = false
        return namedMutation(project, node.relativePath, preserveSyncState = true,
            partialBelongsToSource = { sourceMutationStarted }) {
            val trash = prepareTrashDirectory(project)
            if (trash !is MutationOutcome.Success) return@namedMutation trash
            val trashPath = trash.actual.node.relativePath
            var candidate: String? = null
            repeat(8) {
                if (candidate != null) return@repeat
                val name = LeafNamePolicy.validate("${newTrashId()}_${node.name}")
                when (val listing = storageManager.listDirectoryOutcome(project, trashPath)) {
                    is DirectoryOutcome.Success -> if (listing.nodes.none { it.name == name }) candidate = name
                    else -> return@namedMutation listingFailure(listing)
                }
            }
            val name = candidate ?: return@namedMutation MutationOutcome.Failed(MutationFailure.Collision)
            sourceMutationStarted = true
            storageManager.moveWithinProject(project, node.relativePath, trashPath, name)
        }
    }

    suspend fun restoreFromTrash(project: ProjectEntity, trashNode: VfsNode, originalPath: String): Result<VfsNode> =
        when (val outcome = restoreFromTrashOutcome(project, trashNode, originalPath)) {
            is MutationOutcome.Success -> Result.success(outcome.actual.node)
            else -> Result.failure(MutationOutcomeException(outcome))
        }

    suspend fun restoreFromTrashOutcome(project: ProjectEntity, trashNode: VfsNode, originalPath: String): MutationOutcome =
        namedMutation(project, trashNode.relativePath, preserveSyncState = true) {
            LeafNamePolicy.requireRelativePath(originalPath)
            if (originalPath.isEmpty()) return@namedMutation MutationOutcome.Failed(MutationFailure.InvalidName)
            storageManager.moveWithinProject(project, trashNode.relativePath,
                originalPath.substringBeforeLast('/', ""), originalPath.substringAfterLast('/'))
        }

    private suspend fun prepareTrashDirectory(project: ProjectEntity): MutationOutcome {
        return when (val listing = storageManager.listDirectoryOutcome(project, "")) {
            is DirectoryOutcome.Success -> {
                val existing = listing.nodes.firstOrNull { it.name == ".trash" }
                if (existing != null) {
                    if (existing.isDirectory) MutationOutcome.Success(StorageIdentity(existing), changed = false)
                    else MutationOutcome.Failed(MutationFailure.Collision)
                } else storageManager.createNamedDirectory(project, "", ".trash")
            }
            else -> listingFailure(listing)
        }
    }

    private fun listingFailure(outcome: DirectoryOutcome): MutationOutcome.Failed = when (outcome) {
        DirectoryOutcome.AccessDenied -> MutationOutcome.Failed(MutationFailure.AccessDenied)
        DirectoryOutcome.Missing -> MutationOutcome.Failed(MutationFailure.Missing)
        is DirectoryOutcome.Failure -> MutationOutcome.Failed(MutationFailure.Storage, outcome.cause)
        is DirectoryOutcome.Success -> error("A complete listing is not a failure")
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

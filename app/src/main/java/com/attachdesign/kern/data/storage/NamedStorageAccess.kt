package com.attachdesign.kern.data.storage

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import com.attachdesign.kern.data.local.ProjectEntity
import java.io.File
import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.FileVisitResult
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.NoSuchFileException
import java.nio.file.attribute.BasicFileAttributes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Direct provider access for StorageManager naming and enumeration; mutations are never replayed. */
internal class NamedStorageAccess(
    private val context: Context,
    private val projectRoot: (ProjectEntity) -> File
) {
    suspend fun listDirectory(project: ProjectEntity, relativePath: String): DirectoryOutcome =
        withContext(Dispatchers.IO) {
            val coroutineContext = currentCoroutineContext()
            val checkCancellation = { coroutineContext.ensureActive() }
            try {
                checkCancellation()
                LeafNamePolicy.requireRelativePath(relativePath)
                val nodes = if (project.path.startsWith("content://")) {
                    val directory = resolveDocument(project, relativePath, checkCancellation)
                    requireDirectory(directory)
                    children(directory, checkCancellation).map { identity(it, relativePath).node }
                } else {
                    val root = projectRoot(project)
                    val directory = safeFile(root, relativePath)
                    if (!Files.readAttributes(directory.toPath(), BasicFileAttributes::class.java).isDirectory) {
                        return@withContext DirectoryOutcome.Missing
                    }
                    val files = directory.listFiles() ?: throw IOException("Filesystem directory could not be listed")
                    files.map { file ->
                        checkCancellation()
                        identity(file, join(relativePath, file.name)).node
                    }
                }
                checkCancellation()
                DirectoryOutcome.Success(nodes.sortedWith(compareBy({ !it.isDirectory }, { it.name })))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SecurityException) {
                DirectoryOutcome.AccessDenied
            } catch (_: AccessDeniedException) {
                DirectoryOutcome.AccessDenied
            } catch (_: NoSuchFileException) {
                DirectoryOutcome.Missing
            } catch (failure: NamingStorageException) {
                if (failure.reason == MutationFailure.Missing) DirectoryOutcome.Missing
                else DirectoryOutcome.Failure(failure)
            } catch (failure: Exception) {
                DirectoryOutcome.Failure(failure)
            }
        }

    suspend fun create(
        project: ProjectEntity,
        parentPath: String,
        leafName: String,
        directory: Boolean
    ): MutationOutcome = mutate(null, join(parentPath, leafName.trim())) { attempted, committed ->
        LeafNamePolicy.requireRelativePath(parentPath)
        val name = LeafNamePolicy.validate(leafName)
        val path = join(parentPath, name)
        if (project.path.startsWith("content://")) {
            val parent = resolveDocument(project, parentPath)
            requireDirectory(parent)
            rejectCollision(parent, name)
            if (parent.flags and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE == 0) {
                throw NamingStorageException(MutationFailure.Unsupported)
            }
            val mime = if (directory) DocumentsContract.Document.MIME_TYPE_DIR else "text/markdown"
            attempted(null)
            val uri = DocumentsContract.createDocument(context.contentResolver, parent.uri, mime, name)
                ?: throw IOException("Provider did not create the document")
            committed(uri.toString(), null)
            val actual = readDocument(uri)
            verifyChild(parent, actual)
            identity(actual, parentPath)
        } else {
            val root = projectRoot(project)
            val parent = safeFile(root, parentPath)
            if (!parent.isDirectory) throw NamingStorageException(MutationFailure.Missing)
            val file = safeFile(root, path)
            if (file.exists()) throw NamingStorageException(MutationFailure.Collision)
            val created = if (directory) file.mkdir() else file.createNewFile()
            if (!created) {
                if (file.exists()) throw NamingStorageException(MutationFailure.Collision)
                throw IOException("Filesystem did not create the document")
            }
            committed(null, file.absolutePath)
            identity(file, path)
        }
    }

    suspend fun rename(
        project: ProjectEntity,
        originalPath: String,
        leafName: String,
        conservativeProviderFailure: Boolean = false
    ): MutationOutcome {
        val parentPath = originalPath.substringBeforeLast('/', "")
        var unchanged = false
        val result = mutate(originalPath, join(parentPath, leafName.trim()), conservativeProviderFailure) { attempted, committed ->
            LeafNamePolicy.requireRelativePath(originalPath)
            if (originalPath.isEmpty()) throw NamingStorageException(MutationFailure.InvalidName)
            val name = LeafNamePolicy.validate(leafName)
            val path = join(parentPath, name)
            if (project.path.startsWith("content://")) {
                val source = resolveDocument(project, originalPath)
                if (source.name == leafName || source.name == name) {
                    unchanged = true
                    return@mutate identity(source, parentPath)
                }
                val parent = resolveDocument(project, parentPath)
                rejectCollision(parent, name)
                if (source.flags and DocumentsContract.Document.FLAG_SUPPORTS_RENAME == 0) {
                    throw NamingStorageException(MutationFailure.Unsupported)
                }
                attempted(source.uri.toString())
                val uri = DocumentsContract.renameDocument(context.contentResolver, source.uri, name)
                    ?: throw IOException("Provider did not rename the document")
                committed(uri.toString(), null)
                val actual = readDocument(uri)
                verifyChild(parent, actual)
                identity(actual, parentPath)
            } else {
                val root = projectRoot(project)
                val source = safeFile(root, originalPath)
                if (!source.exists()) throw NamingStorageException(MutationFailure.Missing)
                if (source.name == leafName || source.name == name) {
                    unchanged = true
                    return@mutate identity(source, originalPath)
                }
                val destination = safeFile(root, path)
                if (destination.exists()) throw NamingStorageException(MutationFailure.Collision)
                // No REPLACE_EXISTING: an intervening destination must not be overwritten.
                Files.move(source.toPath(), destination.toPath())
                committed(null, destination.absolutePath)
                identity(destination, path)
            }
        }
        return if (result is MutationOutcome.Success) result.copy(changed = !unchanged) else result
    }

    suspend fun moveWithinProject(
        project: ProjectEntity,
        sourcePath: String,
        destinationParentPath: String,
        destinationLeafName: String
    ): MutationOutcome {
        val sourceParentPath = sourcePath.substringBeforeLast('/', "")
        if (sourceParentPath == destinationParentPath) {
            return resolveMoveFailure(project, rename(project, sourcePath, destinationLeafName, conservativeProviderFailure = true))
        }
        val targetLeaf = if (destinationLeafName == sourcePath.substringAfterLast('/')) destinationLeafName
            else destinationLeafName.trim()
        val targetPath = join(destinationParentPath, targetLeaf)
        val outcome = mutate(sourcePath, targetPath, conservativeProviderFailure = true) { attempted, committed ->
            LeafNamePolicy.requireRelativePath(sourcePath)
            LeafNamePolicy.requireRelativePath(destinationParentPath)
            LeafNamePolicy.validate(destinationLeafName)
            val name = targetLeaf
            if (sourcePath.isEmpty() || destinationParentPath == sourcePath ||
                destinationParentPath.startsWith("$sourcePath/")) {
                throw NamingStorageException(MutationFailure.InvalidName)
            }
            if (project.path.startsWith("content://")) {
                val source = resolveDocument(project, sourcePath)
                val sourceParent = resolveDocument(project, sourceParentPath)
                val destination = resolveDocument(project, destinationParentPath, forbiddenAncestorId = source.id)
                requireDirectory(sourceParent)
                requireDirectory(destination)
                verifyChild(sourceParent, source)
                val needsRename = name != source.name
                if (source.flags and DocumentsContract.Document.FLAG_SUPPORTS_MOVE == 0 ||
                    (needsRename && source.flags and DocumentsContract.Document.FLAG_SUPPORTS_RENAME == 0)) {
                    throw NamingStorageException(MutationFailure.Unsupported)
                }
                // Native move normally keeps the source name before the optional rename.
                // Check that intermediate name too; providers must never overwrite a sibling.
                val siblings = children(destination)
                if (siblings.any { it.name == name || it.name == source.name }) {
                    throw NamingStorageException(MutationFailure.Collision)
                }
                attempted(source.uri.toString())
                val movedUri = DocumentsContract.moveDocument(
                    context.contentResolver, source.uri, sourceParent.uri, destination.uri
                ) ?: throw IOException("Provider did not return a moved document identity")
                committed(movedUri.toString(), null)
                val moved = readDocument(movedUri)
                verifyChild(destination, moved)
                if (children(sourceParent).any { it.id == source.id }) {
                    throw IOException("Provider did not remove the original parent association")
                }
                if (!needsRename || moved.name == name) return@mutate identity(moved, destinationParentPath)
                if (moved.flags and DocumentsContract.Document.FLAG_SUPPORTS_RENAME == 0) {
                    throw NamingStorageException(MutationFailure.Unsupported)
                }
                rejectCollision(destination, name)
                val renamedUri = DocumentsContract.renameDocument(context.contentResolver, moved.uri, name)
                    ?: throw IOException("Document moved but provider did not return a renamed identity")
                committed(renamedUri.toString(), null)
                val renamed = readDocument(renamedUri)
                verifyChild(destination, renamed)
                identity(renamed, destinationParentPath)
            } else {
                val root = projectRoot(project)
                val source = safeFile(root, sourcePath)
                if (!source.exists()) throw NamingStorageException(MutationFailure.Missing)
                val destinationParent = safeFile(root, destinationParentPath)
                if (!destinationParent.isDirectory) throw NamingStorageException(MutationFailure.Missing)
                if (source.isDirectory && (destinationParent.canonicalPath == source.canonicalPath ||
                        destinationParent.canonicalPath.startsWith(source.canonicalPath + File.separator))) {
                    throw NamingStorageException(MutationFailure.InvalidName)
                }
                val destination = safeFile(root, join(destinationParentPath, name))
                if (destination.exists()) throw NamingStorageException(MutationFailure.Collision)
                Files.move(source.toPath(), destination.toPath())
                committed(null, destination.absolutePath)
                identity(destination, join(destinationParentPath, name))
            }
        }
        return resolveMoveFailure(project, outcome)
    }

    private suspend fun resolveMoveFailure(project: ProjectEntity, outcome: MutationOutcome): MutationOutcome {
        if (outcome !is MutationOutcome.ReconciliationRequired) return outcome
        // Resolve the last known URI at the real destination, even when the subsequent
        // rename failed. A provider that changed identity without returning it stays partial.
        val location = outcome.copy(returnedUri = outcome.returnedUri ?: outcome.originalUri)
        val recovered = recover(project, location)
        return if (recovered is MutationOutcome.Success) outcome.copy(actual = recovered.actual) else outcome
    }

    suspend fun deletePermanently(project: ProjectEntity, relativePath: String): DeleteOutcome =
        withContext(Dispatchers.IO) {
            val coroutineContext = currentCoroutineContext()
            var lastKnown: StorageIdentity? = null
            var attempted = false
            try {
                coroutineContext.ensureActive()
                lastKnown = findDeletionTarget(project, relativePath)
                    ?: return@withContext DeleteOutcome.Success(relativePath, alreadyAbsent = true)
                if (project.path.startsWith("content://")) {
                    val document = readDocument(Uri.parse(lastKnown.documentUri))
                    if (document.flags and DocumentsContract.Document.FLAG_SUPPORTS_DELETE == 0) {
                        return@withContext DeleteOutcome.Failed(MutationFailure.Unsupported, remaining = lastKnown)
                    }
                    coroutineContext.ensureActive()
                    attempted = true
                    if (!DocumentsContract.deleteDocument(context.contentResolver, document.uri)) {
                        throw IOException("Provider returned false when deleting the document")
                    }
                } else {
                    val file = File(checkNotNull(lastKnown.absolutePath))
                    attempted = true
                    // Do not follow descendant symlinks out of the selected tree.
                    Files.walkFileTree(file.toPath(), object : SimpleFileVisitor<Path>() {
                        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                            coroutineContext.ensureActive()
                            Files.delete(file)
                            return FileVisitResult.CONTINUE
                        }

                        override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                            coroutineContext.ensureActive()
                            if (exc != null) throw exc
                            Files.delete(dir)
                            return FileVisitResult.CONTINUE
                        }
                    })
                }
                coroutineContext.ensureActive()
                if (findDeletionTarget(project, relativePath, lastKnown.absolutePath) != null) {
                    throw IOException("Document is still present after deletion")
                }
                DeleteOutcome.Success(relativePath, deletedIdentity = lastKnown)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (!attempted) {
                    val classified = failed(failure)
                    DeleteOutcome.Failed(classified.reason, failure, lastKnown)
                } else {
                    var remaining: StorageIdentity? = null
                    var absent = false
                    try {
                        remaining = findDeletionTarget(project, relativePath, lastKnown?.absolutePath)
                        absent = remaining == null
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // The attempted mutation is still indeterminate when inspection fails.
                    }
                    DeleteOutcome.ReconciliationRequired(relativePath, lastKnown, remaining, absent, failure)
                }
            }
        }

    suspend fun confirmAbsence(
        project: ProjectEntity,
        relativePath: String,
        deletedIdentity: StorageIdentity?
    ): DeleteOutcome = withContext(Dispatchers.IO) {
        try {
            currentCoroutineContext().ensureActive()
            val remaining = findDeletionTarget(project, relativePath, deletedIdentity?.absolutePath)
            if (remaining == null) DeleteOutcome.Success(relativePath, alreadyAbsent = true)
            else DeleteOutcome.Failed(MutationFailure.Storage, IOException("Document exists; refresh before metadata cleanup"), remaining)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            val classified = failed(failure)
            DeleteOutcome.Failed(classified.reason, failure)
        }
    }

    private fun findDeletionTarget(
        project: ProjectEntity,
        path: String,
        absolutePath: String? = null
    ): StorageIdentity? {
        LeafNamePolicy.requireRelativePath(path)
        if (path.isEmpty()) throw NamingStorageException(MutationFailure.InvalidName)
        if (project.path.startsWith("content://")) {
            val parentPath = path.substringBeforeLast('/', "")
            val parent = try {
                resolveDocument(project, parentPath).also(::requireDirectory)
            } catch (missing: NamingStorageException) {
                if (missing.reason == MutationFailure.Missing) return null
                throw missing
            }
            val matches = children(parent).filter { it.name == path.substringAfterLast('/') }
            if (matches.isEmpty()) return null
            if (matches.size != 1) throw IOException("Ambiguous provider document name")
            return identity(matches.single(), parentPath)
        }
        val file = absolutePath?.let(::File) ?: safeFile(projectRoot(project), path)
        try {
            Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
        } catch (_: NoSuchFileException) {
            return null
        }
        return identity(file, path)
    }

    /** Checks a receipt's actual location before retrying its metadata transaction. */
    suspend fun verify(project: ProjectEntity, expected: StorageIdentity): MutationOutcome =
        withContext(Dispatchers.IO) {
            try {
                currentCoroutineContext().ensureActive()
                val actual = if (project.path.startsWith("content://")) {
                    val uri = expected.documentUri ?: throw IOException("Missing document identity")
                    val parentPath = expected.node.relativePath.substringBeforeLast('/', "")
                    val document = readDocument(Uri.parse(uri))
                    verifyChild(resolveDocument(project, parentPath), document)
                    identity(document, parentPath)
                } else {
                    val file = expected.absolutePath?.let(::File)
                        ?: throw IOException("Missing filesystem identity")
                    identity(file, expected.node.relativePath)
                }
                if (actual.node.relativePath != expected.node.relativePath ||
                    actual.node.isDirectory != expected.node.isDirectory) {
                    throw IOException("Document identity changed; refresh storage before reconciling")
                }
                if (actual.node is VfsNode.File && expected.node is VfsNode.File &&
                    (actual.node.size != expected.node.size || actual.node.lastModified != expected.node.lastModified)) {
                    throw IOException("Document changed; refresh storage before reconciling")
                }
                MutationOutcome.Success(actual, changed = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                failed(failure)
            }
        }

    /** Resolve only a known post-mutation location; a null/indeterminate URI needs a refresh. */
    suspend fun recover(project: ProjectEntity, partial: MutationOutcome.ReconciliationRequired): MutationOutcome =
        withContext(Dispatchers.IO) {
            try {
                currentCoroutineContext().ensureActive()
                val parentPath = partial.attemptedPath.substringBeforeLast('/', "")
                val actual = if (project.path.startsWith("content://")) {
                    val uri = partial.returnedUri ?: return@withContext partial
                    val document = readDocument(Uri.parse(uri))
                    verifyChild(resolveDocument(project, parentPath), document)
                    identity(document, parentPath)
                } else {
                    val file = partial.returnedAbsolutePath?.let(::File) ?: return@withContext partial
                    identity(file, partial.attemptedPath)
                }
                MutationOutcome.Success(actual)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                partial.copy(cause = failure)
            }
        }

    private suspend fun mutate(
        originalPath: String?,
        attemptedPath: String,
        conservativeProviderFailure: Boolean = false,
        action: ((String?) -> Unit, (String?, String?) -> Unit) -> StorageIdentity
    ): MutationOutcome = withContext(Dispatchers.IO) {
        var returnedUri: String? = null
        var returnedAbsolutePath: String? = null
        var providerAttempted = false
        var originalUri: String? = null
        try {
            currentCoroutineContext().ensureActive()
            val actual = action({ sourceUri ->
                providerAttempted = true
                originalUri = sourceUri
            }) { uri, file ->
                returnedUri = uri
                returnedAbsolutePath = file
            }
            MutationOutcome.Success(actual)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if (returnedUri != null || returnedAbsolutePath != null ||
                (providerAttempted && (conservativeProviderFailure ||
                    (failure !is SecurityException && failure !is UnsupportedOperationException)))) {
                MutationOutcome.ReconciliationRequired(
                    actual = null,
                    originalPath = originalPath,
                    attemptedPath = attemptedPath,
                    returnedUri = returnedUri,
                    cause = failure,
                    returnedAbsolutePath = returnedAbsolutePath,
                    originalUri = originalUri
                )
            } else {
                failed(failure)
            }
        }
    }

    private fun resolveDocument(
        project: ProjectEntity,
        path: String,
        checkCancellation: () -> Unit = {},
        forbiddenAncestorId: String? = null
    ): ProviderDocument {
        LeafNamePolicy.requireRelativePath(path)
        val tree = Uri.parse(project.path)
        var document = readDocument(DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)), checkCancellation)
        if (document.id == forbiddenAncestorId) throw NamingStorageException(MutationFailure.InvalidName)
        if (path.isEmpty()) return document
        for (part in path.split('/')) {
            requireDirectory(document)
            checkCancellation()
            val matching = children(document, checkCancellation).filter { it.name == part }
            if (matching.isEmpty()) throw NamingStorageException(MutationFailure.Missing)
            if (matching.size != 1) throw IOException("Ambiguous provider document name")
            document = matching.single()
            if (document.id == forbiddenAncestorId) throw NamingStorageException(MutationFailure.InvalidName)
        }
        return document
    }

    private fun rejectCollision(parent: ProviderDocument, name: String) {
        requireDirectory(parent)
        if (children(parent).any { it.name == name }) throw NamingStorageException(MutationFailure.Collision)
    }

    private fun verifyChild(parent: ProviderDocument, actual: ProviderDocument) {
        val siblings = children(parent)
        if (siblings.none { it.id == actual.id } || siblings.count { it.name == actual.name } != 1) {
            throw IOException("Provider returned an ambiguous document location")
        }
    }

    private fun readDocument(uri: Uri, checkCancellation: () -> Unit = {}): ProviderDocument {
        val documents = query(uri, checkCancellation)
        if (documents.isEmpty()) throw NamingStorageException(MutationFailure.Missing)
        return documents.single()
    }

    private fun children(parent: ProviderDocument, checkCancellation: () -> Unit = {}): List<ProviderDocument> = query(
        DocumentsContract.buildChildDocumentsUriUsingTree(parent.uri, parent.id), checkCancellation
    )

    private fun query(uri: Uri, checkCancellation: () -> Unit = {}): List<ProviderDocument> {
        // DocumentFile 1.0.1 swallows exceptions and partial cursor failures. Never use it
        // to establish a collision-free parent or a committed document's actual identity.
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        checkCancellation()
        val cursor = context.contentResolver.query(uri, columns, Bundle.EMPTY, null)
            ?: throw IOException("Provider returned no document listing")
        return cursor.use {
            val documents = mutableListOf<ProviderDocument>()
            while (true) {
                checkCancellation()
                if (!it.moveToNext()) break
                val id = it.getString(it.getColumnIndexOrThrow(columns[0]))
                    ?: throw IOException("Provider document has no ID")
                val name = it.getString(it.getColumnIndexOrThrow(columns[1]))
                    ?: throw IOException("Provider document has no name")
                // Keep the provider's name verbatim, including legal surrounding spaces.
                LeafNamePolicy.validate(name)
                val mime = it.getString(it.getColumnIndexOrThrow(columns[2]))
                    ?.takeIf(String::isNotEmpty) ?: throw IOException("Provider document has no type")
                documents += ProviderDocument(
                    DocumentsContract.buildDocumentUriUsingTree(uri, id), id, name, mime,
                    it.getInt(it.getColumnIndexOrThrow(columns[3])),
                    it.getLong(it.getColumnIndexOrThrow(columns[4])),
                    it.getLong(it.getColumnIndexOrThrow(columns[5]))
                )
            }
            if (it.extras.getBoolean(DocumentsContract.EXTRA_LOADING, false)) {
                throw IOException("Provider listing is incomplete; try again when loading finishes")
            }
            checkCancellation()
            documents
        }
    }

    private fun identity(document: ProviderDocument, parentPath: String): StorageIdentity {
        val path = join(parentPath, document.name)
        val node = if (document.directory) VfsNode.Directory(document.name, path)
        else VfsNode.File(document.name, path, document.size, document.modified)
        return StorageIdentity(node, documentUri = document.uri.toString())
    }

    private fun identity(file: File, path: String): StorageIdentity {
        if (!file.exists()) throw NamingStorageException(MutationFailure.Missing)
        val node = if (file.isDirectory) VfsNode.Directory(file.name, path)
        else VfsNode.File(file.name, path, file.length(), file.lastModified())
        return StorageIdentity(node, absolutePath = file.absolutePath)
    }

    private fun safeFile(root: File, path: String): File {
        val file = File(root, path)
        val rootPath = root.canonicalPath
        if (file.canonicalPath != rootPath && !file.canonicalPath.startsWith(rootPath + File.separator)) {
            throw NamingStorageException(MutationFailure.InvalidName)
        }
        return file
    }

    private fun requireDirectory(document: ProviderDocument) {
        if (!document.directory) throw NamingStorageException(MutationFailure.Missing)
    }

    private fun failed(failure: Exception) = MutationOutcome.Failed(
        when (failure) {
            is NamingStorageException -> failure.reason
            is IllegalArgumentException -> MutationFailure.InvalidName
            is SecurityException, is AccessDeniedException -> MutationFailure.AccessDenied
            is FileAlreadyExistsException -> MutationFailure.Collision
            is NoSuchFileException -> MutationFailure.Missing
            is UnsupportedOperationException -> MutationFailure.Unsupported
            else -> MutationFailure.Storage
        }, failure
    )

    private fun join(parent: String, name: String) = if (parent.isEmpty()) name else "$parent/$name"

    private class NamingStorageException(val reason: MutationFailure) : IOException(reason.name)
    private data class ProviderDocument(
        val uri: Uri, val id: String, val name: String, val mime: String,
        val flags: Int, val size: Long, val modified: Long
    ) {
        val directory get() = mime == DocumentsContract.Document.MIME_TYPE_DIR
    }
}

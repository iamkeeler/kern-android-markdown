package com.attachdesign.kern.data.storage

import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.CursorWrapper
import android.database.MatrixCursor
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import java.io.FileNotFoundException
import java.io.IOException
import org.robolectric.Robolectric

/** Real resolver/DocumentsContract boundary; mutations can normalize names and return new IDs. */
class NamingTestDocumentsProvider : DocumentsProvider() {
    data class Entry(
        val id: String,
        val parent: String?,
        var name: String,
        val directory: Boolean,
        val bytes: ByteArray = byteArrayOf()
    )

    val entries = linkedMapOf(ROOT to Entry(ROOT, null, "Linked workspace", true))
    var normalize: (String) -> String = { it }
    var queryFailure: Exception? = null
    var mutationFailure: Exception? = null
    var failReadback = false
    var returnNullMutation = false
    var createCalls = 0
    var renameCalls = 0
    var supportsNativeMove = true
    var supportsRename = true
    var moveCalls = 0
    var lastMoveArguments: Triple<String, String, String>? = null
    var moveNormalize: (String) -> String = { it }
    var moveFailure: Exception? = null
    var moveFailureAfterCommit: Exception? = null
    var returnNullMove = false
    var supportsDelete = true
    var deleteCalls = 0
    var deleteFailure: Exception? = null
    var deleteFailureAfterCommit: Exception? = null
    var deleteNoOp = false
    var nullChildCursor = false
    var childCursorLoading = false
    var childCursorFailure: Exception? = null
    var childCursorFailureAfterRows = 1
    var onChildRow: (() -> Unit)? = null
    var openedCursors = 0
        private set
    var closedCursors = 0
        private set
    private var nextId = 1
    private var mutationCommitted = false

    fun add(name: String, directory: Boolean = false, parent: String = ROOT, bytes: ByteArray = byteArrayOf()): Entry {
        val entry = Entry("id-${nextId++}", parent, name, directory, bytes)
        entries[entry.id] = entry
        return entry
    }

    override fun onCreate() = true

    override fun queryRoots(projection: Array<out String>?): Cursor = MatrixCursor(
        projection ?: arrayOf(DocumentsContract.Root.COLUMN_ROOT_ID, DocumentsContract.Root.COLUMN_DOCUMENT_ID)
    ).apply { addRow(columnNames.map { if (it == DocumentsContract.Root.COLUMN_DOCUMENT_ID) ROOT else "root" }) }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        checkQuery()
        val entry = entries[documentId] ?: throw FileNotFoundException(documentId)
        return tracked(cursor(projection, listOf(entry)))
    }

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor? {
        checkQuery()
        if (nullChildCursor) return null
        val cursor = cursor(projection, entries.values.filter { it.parent == parentDocumentId })
        if (childCursorLoading) cursor.setExtras(Bundle().apply { putBoolean(DocumentsContract.EXTRA_LOADING, true) })
        return tracked(object : CursorWrapper(cursor) {
            private var rowsRead = 0
            override fun moveToNext(): Boolean {
                if (rowsRead == childCursorFailureAfterRows) childCursorFailure?.let { throw it }
                val available = super.moveToNext()
                if (available) {
                    rowsRead++
                    onChildRow?.invoke()
                }
                return available
            }
        })
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        var entry = entries[documentId]
        while (entry != null) {
            if (entry.parent == parentDocumentId) return true
            entry = entries[entry.parent]
        }
        return false
    }

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String? {
        createCalls++
        mutationFailure?.let { throw it }
        if (returnNullMutation) return null
        val actual = normalize(displayName)
        if (entries.values.any { it.parent == parentDocumentId && it.name == actual }) {
            throw IOException("Provider collision")
        }
        val entry = add(actual, mimeType == DocumentsContract.Document.MIME_TYPE_DIR, parentDocumentId)
        mutationCommitted = true
        return entry.id
    }

    override fun renameDocument(documentId: String, displayName: String): String? {
        renameCalls++
        mutationFailure?.let { throw it }
        if (returnNullMutation) return null
        val old = entries[documentId] ?: throw FileNotFoundException(documentId)
        val actual = normalize(displayName)
        if (entries.values.any { it.id != documentId && it.parent == old.parent && it.name == actual }) {
            throw IOException("Provider collision")
        }
        // Model providers that change document IDs on rename; old URI becomes invalid.
        val renamed = old.copy(id = "id-${nextId++}", name = actual)
        entries.remove(documentId)
        entries[renamed.id] = renamed
        entries.values.filter { it.parent == documentId }.forEach { child ->
            entries[child.id] = child.copy(parent = renamed.id)
        }
        mutationCommitted = true
        return renamed.id
    }

    override fun moveDocument(sourceDocumentId: String, sourceParentDocumentId: String, targetParentDocumentId: String): String? {
        moveCalls++
        lastMoveArguments = Triple(sourceDocumentId, sourceParentDocumentId, targetParentDocumentId)
        moveFailure?.let { throw it }
        if (returnNullMove) return null
        val source = entries[sourceDocumentId] ?: throw FileNotFoundException(sourceDocumentId)
        require(source.parent == sourceParentDocumentId) { "Wrong native source parent" }
        require(entries[targetParentDocumentId]?.directory == true) { "Wrong native destination parent" }
        val actualName = moveNormalize(source.name)
        if (entries.values.any { it.parent == targetParentDocumentId && it.name == actualName }) {
            throw IOException("Provider native move collision")
        }
        val moved = source.copy(id = "id-${nextId++}", parent = targetParentDocumentId, name = actualName)
        entries.remove(sourceDocumentId)
        entries[moved.id] = moved
        entries.values.filter { it.parent == sourceDocumentId }.forEach { child ->
            entries[child.id] = child.copy(parent = moved.id)
        }
        mutationCommitted = true
        moveFailureAfterCommit?.let { throw it }
        return moved.id
    }

    override fun deleteDocument(documentId: String) {
        deleteCalls++
        deleteFailure?.let { throw it }
        if (deleteNoOp) return
        if (entries[documentId] == null) throw FileNotFoundException(documentId)
        fun removeTree(id: String) {
            entries.values.filter { it.parent == id }.toList().forEach { removeTree(it.id) }
            entries.remove(id)
        }
        removeTree(documentId)
        deleteFailureAfterCommit?.let { throw it }
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor =
        throw UnsupportedOperationException("Naming must never read/write document text")

    private fun checkQuery() {
        queryFailure?.let { throw it }
        if (failReadback && mutationCommitted) throw IOException("Readback unavailable")
    }

    private fun tracked(cursor: Cursor): Cursor {
        openedCursors++
        return object : CursorWrapper(cursor) {
            override fun close() {
                if (!isClosed) closedCursors++
                super.close()
            }
        }
    }

    private fun cursor(projection: Array<out String>?, entries: List<Entry>): MatrixCursor = MatrixCursor(
        projection ?: arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
    ).apply {
        entries.forEach { entry ->
            addRow(columnNames.map { column ->
                when (column) {
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID -> entry.id
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME -> entry.name
                    DocumentsContract.Document.COLUMN_MIME_TYPE -> if (entry.directory) DocumentsContract.Document.MIME_TYPE_DIR else "text/markdown"
                    DocumentsContract.Document.COLUMN_FLAGS ->
                        (if (supportsRename) DocumentsContract.Document.FLAG_SUPPORTS_RENAME else 0) or
                        (if (supportsNativeMove) DocumentsContract.Document.FLAG_SUPPORTS_MOVE else 0) or
                        (if (supportsDelete) DocumentsContract.Document.FLAG_SUPPORTS_DELETE else 0) or
                        (if (entry.directory) DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE else 0)
                    DocumentsContract.Document.COLUMN_SIZE -> entry.bytes.size
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED -> 123L
                    else -> null
                }
            })
        }
    }

    companion object {
        const val AUTHORITY = "com.attachdesign.kern.naming-test"
        const val ROOT = "workspace"
        const val TREE_URI = "content://$AUTHORITY/tree/$ROOT"

        fun register(context: Context): NamingTestDocumentsProvider = Robolectric.buildContentProvider(
            NamingTestDocumentsProvider::class.java
        ).create(ProviderInfo().apply {
            authority = AUTHORITY
            exported = true
            grantUriPermissions = true
            readPermission = "android.permission.MANAGE_DOCUMENTS"
            writePermission = "android.permission.MANAGE_DOCUMENTS"
            applicationInfo = context.applicationInfo
        }).get()
    }
}

package com.attachdesign.kern.data.storage

import com.attachdesign.kern.data.local.AppDatabase
import com.attachdesign.kern.data.local.ProjectEntity
import com.attachdesign.kern.utils.TextAnalysisUtils
import java.io.IOException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class DocumentSnapshot(val revision: Long, val draft: RecoveryDraft)

/** Serializes recovery and destination writes for one open document. Call from IO. */
class DocumentSaveSession(
    val project: ProjectEntity,
    val path: String,
    private val db: AppDatabase,
    private val storage: StorageManager,
    private val recovery: DraftRecoveryStore,
    baseText: String,
    val fileId: Long? = null
) {
    private val mutex = Mutex()
    private var savedText = baseText
    private var committedRevision = -1L
    @Volatile private var latestRevision = 0L
    @Volatile var protectedRevision = -1L
        private set

    fun accept(revision: Long) {
        latestRevision = revision
    }

    suspend fun protect(snapshot: DocumentSnapshot) = mutex.withLock {
        if (snapshot.revision == latestRevision && snapshot.revision > committedRevision) protectLocked(snapshot)
    }

    suspend fun save(snapshot: DocumentSnapshot) = mutex.withLock {
        if (snapshot.revision != latestRevision || snapshot.revision <= committedRevision) return@withLock
        protectLocked(snapshot)
        ProjectMutationCoordinator.forDatabase(db).withMutation(project.id) {
            if (db.projectDao().getProjectById(project.id)?.path != project.path) {
                throw IOException("Workspace unavailable")
            }
            val file = db.fileDao().getFileByPath(project.id, path)
            if (fileId != null && file?.id != fileId) throw DocumentConflictException()
            val diskText = storage.readFile(project, path)
            if (diskText != savedText && diskText != snapshot.draft.text) throw DocumentConflictException()
            if (diskText != snapshot.draft.text) storage.writeFile(project, path, snapshot.draft.text)
            savedText = snapshot.draft.text
            file?.let {
                val words = TextAnalysisUtils.countWords(savedText)
                db.fileDao().updateFile(file.copy(
                    lastModified = System.currentTimeMillis(),
                    syncState = "PENDING",
                    wordCount = words,
                    characterCount = savedText.length,
                    readabilityGrade = TextAnalysisUtils.calculateAriGrade(
                        wordCount = words,
                        charCount = savedText.count { !it.isWhitespace() },
                        sentenceCount = maxOf(1, TextAnalysisUtils.countSentences(savedText))
                    )
                ))
            }
        }
        recovery.remove(project, path)
        committedRevision = snapshot.revision
    }

    private fun protectLocked(snapshot: DocumentSnapshot) {
        recovery.write(project, path, snapshot.draft.copy(baseText = savedText, fileId = fileId))
        protectedRevision = snapshot.revision
    }
}

class DocumentConflictException : IOException("The document changed outside this editor")

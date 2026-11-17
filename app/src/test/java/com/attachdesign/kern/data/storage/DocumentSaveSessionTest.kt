package com.attachdesign.kern.data.storage

import com.attachdesign.kern.data.local.AppDatabase
import com.attachdesign.kern.data.local.FileEntity
import com.attachdesign.kern.data.local.FileDao
import com.attachdesign.kern.data.local.ProjectEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DocumentSaveSessionTest {
    @get:Rule val temporary = TemporaryFolder()
    private val project = ProjectEntity(17, "Notes", "notes", false, true)
    private val file = FileEntity(1, project.id, "note.md", "note.md", false, 10, "SYNCED")
    private val db = mockk<AppDatabase>(relaxed = true)
    private val fileDao = mockk<FileDao>(relaxed = true)
    private val storage = mockk<StorageManager>()
    private lateinit var recovery: DraftRecoveryStore
    private lateinit var session: DocumentSaveSession
    private var diskText = "Original"

    @Before
    fun setup() {
        recovery = DraftRecoveryStore(temporary.newFolder("recovery"))
        every { db.fileDao() } returns fileDao
        every { db.projectDao().getProjectById(project.id) } returns project
        every { fileDao.getFileByPath(project.id, file.relativePath) } returns file
        coEvery { storage.readFile(project, file.relativePath) } answers { diskText }
        coEvery { storage.writeFile(project, file.relativePath, any()) } answers { diskText = thirdArg() }
        session = newSession()
    }

    private fun newSession() = DocumentSaveSession(project, file.relativePath, db, storage, recovery, "Original", file.id)
    private fun snapshot(revision: Long, text: String = "Thought **one**\r\n日本語 😀\t ") =
        DocumentSnapshot(revision, RecoveryDraft(text, "Original", 2, 8))

    @Test
    fun failedDestinationRetainsExactRecoveryAcrossStoreRecreationAndDoesNotUpdateMetadata() = runTest {
        val draft = snapshot(1)
        session.accept(1)
        coEvery { storage.writeFile(project, file.relativePath, any()) } throws IOException("Full")
        try {
            session.save(draft)
            fail("Save must report destination failure")
        } catch (_: IOException) { }
        val restored = DraftRecoveryStore(temporary.root.resolve("recovery")).read(project, file.relativePath)
        assertEquals(draft.draft.copy(fileId = file.id), restored)
        assertEquals(1L, session.protectedRevision)
        assertEquals("Original", diskText)
        verify(exactly = 0) { fileDao.updateFile(any()) }
    }

    @Test
    fun retryCommitsTheDraftAndClearsRecoveryOnlyAfterSuccess() = runTest {
        val draft = snapshot(1)
        session.accept(1)
        session.protect(draft)
        assertNotNull(recovery.read(project, file.relativePath))
        session.save(draft)
        assertEquals(draft.draft.text, diskText)
        assertNull(recovery.read(project, file.relativePath))
        verify { fileDao.updateFile(match { it.syncState == "PENDING" && it.characterCount == diskText.length }) }
    }

    @Test
    fun metadataFailureKeepsRecoveryUntilTheDestinationAndIndexAreAcknowledged() = runTest {
        session.accept(1)
        every { fileDao.updateFile(any()) } throws IOException("Index unavailable")
        try {
            session.save(snapshot(1))
            fail("Metadata failure must not acknowledge a complete save")
        } catch (_: IOException) { }
        assertEquals(snapshot(1).draft.text, diskText)
        assertNotNull(recovery.read(project, file.relativePath))
        every { fileDao.updateFile(any()) } returns Unit
        session.save(snapshot(1))
        assertNull(recovery.read(project, file.relativePath))
    }

    @Test
    fun changedOrMissingDestinationNeverGetsOverwritten() = runTest {
        session.accept(1)
        diskText = "Edited by another app"
        try {
            session.save(snapshot(1))
            fail("External edits must be preserved")
        } catch (_: DocumentConflictException) { }
        coEvery { storage.readFile(project, file.relativePath) } throws IOException("Missing")
        try {
            session.save(snapshot(1))
            fail("Missing files must not be silently recreated")
        } catch (_: IOException) { }
        coVerify(exactly = 0) { storage.writeFile(any(), any(), any()) }
        assertNotNull(recovery.read(project, file.relativePath))
    }

    @Test
    fun olderQueuedRecoveryAndSaveCannotReplaceANewerDraft() = runTest {
        session.accept(2)
        session.protect(snapshot(2, "Newest"))
        session.protect(snapshot(1, "Old"))
        session.save(snapshot(1, "Old"))
        assertEquals("Newest", recovery.read(project, file.relativePath)?.text)
        coVerify(exactly = 0) { storage.writeFile(any(), any(), any()) }
    }

    @Test
    fun editDuringSaveUsesTheLastSuccessfulDestinationAsItsRecoveryBase() = runTest {
        val writing = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        session.accept(1)
        coEvery { storage.writeFile(project, file.relativePath, any()) } coAnswers {
            writing.complete(Unit)
            finish.await()
            diskText = thirdArg()
        }
        val first = async { session.save(snapshot(1, "First")) }
        writing.await()
        session.accept(2)
        val second = async { session.protect(snapshot(2, "Second")) }
        finish.complete(Unit)
        first.await()
        second.await()
        assertEquals(RecoveryDraft("Second", "First", 2, 8, file.id), recovery.read(project, file.relativePath))
        session.save(snapshot(2, "Second"))
        assertEquals("Second", diskText)
    }

    @Test
    fun replacementAtTheSamePathCannotInheritTheOldDraft() = runTest {
        every { fileDao.getFileByPath(project.id, file.relativePath) } returns file.copy(id = 2)
        session.accept(1)
        try {
            session.save(snapshot(1))
            fail("A replacement is a different document even when its contents match")
        } catch (_: DocumentConflictException) { }
        coVerify(exactly = 0) { storage.writeFile(any(), any(), any()) }
        assertEquals(file.id, recovery.read(project, file.relativePath)?.fileId)
    }

    @Test
    fun lateProtectionCannotRecreateRecoveryForAnAlreadyCommittedRevision() = runTest {
        val draft = snapshot(1)
        session.accept(1)
        session.save(draft)
        session.protect(draft)
        session.save(draft)
        assertNull(recovery.read(project, file.relativePath))
        coVerify(exactly = 1) { storage.writeFile(project, file.relativePath, draft.draft.text) }
    }

    @Test
    fun recoveryFailurePreventsTruncatingTheDestination() = runTest {
        val blockedDirectory = temporary.newFile("not-a-directory")
        session = DocumentSaveSession(project, file.relativePath, db, storage,
            DraftRecoveryStore(blockedDirectory), "Original")
        session.accept(1)
        try {
            session.save(snapshot(1))
            fail("Do not write externally without recovery protection")
        } catch (_: IOException) { }
        assertEquals(-1L, session.protectedRevision)
        coVerify(exactly = 0) { storage.writeFile(any(), any(), any()) }
    }

    @Test
    fun recoveryIdentityIsIsolatedByWorkspaceAndPath() {
        recovery.write(project, file.relativePath, snapshot(1).draft)
        assertNull(recovery.read(project, "other.md"))
        assertNull(recovery.read(project.copy(path = "new-folder"), file.relativePath))
        assertEquals(snapshot(1).draft, recovery.read(project, file.relativePath))
    }
}

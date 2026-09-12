package com.attachdesign.kern.data.sync

import com.attachdesign.kern.data.local.FileDao
import com.attachdesign.kern.data.local.FileEntity
import com.attachdesign.kern.data.local.ProjectEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.Dispatchers

import com.attachdesign.kern.test.MainDispatcherRule
import org.junit.Rule

@OptIn(ExperimentalCoroutinesApi::class)
class SyncEngineTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    class FakeErrorFileDao : FileDao {
        override fun getFilesForProjectFlow(projectId: Long): Flow<List<FileEntity>> = flowOf()
        override fun getFilesForProject(projectId: Long): List<FileEntity> {
            throw RuntimeException("Database error")
        }
        override fun getFilesForProjects(projectIds: List<Long>): List<FileEntity> = emptyList()
        override fun getFileByPath(projectId: Long, relativePath: String): FileEntity? = null
        override fun insertFile(file: FileEntity): Long = 0
        override fun updateFile(file: FileEntity) {}
        override fun deleteFile(projectId: Long, relativePath: String) {}
        override fun deleteFilesForProject(projectId: Long) {}
        override fun getTotalFileCountFlow(): Flow<Long> = flowOf(0L)
        override fun getTotalWordCountFlow(): Flow<Long> = flowOf(0L)
    }

    class FakePendingFileDao : FileDao {
        val files = mutableListOf(
            FileEntity(
                id = 1L,
                projectId = 1L,
                name = "note.md",
                relativePath = "note.md",
                isDirectory = false,
                lastModified = 0L,
                syncState = "PENDING"
            )
        )

        override fun getFilesForProjectFlow(projectId: Long): Flow<List<FileEntity>> = flowOf(files.toList())
        override fun getFilesForProject(projectId: Long): List<FileEntity> = files.toList()
        override fun getFilesForProjects(projectIds: List<Long>): List<FileEntity> = files.toList()
        override fun getFileByPath(projectId: Long, relativePath: String): FileEntity? =
            files.firstOrNull { it.relativePath == relativePath }
        override fun insertFile(file: FileEntity): Long {
            files.add(file)
            return file.id
        }
        override fun updateFile(file: FileEntity) {
            val index = files.indexOfFirst { it.id == file.id }
            if (index >= 0) files[index] = file
        }
        override fun deleteFile(projectId: Long, relativePath: String) {
            files.removeAll { it.relativePath == relativePath }
        }
        override fun deleteFilesForProject(projectId: Long) {
            files.removeAll { it.projectId == projectId }
        }
        override fun getTotalFileCountFlow(): Flow<Long> = flowOf(files.size.toLong())
        override fun getTotalWordCountFlow(): Flow<Long> = flowOf(0L)
    }

    private fun testProject() = ProjectEntity(
        id = 1L,
        name = "Test Project",
        path = "/test/path",
        isExternal = false,
        isSelected = true
    )

    @Test
    fun `triggerSync updates state to ERROR when fileDao throws exception`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)
        val fakeDao = FakeErrorFileDao()
        val syncEngine = SyncEngine(fakeDao, scope, testDispatcher)

        // Set provider so it doesn't skip
        syncEngine.setProvider(SyncProvider.GOOGLE_DRIVE)

        val project = testProject()

        syncEngine.triggerSync(project)

        // M22: bound the wait so a regression here fails fast instead of hanging.
        val finalStatus = withTimeout(5_000) {
            syncEngine.syncStatus.first { it.state == SyncState.ERROR }
        }
        assertEquals(SyncState.ERROR, finalStatus.state)
        assertEquals("Sync failed: Database error", finalStatus.message)
    }

    @Test
    fun `triggerSync with no provider leaves files PENDING and reports not configured`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)
        val fakeDao = FakePendingFileDao()
        val syncEngine = SyncEngine(fakeDao, scope, testDispatcher)

        // Provider defaults to NONE.
        syncEngine.triggerSync(testProject())

        val stored = fakeDao.files.single()
        assertEquals("PENDING", stored.syncState)

        val status = withTimeout(5_000) {
            syncEngine.syncStatus.first { it.message.contains("not configured", ignoreCase = true) }
        }
        assertTrue(status.message.contains("not configured", ignoreCase = true))
    }

    @Test
    fun `triggerSync with provider but no implementation leaves files PENDING`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)
        val fakeDao = FakePendingFileDao()
        val syncEngine = SyncEngine(fakeDao, scope, testDispatcher)

        syncEngine.setProvider(SyncProvider.GOOGLE_DRIVE)
        syncEngine.triggerSync(testProject())

        // M13: no fake SYNCED marking without a real provider round-trip.
        val finalStatus = withTimeout(5_000) {
            syncEngine.syncStatus.first { it.state == SyncState.IDLE && it.message.contains("not configured", ignoreCase = true) }
        }
        assertEquals("PENDING", fakeDao.files.single().syncState)
        assertTrue(finalStatus.message.contains("not configured", ignoreCase = true))
    }
}

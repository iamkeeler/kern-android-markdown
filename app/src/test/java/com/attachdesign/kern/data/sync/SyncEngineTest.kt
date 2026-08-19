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
import org.junit.Assert.assertEquals
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

    @Test
    fun `triggerSync updates state to ERROR when fileDao throws exception`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val scope = TestScope(testDispatcher)
        val fakeDao = FakeErrorFileDao()
        val syncEngine = SyncEngine(fakeDao, scope)

        // Set provider so it doesn't skip
        syncEngine.setProvider(SyncProvider.GOOGLE_DRIVE)

        val project = ProjectEntity(
            id = 1L,
            name = "Test Project",
            path = "/test/path",
            isExternal = false,
            isSelected = true
        )

        syncEngine.triggerSync(project)

        // Wait for the state to become ERROR
        val finalStatus = syncEngine.syncStatus.first { it.state == SyncState.ERROR }
        assertEquals(SyncState.ERROR, finalStatus.state)
        assertEquals("Sync failed: Database error", finalStatus.message)
    }
}

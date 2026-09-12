package com.attachdesign.kern.ui.main

import com.attachdesign.kern.data.local.AppDatabase
import com.attachdesign.kern.data.local.FileDao
import com.attachdesign.kern.data.local.FileEntity
import com.attachdesign.kern.data.local.ProjectDao
import com.attachdesign.kern.data.local.ProjectEntity
import com.attachdesign.kern.data.storage.FileOperationsManager
import com.attachdesign.kern.data.storage.StorageManager
import com.attachdesign.kern.data.storage.VfsNode
import com.attachdesign.kern.test.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.verify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Guards the batched file query in [MainScreenViewModel.refreshAllFiles]: all
 * projects' files must be loaded with a single `getFilesForProjects` query,
 * not one `getFilesForProject` query per project (N+1).
 *
 * Wall-clock timing is intentionally not asserted — benchmarks are
 * noise-dominated on a cold JVM and cannot fail on an N+1 regression. A
 * counting fake plus exact invocation assertions catches the regression
 * deterministically.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainScreenViewModelPerfTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(testDispatcher)

    @Test
    fun `refreshAllFiles issues a single batched query for all projects`() = runTest {
        val numProjects = 5
        val projects = (1..numProjects).map { id ->
            ProjectEntity(id.toLong(), "P$id", "path$id", false, false)
        }
        val dbFiles = projects.map { proj ->
            FileEntity(proj.id * 100 + 1, proj.id, "note.md", "note.md", false, 0L, "SYNCED")
        }

        val db = mockk<AppDatabase>()
        val projectDao = mockk<ProjectDao>(relaxed = true)
        val fileDao = mockk<FileDao>(relaxed = true)
        val storageManager = mockk<StorageManager>(relaxed = true)
        val fileOpsManager = mockk<FileOperationsManager>(relaxed = true)

        every { db.projectDao() } returns projectDao
        every { db.fileDao() } returns fileDao
        every { db.quoteDao() } returns mockk(relaxed = true)

        every { projectDao.getAllProjects() } returns projects
        every { projectDao.getAllProjectsFlow() } returns flowOf(projects)
        every { projectDao.getSelectedProjectFlow() } returns flowOf(null)
        every { projectDao.getSelectedProject() } returns null

        val batchedQueryCount = AtomicInteger(0)
        every { fileDao.getFilesForProjects(any()) } answers {
            batchedQueryCount.incrementAndGet()
            dbFiles
        }
        coEvery { storageManager.listDirectory(any(), any()) } answers {
            listOf(VfsNode.File("note.md", "note.md", 0L, 0L, null))
        }

        val viewModel = MainScreenViewModel(db, storageManager, fileOpsManager, testDispatcher)
        val job = backgroundScope.launch { viewModel.explorerState.collect {} }

        // Act: one explicit refresh on top of whatever init already issued.
        val queriesBefore = batchedQueryCount.get()
        viewModel.refreshAllFiles()
        advanceUntilIdle()

        // Assert: exactly one batched query per refresh…
        assertEquals(
            "refreshAllFiles must load all projects' files with a single batched query",
            queriesBefore + 1,
            batchedQueryCount.get()
        )
        // …and never the per-project query (an N+1 regression would call it once per project).
        verify(exactly = 0) { fileDao.getFilesForProject(any()) }

        // The batched rows must actually be merged into the combined list,
        // with DB metadata (sync state) enriched onto the disk nodes.
        val items = viewModel.explorerState.value.allItems
        assertEquals(numProjects, items.filterIsInstance<FileListItem.ProjectHeader>().size)
        val rows = items.filterIsInstance<FileListItem.FileRow>()
        assertEquals(numProjects, rows.size)
        assertTrue(rows.all { it.node is VfsNode.File && it.node.syncState == "SYNCED" })

        job.cancel()
    }

    @Test
    fun `batched query receives every project id`() = runTest {
        val projects = listOf(
            ProjectEntity(1L, "P1", "path1", false, false),
            ProjectEntity(2L, "P2", "path2", false, false)
        )

        val db = mockk<AppDatabase>()
        val projectDao = mockk<ProjectDao>(relaxed = true)
        val fileDao = mockk<FileDao>(relaxed = true)
        val storageManager = mockk<StorageManager>(relaxed = true)
        val fileOpsManager = mockk<FileOperationsManager>(relaxed = true)

        every { db.projectDao() } returns projectDao
        every { db.fileDao() } returns fileDao
        every { db.quoteDao() } returns mockk(relaxed = true)

        every { projectDao.getAllProjects() } returns projects
        every { projectDao.getAllProjectsFlow() } returns emptyFlow()
        every { projectDao.getSelectedProjectFlow() } returns emptyFlow()
        every { projectDao.getSelectedProject() } returns null
        every { fileDao.getFilesForProjects(any()) } returns emptyList()

        val viewModel = MainScreenViewModel(db, storageManager, fileOpsManager, testDispatcher)
        viewModel.refreshAllFiles()
        advanceUntilIdle()

        // The single batched query must cover every project — otherwise some
        // projects' files would silently go missing from the combined list.
        verify(atLeast = 1) { fileDao.getFilesForProjects(listOf(1L, 2L)) }
    }
}

package com.attachdesign.kern.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class ProjectDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var projectDao: ProjectDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        projectDao = database.projectDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun insertAndGetAllProjects() = runTest {
        val project1 = ProjectEntity(name = "Project 1", path = "/path/1", isExternal = false, isSelected = true)
        val project2 = ProjectEntity(name = "Project 2", path = "/path/2", isExternal = true, isSelected = false)

        projectDao.insertProject(project1)
        projectDao.insertProject(project2)

        val projects = projectDao.getAllProjects()
        assertEquals(2, projects.size)

        assertTrue(projects.any { it.name == "Project 1" })
        assertTrue(projects.any { it.name == "Project 2" })
    }

    @Test
    fun getSelectedProject() = runTest {
        val project1 = ProjectEntity(name = "Project 1", path = "/path/1", isExternal = false, isSelected = false)
        val project2 = ProjectEntity(name = "Project 2", path = "/path/2", isExternal = true, isSelected = true)

        projectDao.insertProject(project1)
        projectDao.insertProject(project2)

        val selectedProject = projectDao.getSelectedProject()
        assertNotNull(selectedProject)
        assertEquals("Project 2", selectedProject?.name)
    }

    @Test
    fun getProjectById() = runTest {
        val project = ProjectEntity(name = "Test Project", path = "/path", isExternal = false, isSelected = false)
        val id = projectDao.insertProject(project)

        val retrievedProject = projectDao.getProjectById(id)
        assertNotNull(retrievedProject)
        assertEquals(id, retrievedProject?.id)
        assertEquals("Test Project", retrievedProject?.name)
    }

    @Test
    fun updateProject() = runTest {
        val project = ProjectEntity(name = "Old Name", path = "/path", isExternal = false, isSelected = false)
        val id = projectDao.insertProject(project)

        val retrievedProject = projectDao.getProjectById(id)
        assertNotNull(retrievedProject)

        val updatedProject = retrievedProject!!.copy(name = "New Name")
        projectDao.updateProject(updatedProject)

        val updatedRetrievedProject = projectDao.getProjectById(id)
        assertEquals("New Name", updatedRetrievedProject?.name)
    }

    @Test
    fun deselectAllProjects() = runTest {
        val project1 = ProjectEntity(name = "Project 1", path = "/path/1", isExternal = false, isSelected = true)
        val project2 = ProjectEntity(name = "Project 2", path = "/path/2", isExternal = true, isSelected = true)

        projectDao.insertProject(project1)
        projectDao.insertProject(project2)

        val initialProjects = projectDao.getAllProjects()
        assertTrue(initialProjects.all { it.isSelected })

        projectDao.deselectAllProjects()

        val updatedProjects = projectDao.getAllProjects()
        assertTrue(updatedProjects.all { !it.isSelected })
    }

    @Test
    fun deleteProjectById() = runTest {
        val project = ProjectEntity(name = "To Delete", path = "/path", isExternal = false, isSelected = false)
        val id = projectDao.insertProject(project)

        var retrievedProject = projectDao.getProjectById(id)
        assertNotNull(retrievedProject)

        projectDao.deleteProjectById(id)

        retrievedProject = projectDao.getProjectById(id)
        assertNull(retrievedProject)
    }

    @Test
    fun getAllProjectsFlow() = runTest {
        val project1 = ProjectEntity(name = "Project 1", path = "/path/1", isExternal = false, isSelected = true)
        val id = projectDao.insertProject(project1)

        val projects = projectDao.getAllProjectsFlow().first()
        assertEquals(1, projects.size)
        assertEquals(id, projects[0].id)
    }

    @Test
    fun getSelectedProjectFlow() = runTest {
        val project1 = ProjectEntity(name = "Project 1", path = "/path/1", isExternal = false, isSelected = true)
        val id = projectDao.insertProject(project1)

        val selectedProject = projectDao.getSelectedProjectFlow().first()
        assertNotNull(selectedProject)
        assertEquals(id, selectedProject?.id)
    }
}

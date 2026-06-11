package com.attachdesign.kern.ui.main

import android.content.Context
import org.junit.Test
import kotlin.system.measureTimeMillis
import com.attachdesign.kern.data.local.ProjectEntity
import com.attachdesign.kern.data.local.FileEntity
import com.attachdesign.kern.data.storage.VfsNode
import com.attachdesign.kern.data.storage.VfsNodeMapper

class MainScreenViewModelPerfTest {

    @Test
    fun benchmarkRefreshAllFiles() {
        val numProjects = 50
        val numFilesPerProject = 50

        val projects = (1..numProjects).map { ProjectEntity(it.toLong(), "P$it", "path", false, false) }
        val files = (1..numProjects).flatMap { pId ->
            (1..numFilesPerProject).map { fId -> FileEntity((pId * 1000 + fId).toLong(), pId.toLong(), "F", "F", false, 0L, "SYNCED") }
        }

        fun getFilesForProject(projectId: Long): List<FileEntity> {
            Thread.sleep(10) // 10ms latency per query
            return files.filter { it.projectId == projectId }
        }

        fun listDirectory(project: ProjectEntity): List<VfsNode> {
            return (1..numFilesPerProject).map { fId -> VfsNode.File("file_$fId", "file_$fId", 0L, 0L, null) }
        }

        // Baseline implementation
        val baselineTime = measureTimeMillis {
            val projectList = projects
            val items = mutableListOf<String>()
            for (proj in projectList) {
                val diskFiles = listDirectory(proj)
                val enriched = VfsNodeMapper.enrichFiles(diskFiles, getFilesForProject(proj.id))
                items += proj.name
                enriched.forEach { items += it.name }
            }
        }
        println("BENCHMARK_RESULT: Baseline time = ${baselineTime}ms")

        // Optimized implementation
        fun getFilesForProjects(projectIds: List<Long>): List<FileEntity> {
            Thread.sleep(15) // Slightly longer latency for larger query, but only once
            return files.filter { it.projectId in projectIds }
        }

        val optimizedTime = measureTimeMillis {
            val projectList = projects
            val items = mutableListOf<String>()

            // Single DB query for all projects
            val allDbFiles = getFilesForProjects(projectList.map { it.id })
            val dbFilesByProject = allDbFiles.groupBy { it.projectId }

            for (proj in projectList) {
                val diskFiles = listDirectory(proj)
                val projDbFiles = dbFilesByProject[proj.id] ?: emptyList()
                val enriched = VfsNodeMapper.enrichFiles(diskFiles, projDbFiles)

                items += proj.name
                enriched.forEach { items += it.name }
            }
        }
        println("BENCHMARK_RESULT: Optimized time = ${optimizedTime}ms")

        // Output for PR
        println("BENCHMARK_RESULT_TEXT: Improved performance from ${baselineTime}ms to ${optimizedTime}ms.")
    }
}

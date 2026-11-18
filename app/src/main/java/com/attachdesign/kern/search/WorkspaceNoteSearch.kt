package com.attachdesign.kern.search

import com.attachdesign.kern.data.local.ProjectEntity
import com.attachdesign.kern.data.storage.DirectoryOutcome
import com.attachdesign.kern.data.storage.TRASH_DIRECTORY
import com.attachdesign.kern.data.storage.VfsNode
import java.io.IOException
import java.io.Reader
import kotlin.coroutines.coroutineContext
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

internal data class WorkspaceSearchHit(
    val project: ProjectEntity,
    val node: VfsNode,
    val excerpt: String?
)

internal data class WorkspaceSearchState(
    val hits: PersistentList<WorkspaceSearchHit> = persistentListOf(),
    val searching: Boolean = true,
    val incomplete: Boolean = false
)

internal class WorkspaceNoteSearch(
    private val list: suspend (ProjectEntity, String) -> DirectoryOutcome,
    private val open: (ProjectEntity, String) -> Reader
) {
    fun search(projects: List<ProjectEntity>, query: String): Flow<WorkspaceSearchState> = flow {
        var state = WorkspaceSearchState()
        for (project in projects.distinctBy { it.id }.sortedBy { it.isExternal }) {
            val folders = ArrayDeque<Pair<String, Int>>().apply { add("" to 0) }
            while (folders.isNotEmpty()) {
                coroutineContext.ensureActive()
                val (folder, depth) = folders.removeFirst()
                val listing = list(project, folder)
                if (listing !is DirectoryOutcome.Success) {
                    state = state.copy(incomplete = true)
                    continue
                }
                for (node in listing.nodes.distinctBy { it.relativePath }) {
                    coroutineContext.ensureActive()
                    if (folder.isEmpty() && node.name == TRASH_DIRECTORY) continue
                    val path = if (folder.isEmpty()) node.name else "$folder/${node.name}"
                    if (node.relativePath != path || '/' in node.name ||
                        node.name.isEmpty() || node.name == "." || node.name == "..") {
                        state = state.copy(incomplete = true)
                        continue
                    }
                    if (node.isDirectory) {
                        if (depth < MAX_FOLDER_DEPTH) folders.addLast(path to depth + 1)
                        else state = state.copy(incomplete = true)
                    }
                    val nameMatches = node.name.contains(query, ignoreCase = true)
                    val extension = node.name.substringAfterLast('.', "").lowercase()
                    val excerpt = if (!node.isDirectory && extension in noteExtensions) {
                        try {
                            open(project, path).use { NoteTextSearch.firstExcerpt(it, query) }
                        } catch (_: IOException) {
                            state = state.copy(incomplete = true)
                            null
                        } catch (_: SecurityException) {
                            state = state.copy(incomplete = true)
                            null
                        }
                    } else null
                    if (nameMatches || excerpt != null) {
                        state = state.copy(hits = state.hits.add(WorkspaceSearchHit(project, node, excerpt)))
                        if (state.hits.size == 1 || state.hits.size % 16 == 0) emit(state)
                    }
                }
            }
            emit(state)
        }
        emit(state.copy(searching = false))
    }

    private companion object {
        // Bound traversal if a linked provider exposes a cycle.
        const val MAX_FOLDER_DEPTH = 64
        val noteExtensions = setOf("md", "markdown", "txt")
    }
}

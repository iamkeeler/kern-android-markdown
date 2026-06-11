package com.attachdesign.kern.data.storage

import com.attachdesign.kern.data.local.FileEntity

object VfsNodeMapper {
    /**
     * Enriches disk-based VfsNode.File objects with their corresponding database syncState.
     */
    fun enrichFiles(diskFiles: List<VfsNode>, dbFiles: List<FileEntity>): List<VfsNode> {
        val dbFilesMap = dbFiles.associateBy { it.relativePath }
        return diskFiles.map { node ->
            if (node is VfsNode.File) {
                val dbFile = dbFilesMap[node.relativePath]
                node.copy(syncState = dbFile?.syncState)
            } else {
                node
            }
        }
    }
}

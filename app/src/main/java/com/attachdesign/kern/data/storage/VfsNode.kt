package com.attachdesign.kern.data.storage

sealed interface VfsNode {
    val name: String
    val relativePath: String
    val isDirectory: Boolean

    data class File(
        override val name: String,
        override val relativePath: String,
        val size: Long,
        val lastModified: Long,
        val syncState: String? = null
    ) : VfsNode {
        override val isDirectory: Boolean get() = false
    }

    data class Directory(
        override val name: String,
        override val relativePath: String
    ) : VfsNode {
        override val isDirectory: Boolean get() = true
    }
}

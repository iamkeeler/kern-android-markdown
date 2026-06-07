package com.example.modernandroidmarkdowneditor

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey

@Serializable data class EditorKey(val projectId: Long, val filePath: String) : NavKey


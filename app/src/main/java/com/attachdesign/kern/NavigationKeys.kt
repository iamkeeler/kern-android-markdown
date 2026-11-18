package com.attachdesign.kern

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey

@Serializable
data class EditorKey(
    val projectId: Long,
    val filePath: String,
    val focusOnStart: Boolean = false,
    val touchOriginX: Float? = null,
    val touchOriginY: Float? = null,
    val sessionId: String = java.util.UUID.randomUUID().toString(),
    val findQuery: String = ""
) : NavKey

@Serializable data object SettingsKey : NavKey



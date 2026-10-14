package com.attachdesign.kern.ui.main.dialogs

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.documentfile.provider.DocumentFile
import com.attachdesign.kern.ui.theme.AppColorTheme

@Composable
fun CreateProjectDialog(
    theme: AppColorTheme,
    selectedUri: String,
    onSelectFolder: () -> Unit,
    onDismiss: () -> Unit,
    onCreate: (name: String, uri: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    val context = LocalContext.current
    LaunchedEffect(selectedUri) {
        if (name.isBlank() && selectedUri.isNotEmpty()) {
            val doc = DocumentFile.fromTreeUri(context, Uri.parse(selectedUri))
            doc?.name?.let { name = it }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Project Workspace", color = theme.textPrimary, fontSize = theme.typography.subtitle, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(theme.dimensions.spacingLarge)) {
                OutlinedTextField(
                    value         = name,
                    onValueChange = { name = it },
                    label         = { Text("Project Name", color = theme.textMuted) },
                    textStyle     = TextStyle(color = theme.textPrimary),
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedTextColor     = theme.textPrimary,
                        unfocusedTextColor   = theme.textPrimary,
                        focusedContainerColor   = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedBorderColor   = theme.accent,
                        unfocusedBorderColor = theme.textMuted.copy(alpha = 0.5f),
                        focusedLabelColor    = theme.accent,
                        unfocusedLabelColor  = theme.textMuted,
                        cursorColor          = theme.accent
                    )
                )
                Column(verticalArrangement = Arrangement.spacedBy(theme.dimensions.spacingSmall)) {
                    OutlinedButton(onClick = onSelectFolder) {
                        Text(if (selectedUri.isEmpty()) "Link local directory" else "Change linked folder")
                    }
                    if (selectedUri.isNotEmpty()) {
                        Text(
                            text = "Linked folder: ${Uri.parse(selectedUri).lastPathSegment?.substringAfterLast(":") ?: ""}",
                            color = theme.accent,
                            fontSize = theme.typography.body,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (selectedUri.isNotEmpty()) {
                        Text(
                            text = "Files in this directory will be loaded.",
                            color = theme.textMuted,
                            fontSize = theme.typography.tiny
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onCreate(name, selectedUri) }, enabled = name.isNotBlank()) {
                Text("Create", color = theme.accent)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = theme.textMuted) } },
        containerColor = theme.surface
    )
}

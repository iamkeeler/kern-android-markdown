package com.attachdesign.kern.ui.main.dialogs

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.attachdesign.kern.ui.theme.AppColorTheme

@Composable
fun InputDialog(
    title: String,
    label: String,
    confirmText: String,
    theme: AppColorTheme,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = theme.textPrimary, fontSize = theme.typography.subtitle, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value         = value,
                onValueChange = { value = it },
                label         = { Text(label, color = theme.textMuted) },
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
        },
        confirmButton = {
            TextButton(
                onClick = { if (value.isNotBlank()) onConfirm(value) },
                enabled = value.isNotBlank()
            ) {
                Text(
                    text = confirmText,
                    color = if (value.isNotBlank()) theme.accent else theme.textMuted.copy(alpha = 0.5f)
                )
            }
        },
        dismissButton = { 
            TextButton(onClick = onDismiss) { 
                Text("Cancel", color = theme.textMuted) 
            } 
        },
        containerColor = theme.surface
    )
}

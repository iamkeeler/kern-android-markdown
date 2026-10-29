package com.attachdesign.kern.ui.main.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import com.attachdesign.kern.ui.theme.AppColorTheme

/** Compatibility host for callers migrating to retained operation state. */
@Composable
fun InputDialog(
    title: String, label: String, confirmText: String, theme: AppColorTheme,
    onDismiss: () -> Unit, onConfirm: (String) -> Unit
) {
    var value by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    InputDialog(title, label, confirmText, theme, value, { value = it }, false, null,
        onDismiss, { onConfirm(value.text) })
}

/** Controlled input: the caller saves the draft and owns the retained mutation receipt. */
@Composable
fun InputDialog(
    title: String,
    label: String,
    confirmText: String,
    theme: AppColorTheme,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    isSubmitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    canSubmit: Boolean = true,
    recoveryText: String? = null,
    onRecovery: (() -> Unit)? = null
) {
    val enabled = canSubmit && !isSubmitting && value.text.isNotBlank()
    val submit = { if (enabled) onConfirm() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = theme.textPrimary, fontSize = theme.typography.subtitle, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = !isSubmitting && canSubmit,
                    singleLine = true,
                    isError = error != null,
                    label = { Text(label, color = theme.textSecondary) },
                    supportingText = {
                        if (error != null) Text(error, color = theme.textDanger,
                            fontSize = theme.typography.helper, lineHeight = theme.typography.helperLineHeight)
                    },
                    textStyle = TextStyle(color = theme.textPrimary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = theme.textPrimary, unfocusedTextColor = theme.textPrimary,
                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        focusedBorderColor = theme.controlAccent, unfocusedBorderColor = theme.indicator,
                        focusedLabelColor = theme.textAccent, unfocusedLabelColor = theme.textSecondary,
                        errorBorderColor = theme.textDanger, errorLabelColor = theme.textDanger,
                        cursorColor = theme.controlAccent
                    )
                )
                if (recoveryText != null && onRecovery != null) {
                    TextButton(onClick = onRecovery, enabled = !isSubmitting) {
                        Text(recoveryText, color = theme.textAccent)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = submit, enabled = enabled) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = theme.textSecondary) } },
        containerColor = theme.surface
    )
}

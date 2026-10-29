package com.attachdesign.kern.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.attachdesign.kern.data.local.AppDatabase
import com.attachdesign.kern.data.local.SettingEntity
import com.attachdesign.kern.data.local.SettingKeys
import com.attachdesign.kern.ui.editor.FoldShortcutAction
import com.attachdesign.kern.ui.editor.rememberFoldEnvironment
import com.attachdesign.kern.ui.editor.rememberTactileFeedbackHelper
import com.attachdesign.kern.ui.theme.AppColorTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Shared by standalone Settings and the editor's embedded Settings. */
@Composable
internal fun FoldShortcutSettings(db: AppDatabase, theme: AppColorTheme) {
    val scope = rememberCoroutineScope()
    var open by rememberSaveable { mutableStateOf(false) }
    MinimalOutlinedButton("Fold shortcut", onClick = { open = true }, theme = theme,
        modifier = Modifier.fillMaxWidth())
    if (!open) return

    val enabledSetting by db.settingDao().getSettingFlow(SettingKeys.FOLD_TO_SPLIT).collectAsState(null)
    val actionSetting by db.settingDao().getSettingFlow(SettingKeys.FOLD_SHORTCUT_ACTION).collectAsState(null)
    val hapticsSetting by db.settingDao().getSettingFlow(SettingKeys.FOLD_HAPTICS).collectAsState(null)
    val soundSetting by db.settingDao().getSettingFlow(SettingKeys.FOLD_SOUND).collectAsState(null)
    val enabled = enabledSetting?.value == "true"
    val action = FoldShortcutAction.fromSetting(actionSetting?.value)
    val haptics = hapticsSetting?.value != "false"
    val sound = soundSetting?.value == "true"
    val feedback = rememberTactileFeedbackHelper(sound)
    val environment = rememberFoldEnvironment()
    val owner = LocalLifecycleOwner.current
    var angle by remember(environment) { mutableStateOf<Float?>(null) }
    // Explicitly opening this dialog provides a local sensor check; no gesture commits here.
    LaunchedEffect(environment, owner) {
        if (environment.sensorAvailable) owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            environment.angles.collect { angle = it.takeIf { value -> value.isFinite() && value in 0f..180f } }
        }
    }
    fun save(key: String, value: String) {
        scope.launch(Dispatchers.IO) { db.settingDao().insertSetting(SettingEntity(key, value)) }
    }

    AlertDialog(
        onDismissRequest = { open = false },
        title = { Text("Fold shortcut") },
        text = {
            Column(Modifier.heightIn(max = theme.dimensions.popupMaxHeight).verticalScroll(rememberScrollState())) {
                Text("With the inner screen open flat, bend it slightly, then open flat again to show or hide your chosen pane. Hold a bend to keep using book posture.")
                FoldToggle("Fold shortcut", enabled, theme, environment.sensorAvailable) {
                    save(SettingKeys.FOLD_TO_SPLIT, it.toString())
                }
                Text(if (!environment.sensorAvailable)
                    "Hinge-angle sensing isn't available. Use the document toolbar to open metrics or split view."
                else angle?.let { "Hinge angle: ${it.roundToInt()}°. Close Settings before using the shortcut." }
                    ?: "Waiting for hinge readings. Gently bend the screen to check the sensor.",
                    fontSize = theme.typography.helper, lineHeight = theme.typography.helperLineHeight,
                    color = theme.textSecondary)
                Spacer(Modifier.height(theme.dimensions.spacingLarge))
                Text("OPEN WITH A FOLD", color = theme.textSecondary, fontSize = theme.typography.tiny)
                Column(Modifier.selectableGroup()) {
                    FoldShortcutAction.entries.forEach { option ->
                        SettingsRadioRow(option.label, action == option, theme) {
                            save(SettingKeys.FOLD_SHORTCUT_ACTION, option.name)
                        }
                    }
                }
                Text("Reader metrics shows Hemingway analysis at any window size. A second editor needs a wide window with room for two readable panes.",
                    fontSize = theme.typography.helper, lineHeight = theme.typography.helperLineHeight,
                    color = theme.textSecondary)
                Spacer(Modifier.height(theme.dimensions.spacingLarge))
                FoldToggle("Fold haptics", haptics, theme) { save(SettingKeys.FOLD_HAPTICS, it.toString()) }
                FoldToggle("Paper crease sound", sound, theme) { save(SettingKeys.FOLD_SOUND, it.toString()) }
                Text("Sound follows your device's touch-sound, volume, silent-mode and Do Not Disturb settings.",
                    fontSize = theme.typography.helper, lineHeight = theme.typography.helperLineHeight,
                    color = theme.textSecondary)
                TextButton(onClick = { feedback.triggerFoldFeedback(haptics, sound) }, enabled = haptics || sound) {
                    Text("Try feedback", color = theme.textAccent)
                }
            }
        },
        confirmButton = { TextButton(onClick = { open = false }) { Text("Done", color = theme.textAccent) } },
        containerColor = theme.surface, titleContentColor = theme.textPrimary, textContentColor = theme.textPrimary
    )
}

@Composable
private fun FoldToggle(label: String, checked: Boolean, theme: AppColorTheme, enabled: Boolean = true,
    onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = theme.dimensions.buttonHeight)
        .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
        .semantics { contentDescription = label }, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = theme.textPrimary, fontSize = theme.typography.body,
            modifier = Modifier.weight(1f).padding(end = theme.dimensions.spacingLarge))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled,
            colors = SwitchDefaults.colors(checkedThumbColor = theme.onControlAccent,
                checkedTrackColor = theme.controlAccent, uncheckedThumbColor = theme.indicator,
                uncheckedBorderColor = theme.indicator))
    }
}

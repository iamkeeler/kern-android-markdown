package com.attachdesign.kern.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.CancellationException
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import com.attachdesign.kern.data.local.AppDatabase
import com.attachdesign.kern.data.local.SettingEntity
import com.attachdesign.kern.data.local.SettingKeys
import com.attachdesign.kern.data.local.ThemeEntity
import com.attachdesign.kern.ui.common.AdaptiveMetricPair
import com.attachdesign.kern.data.stats.StatsRepository
import com.attachdesign.kern.data.analytics.TelemetryCollection
import com.attachdesign.kern.domain.stats.UserStats
import com.attachdesign.kern.ui.theme.ThemeEngine
import com.attachdesign.kern.ui.theme.toFontFamily
import com.attachdesign.kern.ui.theme.toThemeJson
import com.attachdesign.kern.ui.theme.EditorTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import android.widget.Toast

@Composable
fun SettingsTabsContent(
    db: AppDatabase,
    theme: com.attachdesign.kern.ui.theme.AppColorTheme,
    modifier: Modifier = Modifier,
    importTheme: suspend (String) -> Unit = { persistImportedTheme(db, it) }
) {
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    // Observe global settings reactively
    val viewModeSetting     by db.settingDao().getSettingFlow(SettingKeys.VIEW_MODE).collectAsState(initial = null)
    val editorFontSetting   by db.settingDao().getSettingFlow(SettingKeys.EDITOR_FONT_FAMILY).collectAsState(initial = null)
    val editorFontSizeScaleSetting by db.settingDao().getSettingFlow(SettingKeys.EDITOR_FONT_SIZE_SCALE).collectAsState(initial = null)
    val stickySetting       by db.settingDao().getSettingFlow(SettingKeys.STICKY_SELECTION).collectAsState(initial = null)
    val launchNewFileSetting by db.settingDao().getSettingFlow(SettingKeys.LAUNCH_NEW_FILE).collectAsState(initial = null)
    val sentenceCapitalizationSetting by db.settingDao().getSettingFlow(SettingKeys.SENTENCE_CAPITALIZATION).collectAsState(initial = null)
    val showWorkspaceIntroSetting by db.settingDao().getSettingFlow(SettingKeys.SHOW_WORKSPACE_INTRO).collectAsState(initial = null)
    val telemetryCollectionSetting by db.settingDao().getSettingFlow(TelemetryCollection.SETTING_KEY).collectAsState(initial = null)

    val autoHeaderSetting   by db.settingDao().getSettingFlow(SettingKeys.AUTO_HEADER_SPACING).collectAsState(initial = null)
    val autoCompleteSetting by db.settingDao().getSettingFlow(SettingKeys.AUTO_COMPLETE_ENABLED).collectAsState(initial = null)
    val autoCompleteQuotesSetting       by db.settingDao().getSettingFlow(SettingKeys.AUTO_COMPLETE_QUOTES).collectAsState(initial = null)
    val autoCompleteSingleQuotesSetting by db.settingDao().getSettingFlow(SettingKeys.AUTO_COMPLETE_SINGLE_QUOTES).collectAsState(initial = null)
    val autoCompleteBracesSetting       by db.settingDao().getSettingFlow(SettingKeys.AUTO_COMPLETE_BRACES).collectAsState(initial = null)
    val autoCompleteParensSetting       by db.settingDao().getSettingFlow(SettingKeys.AUTO_COMPLETE_PARENS).collectAsState(initial = null)
    val autoCompleteBracketsSetting     by db.settingDao().getSettingFlow(SettingKeys.AUTO_COMPLETE_BRACKETS).collectAsState(initial = null)

    val currentViewMode            = viewModeSetting?.value ?: "RENDERED"
    val currentFontFamily          = editorFontSetting?.value ?: "serif"
    val currentFontSizeScale = EditorTypography.normalizeScale(editorFontSizeScaleSetting?.value)
    val currentSticky              = stickySetting?.value?.toBoolean() ?: true
    val currentLaunchNewFile       = launchNewFileSetting?.value?.toBoolean() ?: true
    val currentSentenceCapitalization = sentenceCapitalizationSetting?.value?.toBoolean() ?: true
    val currentShowWorkspaceIntro  = showWorkspaceIntroSetting?.value?.toBoolean() ?: true
    val currentTelemetryCollection = telemetryCollectionSetting?.value?.toBoolean() ?: TelemetryCollection.DEFAULT_ENABLED

    val currentAutoHeader          = autoHeaderSetting?.value?.toBoolean() ?: true
    val currentAutoComplete        = autoCompleteSetting?.value?.toBoolean() ?: true
    val currentAutoCompleteQuotes  = autoCompleteQuotesSetting?.value?.toBoolean() ?: true
    val currentAutoCompleteSingleQuotes = autoCompleteSingleQuotesSetting?.value?.toBoolean() ?: true
    val currentAutoCompleteBraces  = autoCompleteBracesSetting?.value?.toBoolean() ?: true
    val currentAutoCompleteParens  = autoCompleteParensSetting?.value?.toBoolean() ?: true
    val currentAutoCompleteBrackets = autoCompleteBracketsSetting?.value?.toBoolean() ?: true

    val appFont = theme.toFontFamily()

    val tabs = listOf(
        "appearance" to "Appearance", "behavior" to "Behavior", "stats" to "Stats",
        "sync" to "Sync", "about" to "About"
    )
    var activeTabId by rememberSaveable { mutableStateOf("appearance") }
    val activeTab = tabs.indexOfFirst { it.first == activeTabId }.coerceAtLeast(0)
    val tabScrollStates = mapOf(
        "appearance" to rememberScrollState(), "behavior" to rememberScrollState(),
        "stats" to rememberScrollState(), "sync" to rememberScrollState(),
        "about" to rememberScrollState()
    )
    var inputThemeJson by rememberSaveable { mutableStateOf("") }
    var isImportPending by remember { mutableStateOf(false) }
    var importError by remember { mutableStateOf<String?>(null) }
    val isJsonValid = remember(inputThemeJson) {
        inputThemeJson.isBlank() || ThemeEngine.deserialize(inputThemeJson) != null
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Tabs Selector
        ScrollableTabRow(
            selectedTabIndex = activeTab,
            containerColor = Color.Transparent,
            contentColor = theme.textAccent,
            edgePadding = 0.dp,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                    color = theme.controlAccent
                )
            },
            divider = {},
            modifier = Modifier.fillMaxWidth()
        ) {
            tabs.forEachIndexed { idx, (tabId, tabName) ->
                val selected = activeTab == idx
                Tab(
                    selected = selected,
                    onClick = { activeTabId = tabId },
                    text = {
                        Text(
                            text = tabName,
                            fontSize = theme.typography.body,
                            fontFamily = appFont,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    selectedContentColor = theme.textAccent,
                    unselectedContentColor = theme.textSecondary
                )
            }
        }

        HorizontalDivider(
            thickness = theme.dimensions.borderWidth,
            color = theme.textMuted.copy(alpha = 0.15f)
        )

        Spacer(Modifier.height(theme.dimensions.spacingExtraLarge))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("settings-tab-content")
                .verticalScroll(tabScrollStates.getValue(tabs[activeTab].first))
        ) {
            when (activeTab) {
                0 -> { // Appearance tab
                    Text(
                        text = "THEMES",
                        color = theme.textSecondary,
                        fontSize = theme.typography.tiny,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(Modifier.height(theme.dimensions.spacingSmall))

                    val presets = listOf(
                        "Cream & Charcoal (Default)" to ThemeEngine.DefaultLight,
                        "Inky Charcoal (Default)"   to ThemeEngine.DefaultDark
                    )
                    Column(Modifier.selectableGroup()) {
                        presets.forEach { (name, presetJson) ->
                            SettingsRadioRow(name, theme.name == presetJson.name, theme) {
                                coroutineScope.launch(Dispatchers.IO) {
                                    val jsonString = ThemeEngine.serialize(presetJson)
                                    val id = db.themeDao().insertTheme(ThemeEntity(name = presetJson.name, jsonString = jsonString))
                                    db.settingDao().insertSetting(SettingEntity(SettingKeys.SELECTED_THEME_ID, id.toString()))
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(theme.dimensions.spacingExtraLarge))
                    HorizontalDivider(thickness = theme.dimensions.borderWidth, color = theme.textMuted.copy(alpha = 0.15f))
                    Spacer(Modifier.height(theme.dimensions.spacingExtraLarge))

                    Text("VIEW MODE", color = theme.textSecondary, fontSize = theme.typography.tiny, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Spacer(Modifier.height(theme.dimensions.spacingSmall))
                    val modes = listOf(
                        "RENDERED"           to "Live Preview",
                        "SYNTAX_HIGHLIGHTED" to "Syntax Highlighted",
                        "RAW_PLAIN_TEXT"     to "Raw Plain-Text"
                    )
                    Column(Modifier.selectableGroup()) {
                        modes.forEach { (mode, label) ->
                            SettingsRadioRow(label, currentViewMode == mode, theme) {
                                coroutineScope.launch(Dispatchers.IO) {
                                    db.settingDao().insertSetting(SettingEntity(SettingKeys.VIEW_MODE, mode))
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(theme.dimensions.spacingExtraLarge))
                    HorizontalDivider(thickness = theme.dimensions.borderWidth, color = theme.textMuted.copy(alpha = 0.15f))
                    Spacer(Modifier.height(theme.dimensions.spacingExtraLarge))

                    Text("EDITOR FONT", color = theme.textSecondary, fontSize = theme.typography.tiny, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Spacer(Modifier.height(theme.dimensions.spacingSmall))
                    val fonts = listOf(
                        "serif"      to "Serif (Book)",
                        "sans-serif" to "Sans-Serif (Modern)",
                        "monospace"  to "Monospace (Code)"
                    )
                    Column(Modifier.selectableGroup()) {
                        fonts.forEach { (font, label) ->
                            SettingsRadioRow(label, currentFontFamily == font, theme) {
                                coroutineScope.launch(Dispatchers.IO) {
                                    db.settingDao().insertSetting(SettingEntity(SettingKeys.EDITOR_FONT_FAMILY, font))
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(theme.dimensions.spacingExtraLarge))
                    HorizontalDivider(thickness = theme.dimensions.borderWidth, color = theme.textMuted.copy(alpha = 0.15f))
                    Spacer(Modifier.height(theme.dimensions.spacingExtraLarge))

                    var sliderScale by remember(currentFontSizeScale) { mutableStateOf(currentFontSizeScale) }
                    
                    val sizeLabel = when {
                        sliderScale <= 0.8f -> "Small"
                        sliderScale <= 1.0f -> "Medium"
                        sliderScale <= 1.2f -> "Large"
                        else -> "Extra Large"
                    }
                    
                    Text(
                        text = "EDITOR FONT SIZE: ${sizeLabel.uppercase()}",
                        color = theme.textSecondary,
                        fontSize = theme.typography.tiny,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(Modifier.height(theme.dimensions.spacingSmall))
                    Slider(
                        value = sliderScale,
                        onValueChange = { sliderScale = it },
                        onValueChangeFinished = {
                            coroutineScope.launch(Dispatchers.IO) {
                                db.settingDao().insertSetting(SettingEntity(SettingKeys.EDITOR_FONT_SIZE_SCALE, sliderScale.toString()))
                            }
                        },
                        valueRange = 0.8f..1.4f,
                        steps = 2,
                        colors = SliderDefaults.colors(
                            thumbColor = theme.controlAccent,
                            activeTrackColor = theme.controlAccent,
                            inactiveTrackColor = theme.indicator
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = theme.dimensions.spacingSmall),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Small", color = theme.textSecondary, fontSize = theme.typography.tiny)
                        Text("Medium", color = theme.textSecondary, fontSize = theme.typography.tiny)
                        Text("Large", color = theme.textSecondary, fontSize = theme.typography.tiny)
                        Text("Extra Large", color = theme.textSecondary, fontSize = theme.typography.tiny)
                    }

                    Spacer(Modifier.height(theme.dimensions.spacingExtraLarge))
                    HorizontalDivider(thickness = theme.dimensions.borderWidth, color = theme.textMuted.copy(alpha = 0.15f))
                    Spacer(Modifier.height(theme.dimensions.spacingExtraLarge))

                    Text(
                        text = "CUSTOM THEME",
                        color = theme.textSecondary,
                        fontSize = theme.typography.tiny,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(Modifier.height(theme.dimensions.spacingMedium))

                    MinimalOutlinedButton(
                        text = "Export Current Theme",
                        onClick = {
                            val currentThemeJson = theme.toThemeJson()
                            val serialized = ThemeEngine.serialize(currentThemeJson)
                            clipboardManager.setText(AnnotatedString(serialized))
                            Toast.makeText(context, "Theme copied", Toast.LENGTH_SHORT).show()
                        },
                        theme = theme,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(theme.dimensions.spacingLarge))

                    OutlinedTextField(
                        value = inputThemeJson,
                        onValueChange = { inputThemeJson = it; importError = null },
                        enabled = !isImportPending,
                        label = { Text("Paste Theme JSON", fontSize = theme.typography.small, color = theme.textSecondary) },
                        isError = !isJsonValid || importError != null,
                        supportingText = {
                            val error = importError ?: if (!isJsonValid) "Invalid Theme JSON format" else null
                            if (error != null) {
                                Text(error, color = theme.textDanger, fontSize = theme.typography.helper, lineHeight = theme.typography.helperLineHeight)
                            }
                        },
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = theme.typography.small, color = theme.textPrimary),
                        minLines = 4,
                        maxLines = 8,
                        modifier = Modifier.testTag("theme-json-input").fillMaxWidth().heightIn(min = theme.dimensions.themeInputHeight),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor   = theme.textPrimary,
                            unfocusedTextColor = theme.textPrimary,
                            errorTextColor = theme.textPrimary,
                            focusedBorderColor   = theme.controlAccent,
                            unfocusedBorderColor = theme.indicator,
                            errorBorderColor = theme.textDanger,
                            focusedLabelColor   = theme.textAccent,
                            unfocusedLabelColor = theme.textSecondary,
                            errorLabelColor = theme.textDanger,
                            errorSupportingTextColor = theme.textDanger,
                            cursorColor = theme.controlAccent
                        )
                    )
                    Spacer(Modifier.height(theme.dimensions.spacingMedium))

                    MinimalOutlinedButton(
                        text = "Import Custom Theme",
                        onClick = {
                            if (!isImportPending && inputThemeJson.isNotBlank() && isJsonValid) {
                                val submittedJson = inputThemeJson
                                isImportPending = true
                                importError = null
                                coroutineScope.launch {
                                    try {
                                        importTheme(submittedJson)
                                        if (inputThemeJson == submittedJson) inputThemeJson = ""
                                        Toast.makeText(context, "Theme applied", Toast.LENGTH_SHORT).show()
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        importError = "Could not import theme. Your draft is preserved. Try again."
                                    } finally {
                                        isImportPending = false
                                    }
                                }
                            }
                        },
                        enabled = inputThemeJson.isNotBlank() && isJsonValid && !isImportPending,
                        theme = theme,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                1 -> { // Behavior tab
                    Text(
                        text = "EDITOR BEHAVIOR",
                        color = theme.textSecondary,
                        fontSize = theme.typography.tiny,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(Modifier.height(theme.dimensions.spacingSmall))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(value = currentSticky, role = Role.Switch) { value ->
                                coroutineScope.launch(Dispatchers.IO) {
                                    db.settingDao().insertSetting(SettingEntity(SettingKeys.STICKY_SELECTION, value.toString()))
                                }
                            }
                            .padding(vertical = theme.dimensions.spacingMedium),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                      ) {
                          Column(modifier = Modifier.weight(1f).padding(end = theme.dimensions.spacingExtraLarge)) {
                              Text(
                                  text = "Sticky Selection",
                                  color = theme.textPrimary,
                                  fontSize = theme.typography.body,
                                  fontFamily = appFont,
                                  fontWeight = FontWeight.Medium
                              )
                              Text(
                                  text = "Maintain text selection after applying formatting.",
                                  color = theme.textSecondary,
                                  fontSize = theme.typography.helper,
                                  lineHeight = theme.typography.helperLineHeight
                              )
                          }
                          Switch(
                              checked = currentSticky,
                              modifier = Modifier.testTag("sticky-selection-control"),
                              onCheckedChange = null,
                              colors = SwitchDefaults.colors(
                                  checkedThumbColor = theme.onControlAccent,
                                  checkedTrackColor = theme.controlAccent,
                                 uncheckedThumbColor = theme.indicator,
                                 uncheckedBorderColor = theme.indicator
                              )
                          )
                      }

                      Spacer(Modifier.height(theme.dimensions.spacingLarge))
                      HorizontalDivider(thickness = theme.dimensions.borderWidth, color = theme.textMuted.copy(alpha = 0.15f))
                      Spacer(Modifier.height(theme.dimensions.spacingLarge))

                      Row(
                          modifier = Modifier
                              .fillMaxWidth()
                              .toggleable(value = currentLaunchNewFile, role = Role.Switch) { value ->
                                  coroutineScope.launch(Dispatchers.IO) {
                                      db.settingDao().insertSetting(SettingEntity(SettingKeys.LAUNCH_NEW_FILE, value.toString()))
                                  }
                              }
                              .padding(vertical = theme.dimensions.spacingMedium),
                          verticalAlignment = Alignment.CenterVertically,
                          horizontalArrangement = Arrangement.SpaceBetween
                      ) {
                          Column(modifier = Modifier.weight(1f).padding(end = theme.dimensions.spacingExtraLarge)) {
                               Text(
                                  text = "Launch New File",
                                  color = theme.textPrimary,
                                  fontSize = theme.typography.body,
                                  fontFamily = appFont,
                                  fontWeight = FontWeight.Medium
                              )
                              Text(
                                  text = "Open the editor automatically when creating a new file.",
                                  color = theme.textSecondary,
                                  fontSize = theme.typography.helper,
                                  lineHeight = theme.typography.helperLineHeight
                              )
                          }
                          Switch(
                              checked = currentLaunchNewFile,
                              onCheckedChange = null,
                              colors = SwitchDefaults.colors(
                                  checkedThumbColor = theme.onControlAccent,
                                  checkedTrackColor = theme.controlAccent,
                                 uncheckedThumbColor = theme.indicator,
                                 uncheckedBorderColor = theme.indicator
                              )
                          )
                      }

                      Spacer(Modifier.height(theme.dimensions.spacingLarge))
                      HorizontalDivider(thickness = theme.dimensions.borderWidth, color = theme.textMuted.copy(alpha = 0.15f))
                      Spacer(Modifier.height(theme.dimensions.spacingLarge))

                      Text(
                          text = "TYPING & AUTOMATION",
                          color = theme.textSecondary,
                          fontSize = theme.typography.tiny,
                          fontFamily = FontFamily.Monospace,
                          fontWeight = FontWeight.Bold,
                          letterSpacing = 1.2.sp
                      )
                      Spacer(Modifier.height(theme.dimensions.spacingSmall))

                      Row(
                          modifier = Modifier
                              .fillMaxWidth()
                              .toggleable(value = currentAutoHeader, role = Role.Switch) { value ->
                                  coroutineScope.launch(Dispatchers.IO) {
                                      db.settingDao().insertSetting(SettingEntity(SettingKeys.AUTO_HEADER_SPACING, value.toString()))
                                  }
                              }
                              .padding(vertical = theme.dimensions.spacingMedium),
                          verticalAlignment = Alignment.CenterVertically,
                          horizontalArrangement = Arrangement.SpaceBetween
                      ) {
                          Column(modifier = Modifier.weight(1f).padding(end = theme.dimensions.spacingExtraLarge)) {
                               Text(
                                  text = "Auto Header Spacing",
                                  color = theme.textPrimary,
                                  fontSize = theme.typography.body,
                                  fontFamily = appFont,
                                  fontWeight = FontWeight.Medium
                              )
                              Text(
                                  text = "Insert a space after header symbols automatically (e.g. # Header).",
                                  color = theme.textSecondary,
                                  fontSize = theme.typography.helper,
                                  lineHeight = theme.typography.helperLineHeight
                              )
                          }
                          Switch(
                              checked = currentAutoHeader,
                              onCheckedChange = null,
                              colors = SwitchDefaults.colors(
                                  checkedThumbColor = theme.onControlAccent,
                                  checkedTrackColor = theme.controlAccent,
                                 uncheckedThumbColor = theme.indicator,
                                 uncheckedBorderColor = theme.indicator
                              )
                          )
                      }

                      Spacer(Modifier.height(theme.dimensions.spacingLarge))
                      HorizontalDivider(thickness = theme.dimensions.borderWidth, color = theme.textMuted.copy(alpha = 0.15f))
                      Spacer(Modifier.height(theme.dimensions.spacingLarge))


                      Row(
                          modifier = Modifier
                              .fillMaxWidth()
                              .toggleable(value = currentSentenceCapitalization, role = Role.Switch) { value ->
                                  coroutineScope.launch(Dispatchers.IO) {
                                      db.settingDao().insertSetting(SettingEntity(SettingKeys.SENTENCE_CAPITALIZATION, value.toString()))
                                  }
                              }
                              .padding(vertical = theme.dimensions.spacingMedium),
                          verticalAlignment = Alignment.CenterVertically,
                          horizontalArrangement = Arrangement.SpaceBetween
                      ) {
                          Column(modifier = Modifier.weight(1f).padding(end = theme.dimensions.spacingExtraLarge)) {
                               Text(
                                  text = "Sentence Capitalization",
                                  color = theme.textPrimary,
                                  fontSize = theme.typography.body,
                                  fontFamily = appFont,
                                  fontWeight = FontWeight.Medium
                              )
                              Text(
                                  text = "Automatically capitalize the first letter of sentences.",
                                  color = theme.textSecondary,
                                  fontSize = theme.typography.helper,
                                  lineHeight = theme.typography.helperLineHeight
                              )
                          }
                          Switch(
                              checked = currentSentenceCapitalization,
                              onCheckedChange = null,
                              colors = SwitchDefaults.colors(
                                  checkedThumbColor = theme.onControlAccent,
                                  checkedTrackColor = theme.controlAccent,
                                 uncheckedThumbColor = theme.indicator,
                                 uncheckedBorderColor = theme.indicator
                              )
                          )
                      }

                      Spacer(Modifier.height(theme.dimensions.spacingLarge))
                      HorizontalDivider(thickness = theme.dimensions.borderWidth, color = theme.textMuted.copy(alpha = 0.15f))
                      Spacer(Modifier.height(theme.dimensions.spacingLarge))

                      Row(
                          modifier = Modifier
                              .fillMaxWidth()
                              .toggleable(value = currentShowWorkspaceIntro, role = Role.Switch) { value ->
                                  coroutineScope.launch(Dispatchers.IO) {
                                      db.settingDao().insertSetting(SettingEntity(SettingKeys.SHOW_WORKSPACE_INTRO, value.toString()))
                                  }
                              }
                              .padding(vertical = theme.dimensions.spacingMedium),
                          verticalAlignment = Alignment.CenterVertically,
                          horizontalArrangement = Arrangement.SpaceBetween
                      ) {
                          Column(modifier = Modifier.weight(1f).padding(end = theme.dimensions.spacingExtraLarge)) {
                               Text(
                                  text = "Workspace Intro Dialog",
                                  color = theme.textPrimary,
                                  fontSize = theme.typography.body,
                                  fontFamily = appFont,
                                  fontWeight = FontWeight.Medium
                              )
                              Text(
                                  text = "Show confirmation prompt before adding watched folders.",
                                  color = theme.textSecondary,
                                  fontSize = theme.typography.helper,
                                  lineHeight = theme.typography.helperLineHeight
                              )
                          }
                          Switch(
                              checked = currentShowWorkspaceIntro,
                              onCheckedChange = null,
                              colors = SwitchDefaults.colors(
                                  checkedThumbColor = theme.onControlAccent,
                                  checkedTrackColor = theme.controlAccent,
                                 uncheckedThumbColor = theme.indicator,
                                 uncheckedBorderColor = theme.indicator
                              )
                          )
                      }

                      Spacer(Modifier.height(theme.dimensions.spacingLarge))
                      HorizontalDivider(thickness = theme.dimensions.borderWidth, color = theme.textMuted.copy(alpha = 0.15f))
                      Spacer(Modifier.height(theme.dimensions.spacingLarge))

                      Row(
                          modifier = Modifier
                              .fillMaxWidth()
                              .toggleable(value = currentAutoComplete, role = Role.Switch) { value ->
                                  coroutineScope.launch(Dispatchers.IO) {
                                      db.settingDao().insertSetting(SettingEntity(SettingKeys.AUTO_COMPLETE_ENABLED, value.toString()))
                                  }
                              }
                              .padding(vertical = theme.dimensions.spacingMedium),
                          verticalAlignment = Alignment.CenterVertically,
                          horizontalArrangement = Arrangement.SpaceBetween
                      ) {
                          Column(modifier = Modifier.weight(1f).padding(end = theme.dimensions.spacingExtraLarge)) {
                               Text(
                                  text = "Auto Complete",
                                  color = theme.textPrimary,
                                  fontSize = theme.typography.body,
                                  fontFamily = appFont,
                                  fontWeight = FontWeight.Medium
                              )
                              Text(
                                  text = "Auto-pair quotes, brackets, and braces.",
                                  color = theme.textSecondary,
                                  fontSize = theme.typography.helper,
                                  lineHeight = theme.typography.helperLineHeight
                              )
                          }
                          Switch(
                              checked = currentAutoComplete,
                              onCheckedChange = null,
                              colors = SwitchDefaults.colors(
                                  checkedThumbColor = theme.onControlAccent,
                                  checkedTrackColor = theme.controlAccent,
                                 uncheckedThumbColor = theme.indicator,
                                 uncheckedBorderColor = theme.indicator
                              )
                          )
                      }

                      if (currentAutoComplete) {
                          Spacer(Modifier.height(theme.dimensions.spacingMedium))
                          FlowRow(
                              modifier = Modifier
                                  .fillMaxWidth()
                                  .padding(bottom = theme.dimensions.spacingLarge),
                              horizontalArrangement = Arrangement.spacedBy(theme.dimensions.spacingMedium),
                              verticalArrangement = Arrangement.spacedBy(theme.dimensions.spacingSmall)
                          ) {
                              MinimalOutlinedButtonSmall(text = "\"\"", selected = currentAutoCompleteQuotes, onClick = {
                                  coroutineScope.launch(Dispatchers.IO) { db.settingDao().insertSetting(SettingEntity(SettingKeys.AUTO_COMPLETE_QUOTES, (!currentAutoCompleteQuotes).toString())) }
                              }, theme = theme)
                              MinimalOutlinedButtonSmall(text = "''", selected = currentAutoCompleteSingleQuotes, onClick = {
                                  coroutineScope.launch(Dispatchers.IO) { db.settingDao().insertSetting(SettingEntity(SettingKeys.AUTO_COMPLETE_SINGLE_QUOTES, (!currentAutoCompleteSingleQuotes).toString())) }
                              }, theme = theme)
                              MinimalOutlinedButtonSmall(text = "{}", selected = currentAutoCompleteBraces, onClick = {
                                  coroutineScope.launch(Dispatchers.IO) { db.settingDao().insertSetting(SettingEntity(SettingKeys.AUTO_COMPLETE_BRACES, (!currentAutoCompleteBraces).toString())) }
                              }, theme = theme)
                              MinimalOutlinedButtonSmall(text = "()", selected = currentAutoCompleteParens, onClick = {
                                  coroutineScope.launch(Dispatchers.IO) { db.settingDao().insertSetting(SettingEntity(SettingKeys.AUTO_COMPLETE_PARENS, (!currentAutoCompleteParens).toString())) }
                              }, theme = theme)
                              MinimalOutlinedButtonSmall(text = "[]", selected = currentAutoCompleteBrackets, onClick = {
                                  coroutineScope.launch(Dispatchers.IO) { db.settingDao().insertSetting(SettingEntity(SettingKeys.AUTO_COMPLETE_BRACKETS, (!currentAutoCompleteBrackets).toString())) }
                              }, theme = theme)
                          }
                      }
                }
                2 -> { // Stats tab
                    StatsTabContent(db = db, theme = theme, appFont = appFont)
                }
                3 -> { // Sync tab
                     Text("CLOUD SYNC", color = theme.textSecondary, fontSize = theme.typography.tiny, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                     Spacer(Modifier.height(theme.dimensions.spacingMedium))
                     Text(
                         "We are actively working on adding cloud synchronization features so you can access your documents anywhere. Stay tuned for updates!",
                         color = theme.textSecondary,
                         fontSize = theme.typography.body,
                         fontFamily = appFont,
                         lineHeight = theme.typography.title
                     )
                     Spacer(Modifier.height(theme.dimensions.spacingExtraLarge))
                }
                4 -> { // About tab
                     Text(
                         text = "PRIVACY",
                         color = theme.textSecondary,
                         fontSize = theme.typography.tiny,
                         fontFamily = FontFamily.Monospace,
                         fontWeight = FontWeight.Bold,
                         letterSpacing = 1.2.sp
                     )
                     Spacer(Modifier.height(theme.dimensions.spacingSmall))
                     Row(
                         modifier = Modifier
                             .fillMaxWidth()
                             .toggleable(value = currentTelemetryCollection, role = Role.Switch) { enabled ->
                                 TelemetryCollection.setEnabled(enabled)
                                 coroutineScope.launch(Dispatchers.IO) {
                                     db.settingDao().insertSetting(
                                         SettingEntity(TelemetryCollection.SETTING_KEY, enabled.toString())
                                     )
                                 }
                             }
                             .padding(vertical = theme.dimensions.spacingMedium),
                         verticalAlignment = Alignment.CenterVertically,
                         horizontalArrangement = Arrangement.SpaceBetween
                     ) {
                         Column(modifier = Modifier.weight(1f).padding(end = theme.dimensions.spacingExtraLarge)) {
                             Text(
                                 text = "Help Improve Kern",
                                 color = theme.textPrimary,
                                 fontSize = theme.typography.body,
                                 fontFamily = appFont,
                                 fontWeight = FontWeight.Medium
                             )
                             Text(
                                 text = "Share anonymous analytics and crash logs to help improve the app.",
                                 color = theme.textSecondary,
                                 fontSize = theme.typography.helper,
                                 lineHeight = theme.typography.helperLineHeight
                             )
                         }
                         Switch(
                             checked = currentTelemetryCollection,
                             onCheckedChange = null,
                             colors = SwitchDefaults.colors(
                                 checkedThumbColor = theme.onControlAccent,
                                 checkedTrackColor = theme.controlAccent,
                                 uncheckedThumbColor = theme.indicator,
                                 uncheckedBorderColor = theme.indicator
                             )
                         )
                     }
                     Text(
                         text = "This is on by default. The data is anonymized and only used to improve Kern. You can turn it off at any time.",
                         color = theme.textSecondary,
                         fontSize = theme.typography.helper,
                         fontFamily = appFont,
                         lineHeight = theme.typography.helperLineHeight
                     )
                     Spacer(Modifier.height(theme.dimensions.spacingExtraLarge))
                     HorizontalDivider(thickness = theme.dimensions.borderWidth, color = theme.textMuted.copy(alpha = 0.15f))
                     Spacer(Modifier.height(theme.dimensions.spacingExtraLarge))
                     FlowRow(
                         modifier = Modifier.fillMaxWidth().padding(vertical = theme.dimensions.spacingMedium),
                         horizontalArrangement = Arrangement.SpaceBetween,
                         verticalArrangement = Arrangement.spacedBy(8.dp)
                     ) {
                         Text("Kern", color = theme.textPrimary, fontSize = theme.typography.bodyLarge,
                             fontFamily = appFont, fontWeight = FontWeight.Medium)
                          val appVersion = remember {
                              try {
                                  val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                                  "Version ${packageInfo.versionName ?: "1.0"}"
                              } catch (e: Exception) {
                                  "Version 1.0"
                              }
                          }
                          Text(appVersion, color = theme.textSecondary, fontSize = theme.typography.tiny,
                              fontFamily = FontFamily.Monospace)
                     }
                    Spacer(Modifier.height(theme.dimensions.spacingExtraLarge))
                    HorizontalDivider(thickness = theme.dimensions.borderWidth, color = theme.textMuted.copy(alpha = 0.15f))
                    Spacer(Modifier.height(theme.dimensions.spacingExtraLarge))
                     
                     Text(
                         text = "A modern, typography-focused Markdown editor built for distraction-free writing, local file privacy, and rich document analytics.",
                         color = theme.textSecondary,
                         fontSize = theme.typography.body,
                         fontFamily = appFont,
                         lineHeight = theme.typography.title,
                         modifier = Modifier.padding(vertical = theme.dimensions.spacingSmall)
                     )

                     Spacer(Modifier.height(theme.dimensions.spacingSmall))

                     AboutLinkRow("Design & Development", "Attach.design", theme, appFont) {
                         try { uriHandler.openUri("https://kern.attach.design") } catch (e: Exception) { e.printStackTrace() }
                     }

                     AboutLinkRow("Source Code", "GitHub Repository", theme, appFont) {
                         try { uriHandler.openUri("https://github.com/iamkeeler/kern-android-markdown") } catch (e: Exception) { e.printStackTrace() }
                     }

                     AboutLinkRow("Privacy Policy", "Read Policy", theme, appFont) {
                         try { uriHandler.openUri("https://kern.attach.design/privacy.html") } catch (e: Exception) { e.printStackTrace() }
                     }

                     Spacer(Modifier.height(theme.dimensions.spacingExtraLarge))
                     HorizontalDivider(thickness = theme.dimensions.borderWidth, color = theme.textMuted.copy(alpha = 0.15f))
                     Spacer(Modifier.height(theme.dimensions.spacingExtraLarge))

                     Text(
                         text = "OPEN SOURCE LIBRARIES",
                         color = theme.textSecondary,
                         fontSize = theme.typography.tiny,
                         fontFamily = FontFamily.Monospace,
                         fontWeight = FontWeight.Bold,
                         letterSpacing = 1.2.sp
                     )
                     Spacer(Modifier.height(theme.dimensions.elevationMedium))

                     val libraries = listOf(
                         "Jetpack Compose"                to "Apache 2.0 · Google",
                         "Compose Material 3"             to "Apache 2.0 · Google",
                         "Compose Material Icons"         to "Apache 2.0 · Google",
                         "AndroidX Navigation 3"          to "Apache 2.0 · Google",
                         "Room Persistence Library"       to "Apache 2.0 · Google",
                         "AndroidX Lifecycle & ViewModel" to "Apache 2.0 · Google",
                         "AndroidX Core, Activity & DocumentFile" to "Apache 2.0 · Google",
                         "Firebase Analytics & Crashlytics" to "Apache 2.0 · Google",
                         "KotlinX Serialization"          to "Apache 2.0 · JetBrains",
                         "KotlinX Collections Immutable"  to "Apache 2.0 · JetBrains",
                         "Kotlin & Coroutines"            to "Apache 2.0 · JetBrains",
                         "Coil Compose"                   to "Apache 2.0 · Coil contributors",
                         "Coil Network"                   to "Apache 2.0 · Coil contributors"
                     )
                     libraries.forEach { (lib, license) ->
                          FlowRow(
                              modifier = Modifier.fillMaxWidth().padding(vertical = theme.dimensions.elevationMedium),
                              horizontalArrangement = Arrangement.spacedBy(16.dp),
                              verticalArrangement = Arrangement.spacedBy(4.dp)
                          ) {
                              Text(lib, color = theme.textPrimary, fontSize = theme.typography.body,
                                  fontFamily = appFont)
                             Text(license, color = theme.textSecondary, fontSize = theme.typography.helper, lineHeight = theme.typography.helperLineHeight,
                                 fontFamily = FontFamily.Monospace)
                         }
                     }
                }
            }
            Spacer(Modifier.height(theme.dimensions.spacingMassive))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    db: AppDatabase,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme by ThemeEngine.observeActiveTheme(db).collectAsState(initial = ThemeEngine.DefaultLight.toColorTheme())

    val appFont = theme.toFontFamily()

     var isVisible by remember { mutableStateOf(false) }
     LaunchedEffect(Unit) { isVisible = true }

    androidx.compose.animation.AnimatedVisibility(
        visible = isVisible,
        enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(50)),
        exit = androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(50)),
        modifier = modifier.fillMaxSize().background(theme.background)
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
        val gutter = if (maxWidth >= 600.dp) 24.dp else 16.dp
        Scaffold(
            containerColor = theme.background,
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Text("Settings", fontFamily = appFont, fontWeight = FontWeight.Light, fontSize = theme.typography.h1)
                        },
                        navigationIcon = {
                            IconButton(onClick = onBackClick) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            titleContentColor = theme.textPrimary,
                            navigationIconContentColor = theme.textPrimary
                        )
                    )
                    HorizontalDivider(
                        thickness = 2.dp,
                        color = theme.headerDivider
                    )
                }
            }
        ) { innerPadding ->
            SettingsTabsContent(
                db = db,
                theme = theme,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = gutter)
            )
        }
    }
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AboutLinkRow(
    label: String,
    title: String,
    theme: com.attachdesign.kern.ui.theme.AppColorTheme,
    appFont: FontFamily,
    onClick: () -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(label, color = theme.textSecondary, fontSize = theme.typography.helper,
            lineHeight = theme.typography.helperLineHeight, fontFamily = appFont)
        Text(title, color = theme.textAccent, fontSize = theme.typography.body,
            fontFamily = appFont, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SettingsRadioRow(
    label: String,
    selected: Boolean,
    theme: com.attachdesign.kern.ui.theme.AppColorTheme,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = theme.dimensions.spacingMedium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f).padding(end = theme.dimensions.spacingExtraLarge),
            color = theme.textPrimary,
            fontSize = theme.typography.body,
            fontFamily = theme.toFontFamily()
        )
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(
                selectedColor = theme.controlAccent,
                unselectedColor = theme.indicator
            )
        )
    }
}

@Composable
fun MinimalOutlinedButton(
    text: String,
    onClick: () -> Unit,
    theme: com.attachdesign.kern.ui.theme.AppColorTheme,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    enabled: Boolean = true
) {
    val appFont = theme.toFontFamily()
    val bgColor = if (isPrimary) theme.controlAccent else Color.Transparent
    val textColor = if (isPrimary) theme.onControlAccent else theme.textPrimary
    val borderColor = if (isPrimary) theme.controlAccent else theme.indicator

    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(theme.dimensions.spacingSmall),
        border = BorderStroke(theme.dimensions.borderWidth, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = bgColor,
            contentColor = textColor
        ),
        contentPadding = PaddingValues(horizontal = theme.dimensions.spacingExtraLarge, vertical = theme.dimensions.spacingMedium)
    ) {
        Text(
            text = text,
            fontSize = theme.typography.bodyLarge,
            fontFamily = appFont,
            fontWeight = if (isPrimary) FontWeight.Medium else FontWeight.Normal
        )
    }
}

@Composable
fun MinimalOutlinedButtonSmall(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    theme: com.attachdesign.kern.ui.theme.AppColorTheme,
    modifier: Modifier = Modifier
) {
    val border    = if (selected) theme.controlAccent else theme.indicator
    val bg        = if (selected) theme.controlAccent else Color.Transparent
    val textColor = if (selected) theme.onControlAccent else theme.textSecondary

    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .semantics {
                contentDescription = when (text) {
                    "\"\"" -> "Auto-pair double quotes"
                    "''" -> "Auto-pair single quotes"
                    "{}" -> "Auto-pair braces"
                    "()" -> "Auto-pair parentheses"
                    "[]" -> "Auto-pair brackets"
                    else -> text
                }
                this.selected = selected
                stateDescription = if (selected) "On" else "Off"
            },
        shape = RoundedCornerShape(theme.dimensions.spacingSmall),
        border = BorderStroke(theme.dimensions.borderWidth, border),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = bg,
            contentColor = textColor
        ),
        contentPadding = PaddingValues(horizontal = theme.dimensions.spacingLarge, vertical = theme.dimensions.elevationMedium)
    ) {
        Text(
            text = text,
            fontSize = theme.typography.small,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun StatsTabContent(
    db: AppDatabase,
    theme: com.attachdesign.kern.ui.theme.AppColorTheme,
    appFont: FontFamily
) {
    val coroutineScope = rememberCoroutineScope()
    val statsRepository = remember(db) { StatsRepository(db) }
    val userStats by statsRepository.getStatsFlow().collectAsState(initial = UserStats())
    var showResetDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = theme.dimensions.spacingSmall)
    ) {
        // WRITING METRICS
        Text(
            text = "WRITING METRICS",
            color = theme.textSecondary,
            fontSize = theme.typography.tiny,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
        Spacer(Modifier.height(theme.dimensions.spacingMedium))

        AdaptiveMetricPair(
            first = { tileModifier ->
            MetricStatTile(
                label = "Words Written",
                value = UserStats.formatNumber(userStats.wordsWritten),
                theme = theme,
                appFont = appFont,
                modifier = tileModifier
            )
            },
            second = { tileModifier ->
            MetricStatTile(
                label = "Characters Written",
                value = UserStats.formatNumber(userStats.charactersWritten),
                theme = theme,
                appFont = appFont,
                modifier = tileModifier
            )
            }
        )

        Spacer(Modifier.height(theme.dimensions.spacingExtraLarge))
        HorizontalDivider(
            thickness = theme.dimensions.borderWidth,
            color = theme.textMuted.copy(alpha = 0.15f)
        )
        Spacer(Modifier.height(theme.dimensions.spacingExtraLarge))

        // READING METRICS
        Text(
            text = "READING METRICS",
            color = theme.textSecondary,
            fontSize = theme.typography.tiny,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
        Spacer(Modifier.height(theme.dimensions.spacingMedium))

        AdaptiveMetricPair(
            first = { tileModifier ->
            MetricStatTile(
                label = "Words Read",
                value = UserStats.formatNumber(userStats.wordsRead),
                theme = theme,
                appFont = appFont,
                modifier = tileModifier
            )
            },
            second = { tileModifier ->
            MetricStatTile(
                label = "Est. Reading Time",
                value = UserStats.formatReadingTime(userStats.estimatedReadingTimeMinutes),
                theme = theme,
                appFont = appFont,
                modifier = tileModifier
            )
            }
        )

        Spacer(Modifier.height(theme.dimensions.spacingExtraLarge))
        HorizontalDivider(
            thickness = theme.dimensions.borderWidth,
            color = theme.textMuted.copy(alpha = 0.15f)
        )
        Spacer(Modifier.height(theme.dimensions.spacingExtraLarge))

        // ACTIVITY METRICS
        Text(
            text = "ACTIVITY METRICS",
            color = theme.textSecondary,
            fontSize = theme.typography.tiny,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
        Spacer(Modifier.height(theme.dimensions.spacingMedium))

        AdaptiveMetricPair(
            first = { tileModifier ->
            MetricStatTile(
                label = "Documents Opened",
                value = UserStats.formatNumber(userStats.documentsOpened),
                theme = theme,
                appFont = appFont,
                modifier = tileModifier
            )
            },
            second = { tileModifier ->
            MetricStatTile(
                label = "Times Shared",
                value = UserStats.formatNumber(userStats.timesShared),
                theme = theme,
                appFont = appFont,
                modifier = tileModifier
            )
            }
        )

        Spacer(Modifier.height(theme.dimensions.spacingExtraLarge))
        HorizontalDivider(
            thickness = theme.dimensions.borderWidth,
            color = theme.textMuted.copy(alpha = 0.15f)
        )
        Spacer(Modifier.height(theme.dimensions.spacingExtraLarge))

        // WORKSPACE TOTALS
        Text(
            text = "WORKSPACE TOTALS",
            color = theme.textSecondary,
            fontSize = theme.typography.tiny,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp
        )
        Spacer(Modifier.height(theme.dimensions.spacingMedium))

        AdaptiveMetricPair(
            first = { tileModifier ->
            MetricStatTile(
                label = "Indexed Files",
                value = UserStats.formatNumber(userStats.totalIndexedFiles),
                theme = theme,
                appFont = appFont,
                modifier = tileModifier
            )
            },
            second = { tileModifier ->
            MetricStatTile(
                label = "Workspace Words",
                value = UserStats.formatNumber(userStats.totalIndexedWords),
                theme = theme,
                appFont = appFont,
                modifier = tileModifier
            )
            }
        )

        Spacer(Modifier.height(theme.dimensions.spacingExtraLarge * 2))

        // Reset Button
        OutlinedButton(
            onClick = { showResetDialog = true },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.textDanger),
            border = BorderStroke(theme.dimensions.borderWidth, theme.textDanger)
        ) {
            Text("Reset Statistics", fontFamily = appFont, fontSize = theme.typography.body)
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Statistics", fontFamily = appFont) },
            text = { Text("Are you sure you want to reset all tracked writing, reading, and document activity statistics?", fontFamily = appFont) },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            statsRepository.resetAllStats()
                            showResetDialog = false
                        }
                    }
                ) {
                    Text("Reset", color = theme.textDanger, fontFamily = appFont)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel", fontFamily = appFont)
                }
            }
        )
    }
}

@Composable
private fun MetricStatTile(
    label: String,
    value: String,
    theme: com.attachdesign.kern.ui.theme.AppColorTheme,
    appFont: FontFamily,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = theme.textMuted.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(theme.dimensions.spacingSmall)
    ) {
        Column(modifier = Modifier.padding(theme.dimensions.spacingLarge)) {
        Text(
            text = value,
            fontSize = 26.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = theme.textPrimary
        )
        Spacer(Modifier.height(theme.dimensions.spacingTiny))
        Text(
            text = label,
            fontSize = theme.typography.helper,
            lineHeight = theme.typography.helperLineHeight,
            fontFamily = appFont,
            color = theme.textSecondary
        )
        }
    }
}

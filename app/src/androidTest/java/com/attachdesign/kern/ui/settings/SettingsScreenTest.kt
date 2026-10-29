package com.attachdesign.kern.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.key
import androidx.compose.ui.test.junit4.StateRestorationTester
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import androidx.test.espresso.Espresso
import org.junit.Assert.assertEquals
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.attachdesign.kern.data.local.AppDatabase
import com.attachdesign.kern.data.local.SettingEntity
import com.attachdesign.kern.data.local.SettingKeys
import com.attachdesign.kern.ui.theme.ThemeEngine
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Exercises the production settings rows and their Room-backed callbacks. */
class SettingsScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private lateinit var db: AppDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java
        ).allowMainThreadQueries().build()
        db.settingDao().insertSetting(SettingEntity(SettingKeys.STICKY_SELECTION, "true"))
    }

    @After
    fun tearDown() {
        rule.activityRule.scenario.close()
        db.close()
    }

    private fun show(
        fontScale: Float = 1f,
        restoration: StateRestorationTester? = null,
        importTheme: suspend (String) -> Unit = { persistImportedTheme(db, it) }
    ) {
        val content: @androidx.compose.runtime.Composable () -> Unit = {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MaterialTheme {
                    Box(Modifier.width(320.dp).fillMaxSize().padding(horizontal = 16.dp)) {
                        SettingsTabsContent(db, ThemeEngine.DefaultLight.toColorTheme(), Modifier.fillMaxSize(), importTheme)
                    }
                }
            }
        }
        if (restoration != null) restoration.setContent(content) else rule.setContent(content)
        rule.waitForIdle()
    }

    @Test
    fun foldActionLivesInBehaviorSettingsAndPersistsAcrossReopening() {
        show(fontScale = 2f)
        rule.onNodeWithText("Behavior").performClick()
        rule.onNodeWithText("Fold shortcut").performScrollTo().performClick()
        rule.onNodeWithText("Second editor").performScrollTo().assertIsSelected()
        rule.onNodeWithText("Reader metrics (Hemingway)").performScrollTo().performClick()
        rule.waitUntil(5_000) {
            db.settingDao().getSetting(SettingKeys.FOLD_SHORTCUT_ACTION)?.value == "METRICS"
        }
        rule.onNodeWithText("Done").performClick()
        rule.onNodeWithText("Fold shortcut").performScrollTo().performClick()
        rule.onNodeWithText("Reader metrics (Hemingway)").performScrollTo().assertIsSelected()
        rule.onNodeWithText("Second editor").performScrollTo().performClick()
        rule.waitUntil(5_000) {
            db.settingDao().getSetting(SettingKeys.FOLD_SHORTCUT_ACTION)?.value == "SECOND_EDITOR"
        }
    }

    @Test
    fun settingRowHasOneSwitchActionAndPersistsOneToggle() {
        show()
        rule.onNodeWithText("Behavior").performClick()
        val row = hasText("Sticky Selection") and hasClickAction()
        val rowAction = hasClickAction() and hasAnyDescendant(hasText("Sticky Selection"))
        rule.onAllNodes(rowAction, useUnmergedTree = true).assertCountEquals(1)
        rule.onNode(row).assertIsOn().performClick()
        rule.waitUntil(5_000) {
            db.settingDao().getSetting(SettingKeys.STICKY_SELECTION)?.value == "false"
        }
        rule.onNode(row).assertIsOff()
        rule.onAllNodes(hasClickAction() and hasAnyAncestor(rowAction), useUnmergedTree = true)
            .assertCountEquals(0)
        rule.onNode(row).performClick()
        rule.waitUntil(5_000) {
            db.settingDao().getSetting(SettingKeys.STICKY_SELECTION)?.value == "true"
        }
        rule.onNode(row).assertIsOn()
    }

    @Test
    fun appearanceHasThreeRadioGroupsAndOneActionPerOption() {
        show()
        rule.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup) and
                hasAnyAncestor(hasTestTag("settings-tab-content")),
            useUnmergedTree = true
        )
            .assertCountEquals(3)
        val radio = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)
        rule.onAllNodes(radio and hasClickAction(), useUnmergedTree = true).assertCountEquals(8)
        val raw = hasText("Raw Plain-Text") and radio
        rule.onNode(raw).performScrollTo().performClick()
        rule.waitUntil(5_000) {
            db.settingDao().getSetting(SettingKeys.VIEW_MODE)?.value == "RAW_PLAIN_TEXT"
        }
        rule.onNode(raw).assertIsSelected()
        rule.onAllNodes(hasClickAction() and hasAnyAncestor(radio and hasAnyDescendant(hasText("Raw Plain-Text"))), useUnmergedTree = true)
            .assertCountEquals(0)
        rule.onNode(hasText("Sans-Serif (Modern)") and radio).performScrollTo().performClick()
        rule.waitUntil(5_000) {
            db.settingDao().getSetting(SettingKeys.EDITOR_FONT_FAMILY)?.value == "sans-serif"
        }
        rule.onNode(hasText("Sans-Serif (Modern)") and radio).assertIsSelected()
    }

    @Test
    fun largeFontDescriptionsAvoidControlsAndPairButtonsWrapWithFullTouchBounds() {
        show(fontScale = 2f)
        rule.onNodeWithText("Behavior").performClick()
        val description = rule.onNodeWithText(
            "Maintain text selection after applying formatting.", useUnmergedTree = true
        ).fetchSemanticsNode().boundsInRoot
        val control = rule.onNodeWithTag("sticky-selection-control", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Helper text must keep a separate column from the switch", description.right <= control.left)
        val names = listOf("double quotes", "single quotes", "braces", "parentheses", "brackets")
        rule.onNodeWithContentDescription("Auto-pair brackets").performScrollTo()
        val bounds = names.map { name ->
            rule.onNodeWithContentDescription("Auto-pair $name")
                .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
                .assertIsSelected().fetchSemanticsNode().boundsInRoot
        }
        assertTrue("Pair controls must wrap at narrow widths with large fonts", bounds.map { it.top }.distinct().size > 1)
        rule.onNodeWithContentDescription("Auto-pair brackets").performClick()
        rule.waitUntil(5_000) {
            db.settingDao().getSetting(SettingKeys.AUTO_COMPLETE_BRACKETS)?.value == "false"
        }
        // Room writes finish before the observed Flow necessarily reaches Compose.
        rule.waitUntil(5_000) {
            rule.onNodeWithContentDescription("Auto-pair brackets").fetchSemanticsNode()
                .config[SemanticsProperties.Selected] == false
        }
        rule.onNodeWithContentDescription("Auto-pair brackets").assertIsNotSelected()
    }

    private fun assertDraft(value: String) {
        rule.onNodeWithTag("theme-json-input").assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(value))
        )
    }

    private fun waitForClearedDraft() {
        rule.waitUntil(5_000) {
            rule.onNodeWithTag("theme-json-input").fetchSemanticsNode()
                .config[SemanticsProperties.EditableText].text.isEmpty()
        }
    }

    private val themeDraft: String
        get() = ThemeEngine.serialize(ThemeEngine.DefaultDark)

    private fun scrollOffset(): Float = rule.onNodeWithTag("settings-tab-content")
        .fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    @Test
    fun tabDraftAndSeparateScrollPositionsRestoreTogether() {
        val restoration = StateRestorationTester(rule)
        // At large fonts both tabs exceed the viewport, so each saved offset is observable.
        show(fontScale = 2f, restoration = restoration)
        rule.onNodeWithTag("theme-json-input").performScrollTo().performTextReplacement("retained draft")
        Espresso.closeSoftKeyboard()
        rule.onNodeWithText("Import Custom Theme").performScrollTo()
        val appearanceOffset = scrollOffset()
        assertTrue(appearanceOffset > 0)
        rule.onNodeWithText("Behavior").performClick()
        assertEquals(0f, scrollOffset(), 1f)
        rule.onNodeWithContentDescription("Auto-pair brackets").performScrollTo()
        val behaviorOffset = scrollOffset()
        assertTrue(behaviorOffset > 0)
        restoration.emulateSavedInstanceStateRestore()
        rule.onNodeWithText("Behavior").assertIsSelected()
        assertEquals(behaviorOffset, scrollOffset(), 1f)
        rule.onNodeWithText("Appearance").performClick()
        assertEquals(appearanceOffset, scrollOffset(), 1f)
        assertDraft("retained draft")
        rule.onNodeWithText("Behavior").performClick()
        assertEquals(behaviorOffset, scrollOffset(), 1f)
    }

    @Test
    fun aDifferentSettingsHostStartsWithItsOwnTabDraftAndScroll() {
        val host = mutableIntStateOf(0)
        rule.setContent {
            key(host.intValue) {
                MaterialTheme {
                    SettingsTabsContent(db, ThemeEngine.DefaultLight.toColorTheme(), Modifier.fillMaxSize())
                }
            }
        }
        rule.onNodeWithTag("theme-json-input").performScrollTo().performTextReplacement("first host draft")
        Espresso.closeSoftKeyboard()
        rule.onNodeWithText("Behavior").performClick()
        rule.onNodeWithContentDescription("Auto-pair brackets").performScrollTo()
        rule.runOnIdle { host.intValue = 1 }
        rule.onNodeWithText("Appearance").assertIsSelected()
        assertEquals(0f, scrollOffset(), 1f)
        rule.onNodeWithTag("theme-json-input").performScrollTo()
        assertDraft("")
    }

    @Test
    fun importRejectsBlankAndInvalidDraftAndGrowsForErrorText() {
        show()
        rule.onNodeWithText("Import Custom Theme").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("theme-json-input").performScrollTo().performTextReplacement("invalid")
        rule.onNodeWithText("Invalid Theme JSON format").assertExists()
        rule.onNodeWithText("Import Custom Theme").performScrollTo().assertIsNotEnabled()
        assertEquals(null, db.settingDao().getSetting(SettingKeys.SELECTED_THEME_ID))
    }

    @Test
    fun importWaitsForCommitGuardsRepeatedSubmissionAndClearsOnlyAfterSuccess() {
        val release = CompletableDeferred<Unit>()
        var calls = 0
        show(importTheme = { submitted ->
            calls++
            release.await()
            persistImportedTheme(db, submitted)
        })
        rule.onNodeWithTag("theme-json-input").performScrollTo().performTextReplacement(themeDraft)
        Espresso.closeSoftKeyboard()
        rule.onNodeWithText("Import Custom Theme").performScrollTo().performClick()
        rule.waitUntil(5_000) { calls == 1 }
        rule.onNodeWithText("Import Custom Theme").assertIsNotEnabled().performClick()
        rule.onNodeWithTag("theme-json-input").assertIsNotEnabled()
        assertDraft(themeDraft)
        assertEquals(null, db.settingDao().getSetting(SettingKeys.SELECTED_THEME_ID))
        assertEquals(null, db.themeDao().getThemeByJson(themeDraft))
        rule.runOnIdle { release.complete(Unit) }
        rule.waitUntil(5_000) { db.settingDao().getSetting(SettingKeys.SELECTED_THEME_ID) != null }
        rule.waitForIdle()
        waitForClearedDraft()
        rule.onNodeWithTag("theme-json-input").assertIsEnabled()
        assertDraft("")
        assertEquals(1, calls)
        val saved = requireNotNull(db.themeDao().getThemeByJson(themeDraft))
        assertEquals(saved.id.toString(), db.settingDao().getSetting(SettingKeys.SELECTED_THEME_ID)?.value)
    }

    @Test
    fun failedSelectionWriteRollsBackInsertAndRetainsDraftForRetry() {
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_theme_selection BEFORE INSERT ON settings " +
                "WHEN NEW.key = 'selected_theme_id' BEGIN SELECT RAISE(ABORT, 'test failure'); END"
        )
        show()
        rule.onNodeWithTag("theme-json-input").performScrollTo().performTextReplacement(themeDraft)
        Espresso.closeSoftKeyboard()
        rule.onNodeWithText("Import Custom Theme").performScrollTo().performClick()
        rule.waitUntil(5_000) {
            rule.onAllNodesWithText("Could not import theme. Your draft is preserved. Try again.")
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertDraft(themeDraft)
        rule.onNodeWithText("Import Custom Theme").assertIsEnabled()
        assertEquals(null, db.themeDao().getThemeByJson(themeDraft))
        assertEquals(null, db.settingDao().getSetting(SettingKeys.SELECTED_THEME_ID))
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_theme_selection")
        rule.onNodeWithText("Import Custom Theme").performClick()
        rule.waitUntil(5_000) { db.themeDao().getThemeByJson(themeDraft) != null }
        rule.waitForIdle()
        waitForClearedDraft()
        assertDraft("")
    }

    @Test
    fun retryAfterCommittedImportAndRestorationReusesTheExactJsonRow() {
        val restoration = StateRestorationTester(rule)
        val committed = CompletableDeferred<Unit>()
        val neverReturns = CompletableDeferred<Unit>()
        var attempts = 0
        show(restoration = restoration, importTheme = { submitted ->
            persistImportedTheme(db, submitted)
            attempts++
            if (attempts == 1) {
                committed.complete(Unit)
                neverReturns.await()
            }
        })
        rule.onNodeWithTag("theme-json-input").performScrollTo().performTextReplacement(themeDraft)
        Espresso.closeSoftKeyboard()
        rule.onNodeWithText("Import Custom Theme").performScrollTo().performClick()
        rule.waitUntil(5_000) { committed.isCompleted }
        val firstId = requireNotNull(db.themeDao().getThemeByJson(themeDraft)).id
        restoration.emulateSavedInstanceStateRestore()
        assertDraft(themeDraft)
        rule.onNodeWithText("Import Custom Theme").assertIsEnabled().performClick()
        rule.waitUntil(5_000) { attempts == 2 }
        rule.waitForIdle()
        waitForClearedDraft()
        assertDraft("")
        assertEquals(firstId, db.themeDao().getThemeByJson(themeDraft)?.id)
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM themes").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
    }


    @Test
    fun importedThemesWithTheSameNameAndDifferentJsonKeepSeparateRows() = runBlocking {
        val otherDraft = ThemeEngine.serialize(ThemeEngine.DefaultDark.copy(accentHex = "#123456"))
        persistImportedTheme(db, themeDraft)
        persistImportedTheme(db, otherDraft)
        val original = requireNotNull(db.themeDao().getThemeByJson(themeDraft))
        val other = requireNotNull(db.themeDao().getThemeByJson(otherDraft))
        assertEquals(original.name, other.name)
        assertTrue(original.id != other.id)
        assertEquals(other.id.toString(), db.settingDao().getSetting(SettingKeys.SELECTED_THEME_ID)?.value)
    }

    @Test
    fun aboutLinksHaveFullTouchRowsAndLongLicensesRemainReachableAtLargeFonts() {
        show(fontScale = 2f)
        rule.onNodeWithText("About").performScrollTo().performClick()
        listOf("Attach.design", "GitHub Repository", "Read Policy").forEach { title ->
            val link = rule.onNode(hasText(title) and hasClickAction())
            link.performScrollTo().assertIsDisplayed()
            val bounds = link.fetchSemanticsNode().boundsInRoot
            val density = rule.activity.resources.displayMetrics.density
            assertTrue(bounds.height / density >= 48f)
        }
        rule.onNodeWithText("AndroidX Core, Activity & DocumentFile").performScrollTo().assertIsDisplayed()
    }

}

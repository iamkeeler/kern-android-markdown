package com.attachdesign.kern.ui.editor

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.attachdesign.kern.MainNavigation
import com.attachdesign.kern.data.local.AppDatabase
import com.attachdesign.kern.data.local.ProjectEntity
import com.attachdesign.kern.data.storage.StorageManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class QuickCaptureNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var db: AppDatabase
    private lateinit var storage: StorageManager
    private val project = ProjectEntity(1, "Capture", "capture_${System.nanoTime()}", false, true)

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        db.projectDao().insertProject(project)
        storage = StorageManager(context)
    }

    @After
    fun cleanup() {
        compose.activityRule.scenario.close()
        db.close()
    }

    private fun waitForText(text: String) {
        compose.waitUntil(30_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitForEditor() {
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("Document editor").fetchSemanticsNodes().size == 1
        }
        compose.waitUntil(15_000) {
            compose.onNodeWithContentDescription("Document editor").fetchSemanticsNode()
                .config[androidx.compose.ui.semantics.SemanticsProperties.Focused]
        }
    }

    @Test
    fun captureRenameUndoAndSystemBackPreserveIndependentFilesThroughRealNavigation() {
        compose.setContent { MainNavigation(db, storage) }
        waitForText("files")
        compose.onNodeWithContentDescription("New note").performClick()
        waitForEditor()
        compose.onNodeWithContentDescription("Document editor").assertIsFocused().performTextInput("A captured thought")
        compose.onNodeWithContentDescription("Document editor").performTextInput(" again")
        compose.onNodeWithContentDescription("Document editor").performSemanticsAction(SemanticsActions.SetSelection) {
            it(2, 9, false)
        }
        compose.onNodeWithText("Untitled note.md").performClick()
        compose.onNode(hasSetTextAction() and hasText("Document name")).performTextReplacement("Captured.md")
        compose.onNodeWithText("Rename").performClick()
        waitForText("Captured.md")
        assertEquals(TextRange(2, 9), compose.onNodeWithContentDescription("Document editor")
            .fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange])
        compose.onNodeWithContentDescription("Undo").assertIsEnabled()
        compose.onNodeWithContentDescription("Back").performClick()
        waitForText("Recent notes")
        assertEquals("A captured thought again", runBlocking { storage.readFile(project, "Captured.md") })

        compose.onNodeWithContentDescription("New note").performClick()
        waitForEditor()
        compose.onNodeWithContentDescription("Document editor").performTextInput("Second independent note")
        Espresso.closeSoftKeyboard()
        Espresso.pressBack()
        waitForText("Recent notes")
        assertEquals("Second independent note", runBlocking { storage.readFile(project, "Untitled note.md") })
        assertEquals("A captured thought again", runBlocking { storage.readFile(project, "Captured.md") })
    }
}

package com.attachdesign.kern.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingKeysTest {

    @Test
    fun testExactStringParityWithLegacyKeys() {
        assertEquals("selected_theme_id", SettingKeys.SELECTED_THEME_ID)
        assertEquals("editor_font_family", SettingKeys.EDITOR_FONT_FAMILY)
        assertEquals("editor_font_size_scale", SettingKeys.EDITOR_FONT_SIZE_SCALE)
        assertEquals("view_mode", SettingKeys.VIEW_MODE)
        assertEquals("sticky_selection", SettingKeys.STICKY_SELECTION)
        assertEquals("sync_provider", SettingKeys.SYNC_PROVIDER)
        assertEquals("auto_header_spacing", SettingKeys.AUTO_HEADER_SPACING)
        assertEquals("auto_complete_enabled", SettingKeys.AUTO_COMPLETE_ENABLED)
        assertEquals("auto_complete_quotes", SettingKeys.AUTO_COMPLETE_QUOTES)
        assertEquals("auto_complete_single_quotes", SettingKeys.AUTO_COMPLETE_SINGLE_QUOTES)
        assertEquals("auto_complete_braces", SettingKeys.AUTO_COMPLETE_BRACES)
        assertEquals("auto_complete_parens", SettingKeys.AUTO_COMPLETE_PARENS)
        assertEquals("auto_complete_brackets", SettingKeys.AUTO_COMPLETE_BRACKETS)
        assertEquals("sentence_capitalization", SettingKeys.SENTENCE_CAPITALIZATION)
        assertEquals("show_workspace_intro", SettingKeys.SHOW_WORKSPACE_INTRO)
        assertEquals("launch_new_file", SettingKeys.LAUNCH_NEW_FILE)
    }
}

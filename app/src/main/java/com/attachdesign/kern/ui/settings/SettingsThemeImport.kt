package com.attachdesign.kern.ui.settings

import androidx.room.withTransaction
import com.attachdesign.kern.data.local.AppDatabase
import com.attachdesign.kern.data.local.SettingEntity
import com.attachdesign.kern.data.local.SettingKeys
import com.attachdesign.kern.data.local.ThemeEntity
import com.attachdesign.kern.ui.theme.ThemeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Retrying the same draft after a committed, interrupted UI operation reuses its exact row. */
internal suspend fun persistImportedTheme(db: AppDatabase, submittedJson: String) {
    val theme = requireNotNull(ThemeEngine.deserialize(submittedJson)) { "Invalid theme JSON" }
    withContext(Dispatchers.IO) {
        db.withTransaction {
            val id = db.themeDao().getThemeByJson(submittedJson)?.id
                ?: db.themeDao().insertTheme(ThemeEntity(name = theme.name, jsonString = submittedJson))
            db.settingDao().insertSetting(SettingEntity(SettingKeys.SELECTED_THEME_ID, id.toString()))
        }
    }
}

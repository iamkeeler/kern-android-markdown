package com.attachdesign.kern.data.stats

import com.attachdesign.kern.data.local.AppDatabase
import com.attachdesign.kern.data.local.SettingEntity
import com.attachdesign.kern.domain.stats.UserStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

class StatsRepository(private val db: AppDatabase) {

    companion object {
        const val KEY_DOCS_OPENED = "stat_docs_opened"
        const val KEY_WORDS_WRITTEN = "stat_words_written"
        const val KEY_CHARS_WRITTEN = "stat_chars_written"
        const val KEY_WORDS_READ = "stat_words_read"
        const val KEY_TIMES_SHARED = "stat_times_shared"
    }

    fun getStatsFlow(): Flow<UserStats> {
        val userSettingsFlow = combine(
            db.settingDao().getSettingFlow(KEY_DOCS_OPENED),
            db.settingDao().getSettingFlow(KEY_WORDS_WRITTEN),
            db.settingDao().getSettingFlow(KEY_CHARS_WRITTEN),
            db.settingDao().getSettingFlow(KEY_WORDS_READ),
            db.settingDao().getSettingFlow(KEY_TIMES_SHARED)
        ) { docsOpened, wordsWritten, charsWritten, wordsRead, timesShared ->
            UserStats(
                documentsOpened = docsOpened?.value?.toLongOrNull() ?: 0L,
                wordsWritten = wordsWritten?.value?.toLongOrNull() ?: 0L,
                charactersWritten = charsWritten?.value?.toLongOrNull() ?: 0L,
                wordsRead = wordsRead?.value?.toLongOrNull() ?: 0L,
                timesShared = timesShared?.value?.toLongOrNull() ?: 0L
            )
        }

        return combine(
            userSettingsFlow,
            db.fileDao().getTotalFileCountFlow(),
            db.fileDao().getTotalWordCountFlow()
        ) { baseStats, totalFiles, totalWords ->
            baseStats.copy(
                totalIndexedFiles = totalFiles,
                totalIndexedWords = totalWords
            )
        }
    }

    suspend fun incrementDocumentsOpened() = withContext(Dispatchers.IO) {
        incrementKey(KEY_DOCS_OPENED, 1L)
    }

    suspend fun addWordsWritten(delta: Long) = withContext(Dispatchers.IO) {
        if (delta > 0) incrementKey(KEY_WORDS_WRITTEN, delta)
    }

    suspend fun addCharactersWritten(delta: Long) = withContext(Dispatchers.IO) {
        if (delta > 0) incrementKey(KEY_CHARS_WRITTEN, delta)
    }

    suspend fun addWordsRead(delta: Long) = withContext(Dispatchers.IO) {
        if (delta > 0) incrementKey(KEY_WORDS_READ, delta)
    }

    suspend fun incrementTimesShared() = withContext(Dispatchers.IO) {
        incrementKey(KEY_TIMES_SHARED, 1L)
    }

    suspend fun resetAllStats() = withContext(Dispatchers.IO) {
        val keys = listOf(
            KEY_DOCS_OPENED,
            KEY_WORDS_WRITTEN,
            KEY_CHARS_WRITTEN,
            KEY_WORDS_READ,
            KEY_TIMES_SHARED
        )
        for (key in keys) {
            db.settingDao().insertSetting(SettingEntity(key = key, value = "0"))
        }
    }

    private fun incrementKey(key: String, amount: Long) {
        val current = db.settingDao().getSetting(key)?.value?.toLongOrNull() ?: 0L
        val updated = current + amount
        db.settingDao().insertSetting(SettingEntity(key = key, value = updated.toString()))
    }
}

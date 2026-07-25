package com.attachdesign.kern.domain.stats

import androidx.compose.runtime.Immutable
import java.util.Locale

@Immutable
data class UserStats(
    val documentsOpened: Long = 0,
    val wordsWritten: Long = 0,
    val charactersWritten: Long = 0,
    val wordsRead: Long = 0,
    val timesShared: Long = 0,
    val totalIndexedFiles: Long = 0,
    val totalIndexedWords: Long = 0
) {
    /**
     * Estimated total reading time based on 200 words per minute average reading speed.
     */
    val estimatedReadingTimeMinutes: Long
        get() = (wordsRead / 200).coerceAtLeast(0)

    companion object {
        fun formatNumber(number: Long): String {
            return when {
                number >= 1_000_000 -> String.format(Locale.US, "%.1fM", number / 1_000_000.0)
                number >= 10_000 -> String.format(Locale.US, "%.1fk", number / 1_000.0)
                else -> String.format(Locale.US, "%,d", number)
            }
        }

        fun formatReadingTime(minutes: Long): String {
            return when {
                minutes < 1 -> "< 1 min"
                minutes < 60 -> "$minutes min"
                else -> {
                    val hours = minutes / 60
                    val remMins = minutes % 60
                    if (remMins == 0L) "${hours}h" else "${hours}h ${remMins}m"
                }
            }
        }
    }
}

package com.attachdesign.kern.domain.stats

import org.junit.Assert.assertEquals
import org.junit.Test

class StatsDomainTest {

    @Test
    fun testFormatNumber() {
        assertEquals("0", UserStats.formatNumber(0))
        assertEquals("999", UserStats.formatNumber(999))
        assertEquals("1,234", UserStats.formatNumber(1234))
        assertEquals("12.5k", UserStats.formatNumber(12500))
        assertEquals("1.5M", UserStats.formatNumber(1500000))
    }

    @Test
    fun testReadingTimeCalculation() {
        val stats0 = UserStats(wordsRead = 0)
        assertEquals(0L, stats0.estimatedReadingTimeMinutes)
        assertEquals("< 1 min", UserStats.formatReadingTime(stats0.estimatedReadingTimeMinutes))

        val statsShort = UserStats(wordsRead = 500) // 2.5 mins -> 2 mins
        assertEquals(2L, statsShort.estimatedReadingTimeMinutes)
        assertEquals("2 min", UserStats.formatReadingTime(statsShort.estimatedReadingTimeMinutes))

        val statsLong = UserStats(wordsRead = 24000) // 120 mins -> 2h
        assertEquals(120L, statsLong.estimatedReadingTimeMinutes)
        assertEquals("2h", UserStats.formatReadingTime(statsLong.estimatedReadingTimeMinutes))

        val statsMixed = UserStats(wordsRead = 15000) // 75 mins -> 1h 15m
        assertEquals(75L, statsMixed.estimatedReadingTimeMinutes)
        assertEquals("1h 15m", UserStats.formatReadingTime(statsMixed.estimatedReadingTimeMinutes))
    }
}

package com.attachdesign.kern.data.analytics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryCollectionTest {
    @Test
    fun defaultIsEnabled() {
        TelemetryCollection.setEnabled(TelemetryCollection.DEFAULT_ENABLED)

        assertTrue(TelemetryCollection.isEnabled())
    }

    @Test
    fun choiceCanDisableAndReenableCollection() {
        TelemetryCollection.setEnabled(false)
        assertFalse(TelemetryCollection.isEnabled())

        TelemetryCollection.setEnabled(true)
        assertTrue(TelemetryCollection.isEnabled())
    }
}

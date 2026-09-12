package com.attachdesign.kern.data.analytics

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TelemetryCollectionTest {
    // TelemetryCollection.enabled is process-wide mutable state (an AtomicBoolean
    // on the singleton). Reset it around every test so tests are independent of
    // execution order and cannot leak state into other test classes in the same
    // JVM, including under parallel execution.
    @Before
    fun setUp() {
        TelemetryCollection.setEnabled(TelemetryCollection.DEFAULT_ENABLED)
    }

    @After
    fun tearDown() {
        TelemetryCollection.setEnabled(TelemetryCollection.DEFAULT_ENABLED)
    }

    @Test
    fun defaultIsDisabled() {
        TelemetryCollection.setEnabled(TelemetryCollection.DEFAULT_ENABLED)

        assertFalse(TelemetryCollection.isEnabled())
    }

    @Test
    fun choiceCanDisableAndReenableCollection() {
        TelemetryCollection.setEnabled(false)
        assertFalse(TelemetryCollection.isEnabled())

        TelemetryCollection.setEnabled(true)
        assertTrue(TelemetryCollection.isEnabled())
    }
}

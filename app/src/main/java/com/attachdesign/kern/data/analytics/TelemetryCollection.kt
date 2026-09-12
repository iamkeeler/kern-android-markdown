package com.attachdesign.kern.data.analytics

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Process-wide consent state used by all telemetry integrations.
 *
 * Collection is DISABLED until the persisted preference has been read (H5):
 * new installs default to off, and no telemetry integration may enable
 * collection on its own — the persisted `telemetry_collection_enabled`
 * preference (applied by the app's startup flow) is the single source of truth.
 */
object TelemetryCollection {
    const val SETTING_KEY = "telemetry_collection_enabled"
    const val DEFAULT_ENABLED = false

    private val enabled = AtomicBoolean(DEFAULT_ENABLED)

    fun isEnabled(): Boolean = enabled.get()

    fun setEnabled(value: Boolean) {
        enabled.set(value)
    }
}

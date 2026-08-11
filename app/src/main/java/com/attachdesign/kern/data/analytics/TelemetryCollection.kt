package com.attachdesign.kern.data.analytics

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Process-wide consent state used by all telemetry integrations.
 *
 * Collection is enabled until the persisted preference has been read. This
 * preserves the documented default for new installs and existing users.
 */
object TelemetryCollection {
    const val SETTING_KEY = "telemetry_collection_enabled"
    const val DEFAULT_ENABLED = true

    private val enabled = AtomicBoolean(DEFAULT_ENABLED)

    fun isEnabled(): Boolean = enabled.get()

    fun setEnabled(value: Boolean) {
        enabled.set(value)
    }
}

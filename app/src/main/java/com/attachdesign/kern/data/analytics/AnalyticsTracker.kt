package com.attachdesign.kern.data.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

class AnalyticsTracker(context: Context) {
    private var firebaseAnalytics: FirebaseAnalytics? = null

    init {
        try {
            // H5: never enable collection here. The persisted
            // `telemetry_collection_enabled` preference is applied by the app's
            // startup flow, which calls setAnalyticsCollectionEnabled with the
            // stored value; until then collection stays at the Firebase default
            // (disabled via TelemetryCollection.DEFAULT_ENABLED = false).
            firebaseAnalytics = FirebaseAnalytics.getInstance(context.applicationContext)
        } catch (_: Exception) {
            // Safe fallback if Firebase is not initialized or running in unit tests
        }
    }

    fun logDocumentOpened() {
        logEvent("document_opened", null)
    }

    fun logDocumentShared() {
        logEvent("document_shared", null)
    }

    fun logWordsWritten(count: Long) {
        if (count <= 0) return
        val params = Bundle().apply {
            putLong("count", count)
        }
        logEvent("words_written", params)
    }

    fun logCharactersWritten(count: Long) {
        if (count <= 0) return
        val params = Bundle().apply {
            putLong("count", count)
        }
        logEvent("chars_written", params)
    }

    fun logWordsRead(count: Long) {
        if (count <= 0) return
        val params = Bundle().apply {
            putLong("count", count)
        }
        logEvent("words_read", params)
    }

    private fun logEvent(name: String, params: Bundle?) {
        if (!TelemetryCollection.isEnabled()) return
        try {
            firebaseAnalytics?.logEvent(name, params)
        } catch (_: Exception) {
            // Ignore errors in offline or uninitialized states
        }
    }
}

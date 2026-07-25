package com.attachdesign.kern.data.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

class AnalyticsTracker(context: Context) {
    private var firebaseAnalytics: FirebaseAnalytics? = null

    init {
        try {
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
        try {
            firebaseAnalytics?.logEvent(name, params)
        } catch (_: Exception) {
            // Ignore errors in offline or uninitialized states
        }
    }
}

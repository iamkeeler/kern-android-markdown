package com.attachdesign.kern

/**
 * One-shot request created when Android launches Kern through ACTION_VIEW.
 *
 * The monotonically increasing id lets Compose treat repeated opens of the same
 * URI as distinct events while still clearing each request after it is handled.
 */
data class ExternalOpenRequest(
    val id: Long,
    val uriString: String,
    val mimeType: String?
)

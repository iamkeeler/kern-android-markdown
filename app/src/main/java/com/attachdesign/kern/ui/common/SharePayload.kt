package com.attachdesign.kern.ui.common

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri

data class SharePayload(
    val uri: Uri,
    val mimeType: String,
    val textContent: String?,
    val title: String
)

fun Context.launchShare(payload: SharePayload) {
    val shareIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_STREAM, payload.uri)
        if (!payload.textContent.isNullOrEmpty()) {
            putExtra(Intent.EXTRA_TEXT, payload.textContent)
        }
        type = payload.mimeType
        clipData = ClipData.newRawUri(payload.title, payload.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(shareIntent, payload.title).apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(chooser)
}

package com.Atom2Universe.app.dictaphone

import android.net.Uri

data class DictaphoneRecording(
    val uri: Uri,
    val displayName: String,
    val durationMs: Long,
    val dateAddedSec: Long,
    val mimeType: String
)

package io.github.kylinlee.chatsim.domain.model

import android.net.Uri

data class AttachmentInfo(
    val uri: Uri,
    val filename: String,
    val mimeType: String,
    val size: Long,
    val isImage: Boolean,
    val isGif: Boolean,
    val isVCard: Boolean,
)

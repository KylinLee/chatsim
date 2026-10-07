package io.github.kylinlee.chatsim.repository

import android.net.Uri
import io.github.kylinlee.chatsim.domain.model.AttachmentInfo
import io.github.kylinlee.chatsim.data.model.Attachment
import ezvcard.VCard
import java.io.File

interface AttachmentRepository {
    suspend fun getAttachmentInfo(uri: Uri): AttachmentInfo?

    fun createAttachment(uri: Uri, messageId: Long = -1L, info: AttachmentInfo? = null): Attachment?

    suspend fun saveAttachment(sourceUri: Uri, destinationUri: Uri): Boolean

    fun getAttachmentsDir(): File

    fun createTempAttachmentFile(prefix: String = "attachment_", suffix: String = ".jpg"): File

    suspend fun parseVCard(uri: Uri): List<VCard>

    fun parseAttachmentNames(smilText: String): List<String>
}

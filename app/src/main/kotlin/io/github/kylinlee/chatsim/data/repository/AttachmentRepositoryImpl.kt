package io.github.kylinlee.chatsim.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import io.github.kylinlee.chatsim.common.extensions.getFilenameFromUri
import io.github.kylinlee.chatsim.di.IoDispatcher
import io.github.kylinlee.chatsim.domain.model.AttachmentInfo
import io.github.kylinlee.chatsim.common.extensions.getFileSizeFromUri
import io.github.kylinlee.chatsim.common.extensions.isGifMimeType
import io.github.kylinlee.chatsim.common.extensions.isImageMimeType
import io.github.kylinlee.chatsim.data.legacy.AttachmentUtils
import io.github.kylinlee.chatsim.data.legacy.parseVCardFromUri
import io.github.kylinlee.chatsim.data.model.Attachment
import io.github.kylinlee.chatsim.repository.AttachmentRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import ezvcard.VCard
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class AttachmentRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AttachmentRepository {
    override suspend fun getAttachmentInfo(uri: Uri): AttachmentInfo? = withContext(ioDispatcher) {
        val mimeType = context.contentResolver.getType(uri) ?: return@withContext null
        val size = runCatching { context.getFileSizeFromUri(uri) }.getOrDefault(0L)

        AttachmentInfo(
            uri = uri,
            filename = context.getFilenameFromUri(uri),
            mimeType = mimeType,
            size = size,
            isImage = mimeType.isImageMimeType(),
            isGif = mimeType.isGifMimeType(),
            isVCard = mimeType.contains("vcard", ignoreCase = true),
        )
    }

    override fun createAttachment(uri: Uri, messageId: Long, info: AttachmentInfo?): Attachment? {
        val mimeType = info?.mimeType ?: context.contentResolver.getType(uri) ?: return null
        val filename = info?.filename ?: context.getFilenameFromUri(uri)
        return Attachment(
            id = null,
            messageId = messageId,
            uriString = uri.toString(),
            mimetype = mimeType,
            width = 0,
            height = 0,
            filename = filename,
        )
    }

    override suspend fun saveAttachment(sourceUri: Uri, destinationUri: Uri): Boolean = withContext(ioDispatcher) {
        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(destinationUri, takeFlags) }

        try {
            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                context.contentResolver.openOutputStream(destinationUri, "rwt")?.use { outputStream ->
                    inputStream.copyTo(outputStream)
                    outputStream.flush()
                }
            } ?: return@withContext false
            true
        } catch (e: Exception) {
            false
        }
    }

    override fun getAttachmentsDir(): File = File(context.cacheDir, "attachments").apply {
        if (!exists()) {
            mkdirs()
        }
    }

    override fun createTempAttachmentFile(prefix: String, suffix: String): File =
        File.createTempFile(prefix, suffix, getAttachmentsDir())

    override suspend fun parseVCard(uri: Uri): List<VCard> = suspendCancellableCoroutine { continuation ->
        parseVCardFromUri(context, uri) { vCards ->
            if (continuation.isActive) {
                continuation.resume(vCards)
            }
        }
    }

    override fun parseAttachmentNames(smilText: String): List<String> =
        runCatching { AttachmentUtils.parseAttachmentNames(smilText) }.getOrDefault(emptyList())
}

package io.github.kylinlee.chatsim.background


import io.github.kylinlee.chatsim.common.*
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Scale
import coil3.toBitmap
import com.klinker.android.send_message.MmsReceivedReceiver
import kotlinx.coroutines.runBlocking
import io.github.kylinlee.chatsim.common.extensions.normalizePhoneNumber
import io.github.kylinlee.chatsim.common.extensions.showErrorToast
import io.github.kylinlee.chatsim.common.ensureBackgroundThread
import io.github.kylinlee.chatsim.R
import io.github.kylinlee.chatsim.common.extensions.conversationsDB
import io.github.kylinlee.chatsim.common.extensions.messagesDB
import io.github.kylinlee.chatsim.common.extensions.getNameFromAddress
import io.github.kylinlee.chatsim.common.extensions.getLatestMMS
import io.github.kylinlee.chatsim.common.extensions.showReceivedMessageNotification
import io.github.kylinlee.chatsim.common.extensions.updateUnreadCountBadge
import io.github.kylinlee.chatsim.common.extensions.upsertIncomingConversation
import io.github.kylinlee.chatsim.common.refreshMessages
import io.github.kylinlee.chatsim.data.rules.IncomingRuleExecutor
import io.github.kylinlee.chatsim.data.rules.UserRuleExecutor
import io.github.kylinlee.chatsim.domain.rule.IncomingKind
import io.github.kylinlee.chatsim.domain.rule.RuleDecision
import io.github.kylinlee.chatsim.domain.rule.user.RecordKind
import io.github.kylinlee.chatsim.domain.rule.user.RuleRecord
import io.github.kylinlee.chatsim.domain.rule.user.SmsDirection

// more info at https://github.com/klinker41/android-smsmms
class MmsReceiver : MmsReceivedReceiver() {

    override fun isAddressBlocked(context: Context, address: String): Boolean {
        val normalizedAddress = address.normalizePhoneNumber()
        return IncomingRuleExecutor.isBlocked(context, IncomingKind.MMS, normalizedAddress)
    }

    override fun onMessageReceived(context: Context, messageUri: Uri) {
        val mms = context.getLatestMMS() ?: return
        val address = mms.getSender()?.phoneNumbers?.first()?.normalizedNumber ?: ""

        val size = context.resources.getDimension(R.dimen.notification_large_icon_size).toInt()
        ensureBackgroundThread {
            val bitmap = try {
                runBlocking {
                    context.imageLoader.execute(
                        ImageRequest.Builder(context)
                            .data(mms.attachment!!.attachments.first().getUri())
                            .size(size)
                            .scale(Scale.FILL)
                            .allowHardware(false)
                            .build()
                    ).image?.toBitmap()
                }
            } catch (e: Exception) {
                null
            }

            Handler(Looper.getMainLooper()).post {
                ensureBackgroundThread {
                    val outcome = IncomingRuleExecutor.evaluateBuiltin(context, IncomingKind.MMS, address, mms.subscriptionId)
                    if (outcome.decision == RuleDecision.BLOCK) return@ensureBackgroundThread

                    val resolved = outcome.resolution ?: return@ensureBackgroundThread
                    val senderName = context.getNameFromAddress(resolved.peerNumber)

                    val ruleRecord = RuleRecord(
                        address = resolved.peerNumber,
                        roleId = resolved.role.id,
                        body = mms.body,
                        kind = RecordKind.SMS,
                        smsDirection = SmsDirection.INBOX,
                        timestamp = mms.millis(),
                    )
                    val ruleOutcome = UserRuleExecutor.evaluate(context, ruleRecord)
                    if (ruleOutcome.blocked) {
                        UserRuleExecutor.recordBlockedHits(context, ruleOutcome, ruleRecord, senderName)
                        return@ensureBackgroundThread
                    }

                    val conversationId = context.upsertIncomingConversation(
                        roleId = resolved.role.id,
                        peerNumber = resolved.peerNumber,
                        body = mms.body,
                        date = mms.date,
                        title = senderName,
                        photoUri = "",
                    )
                    context.messagesDB.insertOrUpdate(mms.copy(conversationId = conversationId))
                    UserRuleExecutor.applyToStored(context, mms.id, ruleOutcome, ruleRecord, senderName)
                    context.showReceivedMessageNotification(mms.id, resolved.peerNumber, mms.body, conversationId, resolved.role.id, bitmap, resolved.role.label)
                    context.updateUnreadCountBadge(context.conversationsDB.getUnreadConversations())
                    refreshMessages()
                }
            }
        }
    }

    override fun onError(context: Context, error: String) = context.showErrorToast(context.getString(R.string.couldnt_download_mms))
}

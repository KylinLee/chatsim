package io.github.kylinlee.chatsim.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import io.github.kylinlee.chatsim.common.extensions.config
import io.github.kylinlee.chatsim.common.extensions.conversationsDB
import io.github.kylinlee.chatsim.common.extensions.getNotificationBitmap
import io.github.kylinlee.chatsim.common.extensions.getNameFromAddress
import io.github.kylinlee.chatsim.common.extensions.getThreadId
import io.github.kylinlee.chatsim.common.extensions.insertNewSMS
import io.github.kylinlee.chatsim.common.extensions.messagesDB
import io.github.kylinlee.chatsim.common.extensions.showReceivedMessageNotification
import io.github.kylinlee.chatsim.common.extensions.updateUnreadCountBadge
import io.github.kylinlee.chatsim.common.extensions.upsertIncomingConversation
import io.github.kylinlee.chatsim.common.refreshMessages
import io.github.kylinlee.chatsim.data.contacts.SimpleContactsHelper
import io.github.kylinlee.chatsim.data.model.Message
import io.github.kylinlee.chatsim.data.rules.IncomingRuleExecutor
import io.github.kylinlee.chatsim.data.rules.UserRuleExecutor
import io.github.kylinlee.chatsim.domain.model.PhoneNumber
import io.github.kylinlee.chatsim.domain.model.SimpleContact
import io.github.kylinlee.chatsim.domain.rule.IncomingKind
import io.github.kylinlee.chatsim.domain.rule.RuleDecision
import io.github.kylinlee.chatsim.domain.rule.user.RecordKind
import io.github.kylinlee.chatsim.domain.rule.user.RuleRecord
import io.github.kylinlee.chatsim.domain.rule.user.SmsDirection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 接收短信。`onReceive` 返回后进程随时可能被回收（尤其是应用被划掉后），
 * 必须用 `goAsync` 持有广播，直到系统库/本地库写入与通知全部完成，
 * 否则短信会在处理中途丢失。
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext

        CoroutineScope(Dispatchers.IO).launch {
            try {
                handleSms(appContext, intent)
            } catch (throwable: Throwable) {
                Log.w(TAG, "failed to handle incoming sms", throwable)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun handleSms(context: Context, intent: Intent) {
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        var address = ""
        var body = ""
        var subject = ""
        var date = 0L
        var threadId = 0L
        var status = Telephony.Sms.STATUS_NONE
        val type = Telephony.Sms.MESSAGE_TYPE_INBOX
        val read = 0
        val subscriptionId = intent.getIntExtra("subscription", -1)

        messages.forEach {
            address = it.originatingAddress ?: ""
            subject = it.pseudoSubject
            status = it.status
            body += it.messageBody
            date = System.currentTimeMillis()
            threadId = context.getThreadId(address)
        }

        if (context.config.blockUnknownNumbers && !context.isKnownContact(address)) {
            return
        }

        handleMessage(context, address, subject, body, date, read, threadId, type, subscriptionId, status)
    }

    /** 联系人查询回调在其它线程执行，带超时等待；超时按已保存处理，避免误丢短信。 */
    private fun Context.isKnownContact(address: String): Boolean {
        val latch = CountDownLatch(1)
        val known = AtomicBoolean(false)
        SimpleContactsHelper(this).exists(address) { exists ->
            known.set(exists)
            latch.countDown()
        }
        latch.await(CONTACT_LOOKUP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        return known.get()
    }

    private fun handleMessage(
        context: Context,
        address: String,
        subject: String,
        body: String,
        date: Long,
        read: Int,
        threadId: Long,
        type: Int,
        subscriptionId: Int,
        status: Int,
    ) {
        val photoUri = SimpleContactsHelper(context).getPhotoUriFromPhoneNumber(address)
        val bitmap = context.getNotificationBitmap(photoUri)

        val outcome = IncomingRuleExecutor.evaluateBuiltin(context, IncomingKind.SMS, address, subscriptionId)
        if (outcome.decision == RuleDecision.BLOCK) return

        val resolved = outcome.resolution ?: return
        val peerNumber = resolved.peerNumber
        val senderName = context.getNameFromAddress(peerNumber)

        val ruleRecord = RuleRecord(
            address = peerNumber,
            roleId = resolved.role.id,
            body = body,
            kind = RecordKind.SMS,
            smsDirection = SmsDirection.INBOX,
            timestamp = date,
        )
        val ruleOutcome = UserRuleExecutor.evaluate(context, ruleRecord)
        if (ruleOutcome.blocked) {
            UserRuleExecutor.recordBlockedHits(context, ruleOutcome, ruleRecord, senderName)
            return
        }

        val newMessageId = context.insertNewSMS(address, subject, body, date, read, threadId, type, subscriptionId)

        val conversationId = context.upsertIncomingConversation(
            roleId = resolved.role.id,
            peerNumber = peerNumber,
            body = body,
            date = (date / 1000).toInt(),
            title = senderName,
            photoUri = photoUri,
        )

        try {
            context.updateUnreadCountBadge(context.conversationsDB.getUnreadConversations())
        } catch (ignored: Exception) {
        }

        val phoneNumber = PhoneNumber(peerNumber, 0, "", peerNumber)
        val participant = SimpleContact(0, 0, senderName, photoUri, arrayListOf(phoneNumber), ArrayList(), ArrayList())
        val participants = arrayListOf(participant)
        val messageDate = (date / 1000).toInt()

        val message =
            Message(
                newMessageId,
                body,
                type,
                status,
                participants,
                messageDate,
                false,
                threadId,
                false,
                null,
                peerNumber,
                senderName,
                photoUri,
                subscriptionId,
                false,
                conversationId
            )
        context.messagesDB.insertOrUpdate(message)
        UserRuleExecutor.applyToStored(context, message.id, ruleOutcome, ruleRecord, senderName)
        refreshMessages()
        context.showReceivedMessageNotification(newMessageId, peerNumber, body, conversationId, resolved.role.id, bitmap, resolved.role.label)
    }

    private companion object {
        const val TAG = "SmsReceiver"
        const val CONTACT_LOOKUP_TIMEOUT_SECONDS = 5L
    }
}

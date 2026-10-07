package io.github.kylinlee.chatsim.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.telecom.PhoneAccountHandle
import android.telephony.SmsManager
import io.github.kylinlee.chatsim.common.extensions.areMultipleSIMsAvailable
import io.github.kylinlee.chatsim.common.extensions.getAvailableSIMCardLabels
import io.github.kylinlee.chatsim.data.model.SIMAccount
import io.github.kylinlee.chatsim.common.extensions.subscriptionManagerCompat
import io.github.kylinlee.chatsim.data.local.Config
import io.github.kylinlee.chatsim.data.model.Message
import io.github.kylinlee.chatsim.data.model.SIMCard
import io.github.kylinlee.chatsim.repository.SimRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SimRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : SimRepository {
    private val config = Config(context)

    override fun getAvailableSimAccounts(): List<SIMAccount> = context.getAvailableSIMCardLabels()

    @SuppressLint("MissingPermission")
    override fun getActiveSubscriptions(): List<SIMCard> {
        val subscriptions = context.subscriptionManagerCompat().activeSubscriptionInfoList ?: return emptyList()
        return subscriptions.mapIndexed { index, subscriptionInfo ->
            var label = subscriptionInfo.displayName?.toString() ?: ""
            if (subscriptionInfo.number?.isNotEmpty() == true) {
                label += " (${subscriptionInfo.number})"
            }
            SIMCard(index + 1, subscriptionInfo.subscriptionId, label)
        }
    }

    override fun areMultipleSimsAvailable(): Boolean = context.areMultipleSIMsAvailable()

    override fun getDefaultSmsSubscriptionId(): Int = SmsManager.getDefaultSmsSubscriptionId()

    override fun getPreferredSimSubscriptionId(number: String): Int = config.getUseSIMIdAtNumber(number)

    override fun savePreferredSimForNumber(number: String, subscriptionId: Int) {
        config.saveUseSIMIdAtNumber(number, subscriptionId)
    }

    override fun getProperSimIndex(
        availableSubscriptions: List<SIMCard>,
        numbers: List<String>,
        lastMessage: Message?,
    ): Int {
        if (availableSubscriptions.isEmpty()) return 0

        val firstNumber = numbers.firstOrNull() ?: return 0
        val userPreferredSimId = config.getUseSIMIdAtNumber(firstNumber)
        val userPreferredSimIdx = availableSubscriptions.indexOfFirst { it.subscriptionId == userPreferredSimId }
            .takeIf { it >= 0 }

        val senderPreferredSimIdx = if (lastMessage?.isReceivedMessage() == true) {
            availableSubscriptions.indexOfFirst { it.subscriptionId == lastMessage.subscriptionId }
                .takeIf { it >= 0 }
        } else {
            null
        }

        val defaultSmsSubscriptionId = SmsManager.getDefaultSmsSubscriptionId()
        val systemPreferredSimIdx = if (defaultSmsSubscriptionId >= 0) {
            availableSubscriptions.indexOfFirst { it.subscriptionId == defaultSmsSubscriptionId }
                .takeIf { it >= 0 }
        } else {
            null
        }

        return userPreferredSimIdx ?: senderPreferredSimIdx ?: systemPreferredSimIdx ?: 0
    }

    override fun getCustomCallSim(number: String): PhoneAccountHandle? = config.getCustomSIM(number)

    override fun saveCustomCallSim(number: String, handle: PhoneAccountHandle) {
        config.saveCustomSIM(number, handle)
    }

    override fun removeCustomCallSim(number: String) {
        config.removeCustomSIM(number)
    }

    override fun getPhoneAccountHandleForSubscription(subscriptionId: Int?): PhoneAccountHandle? {
        if (subscriptionId == null) return null

        val simCard = getActiveSubscriptions().firstOrNull { it.subscriptionId == subscriptionId } ?: return null
        return getAvailableSimAccounts().firstOrNull { it.id == simCard.id }?.handle
    }
}

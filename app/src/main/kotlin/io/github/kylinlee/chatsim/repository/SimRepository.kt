package io.github.kylinlee.chatsim.repository

import android.telecom.PhoneAccountHandle
import io.github.kylinlee.chatsim.data.model.SIMAccount
import io.github.kylinlee.chatsim.data.model.Message
import io.github.kylinlee.chatsim.data.model.SIMCard

interface SimRepository {
    fun getAvailableSimAccounts(): List<SIMAccount>

    fun getActiveSubscriptions(): List<SIMCard>

    fun areMultipleSimsAvailable(): Boolean

    fun getDefaultSmsSubscriptionId(): Int

    fun getPreferredSimSubscriptionId(number: String): Int

    fun savePreferredSimForNumber(number: String, subscriptionId: Int)

    fun getProperSimIndex(
        availableSubscriptions: List<SIMCard>,
        numbers: List<String>,
        lastMessage: Message?,
    ): Int

    fun getCustomCallSim(number: String): PhoneAccountHandle?

    fun saveCustomCallSim(number: String, handle: PhoneAccountHandle)

    fun removeCustomCallSim(number: String)

    fun getPhoneAccountHandleForSubscription(subscriptionId: Int?): PhoneAccountHandle?
}

package io.github.kylinlee.chatsim.repository

import io.github.kylinlee.chatsim.domain.model.Role

interface MessagingRepository {
    suspend fun sendMessage(
        text: String,
        addresses: List<String>,
        subscriptionId: Int?,
        messageId: Long? = null,
        applyRole: Boolean = true,
        role: Role? = null,
    )
}

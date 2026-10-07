package io.github.kylinlee.chatsim.data.model

import kotlinx.serialization.Serializable

@Serializable
data class PhoneAccountHandleModel(
    val packageName: String,
    val className: String,
    val id: String
)

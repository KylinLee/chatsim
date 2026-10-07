package io.github.kylinlee.chatsim.domain.model.contacts

import kotlinx.serialization.Serializable

@Serializable
data class Event(var value: String, var type: Int)

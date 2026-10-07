package io.github.kylinlee.chatsim.domain.model.contacts

import kotlinx.serialization.Serializable

@Serializable
data class Group(
    val id: Long,
    val title: String,
)

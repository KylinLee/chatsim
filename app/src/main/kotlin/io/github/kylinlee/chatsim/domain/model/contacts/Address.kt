package io.github.kylinlee.chatsim.domain.model.contacts

import kotlinx.serialization.Serializable

@Serializable
data class Address(var value: String, var type: Int, var label: String)

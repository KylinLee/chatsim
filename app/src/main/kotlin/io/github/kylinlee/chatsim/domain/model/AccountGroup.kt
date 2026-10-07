package io.github.kylinlee.chatsim.domain.model

import io.github.kylinlee.chatsim.domain.model.contacts.Group

data class AccountGroup(
    val group: Group,
    val accountName: String,
    val accountType: String,
)

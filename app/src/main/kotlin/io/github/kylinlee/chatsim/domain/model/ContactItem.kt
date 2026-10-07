package io.github.kylinlee.chatsim.domain.model

import io.github.kylinlee.chatsim.domain.model.contacts.Contact

data class ContactItem(
    val contact: Contact,
    val isPinned: Boolean,
    val lastContactTime: Long,
)

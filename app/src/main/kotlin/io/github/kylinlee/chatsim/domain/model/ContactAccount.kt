package io.github.kylinlee.chatsim.domain.model

import io.github.kylinlee.chatsim.domain.model.contacts.ContactSource

/** An account the aggregated contact can belong to, together with the groups of its raw contact. */
data class ContactAccount(
    val source: ContactSource,
    val rawContactId: Int,
    val groupIds: Set<Long>,
) {
    val exists: Boolean
        get() = rawContactId != 0
}

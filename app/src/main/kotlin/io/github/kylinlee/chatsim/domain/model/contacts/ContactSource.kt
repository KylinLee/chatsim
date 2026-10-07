package io.github.kylinlee.chatsim.domain.model.contacts

import io.github.kylinlee.chatsim.common.SMT_PRIVATE

data class ContactSource(
    var name: String,
    var type: String,
    var publicName: String,
    var count: Int = 0,
    val isLocal: Boolean = false,
) {
    fun getFullIdentifier(): String = if (type == SMT_PRIVATE) type else "$name:$type"
}

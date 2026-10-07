package io.github.kylinlee.chatsim.data.model

/** 系统 BlockedNumberContract 里的一个拦截号码。 */
data class BlockedNumber(
    val id: Long,
    val number: String,
    val normalizedNumber: String,
    val numberToCompare: String,
    val contactName: String? = null,
)

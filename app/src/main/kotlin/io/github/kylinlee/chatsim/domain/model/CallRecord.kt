package io.github.kylinlee.chatsim.domain.model

data class CallRecord(
    val id: Int,
    val phoneNumber: String,
    val displayNumber: String,
    val name: String,
    val photoUri: String,
    val startTS: Int,
    val duration: Int,
    val type: Int,
    val simID: Int,
    val neighbourIDs: List<Int> = emptyList(),
    val role: Role? = null,
    val specificNumber: String = "",
    val specificType: String = "",
    val isUnknownNumber: Boolean = false,
) {
    val groupedCount: Int
        get() = neighbourIDs.size + 1
}

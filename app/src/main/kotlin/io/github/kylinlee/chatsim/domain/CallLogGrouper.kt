package io.github.kylinlee.chatsim.domain

import io.github.kylinlee.chatsim.domain.model.CallRecord

object CallLogGrouper {
    fun groupingKey(call: CallRecord): String = "${call.phoneNumber}|${call.name}|${call.simID}"

    fun group(calls: List<CallRecord>): List<CallRecord> {
        if (calls.size < 2) return calls

        val result = mutableListOf<CallRecord>()
        var previousKey: String? = null

        calls.forEach { call ->
            val key = groupingKey(call)
            if (key == previousKey && result.isNotEmpty()) {
                val last = result.last()
                result[result.lastIndex] = last.copy(neighbourIDs = last.neighbourIDs + call.id)
            } else {
                result.add(call)
            }
            previousKey = key
        }

        return result
    }
}

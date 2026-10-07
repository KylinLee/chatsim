package io.github.kylinlee.chatsim.data.model

import io.github.kylinlee.chatsim.domain.model.CallRecord
import io.github.kylinlee.chatsim.domain.model.Role

/** Stores a call log entry as a messages row; the negative id keeps it apart from real messages. */
fun CallRecord.toMessage(conversationId: Long): Message = Message(
    id = -id.toLong(),
    body = "",
    type = 0,
    status = 0,
    participants = ArrayList(),
    date = startTS,
    read = true,
    threadId = 0,
    isMMS = false,
    attachment = null,
    senderPhoneNumber = displayNumber,
    senderName = name,
    senderPhotoUri = photoUri,
    subscriptionId = 0,
    isScheduled = false,
    conversationId = conversationId,
    isCall = true,
    callType = type,
    callDuration = duration,
    simId = simID,
)

fun Message.toCallRecord(role: Role?): CallRecord = CallRecord(
    id = (-id).toInt(),
    phoneNumber = senderPhoneNumber,
    displayNumber = senderPhoneNumber,
    name = senderName,
    photoUri = senderPhotoUri,
    startTS = date,
    duration = callDuration,
    type = callType,
    simID = simId,
    role = role,
)

package io.github.kylinlee.chatsim.ui.thread

import android.provider.CallLog
import android.provider.Telephony
import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.res.painterResource
import io.github.kylinlee.chatsim.common.extensions.toast
import io.github.kylinlee.chatsim.R
import io.github.kylinlee.chatsim.domain.model.CallRecord
import io.github.kylinlee.chatsim.data.model.Message
import io.github.kylinlee.chatsim.domain.model.TimelineItem
import io.github.kylinlee.chatsim.ui.common.AppMenuItem
import io.github.kylinlee.chatsim.ui.common.PressAnchorMenu
import io.github.kylinlee.chatsim.ui.common.callStatusLabel
import io.github.kylinlee.chatsim.ui.common.trackPressOffset
import io.github.kylinlee.chatsim.ui.theme.SmsMessengerTheme
import io.github.kylinlee.chatsim.ui.common.formatDateHeader
import io.github.kylinlee.chatsim.ui.common.formatDuration
import io.github.kylinlee.chatsim.ui.common.formatMessageTime
import io.github.kylinlee.chatsim.ui.common.isMissedCallType
import java.util.Calendar
import java.util.Date

@Composable
fun TimelineList(
    items: List<TimelineItem>,
    onLoadOlder: () -> Unit,
    onCallClick: (CallRecord) -> Unit,
    onDeleteMessage: (Message) -> Unit,
    modifier: Modifier = Modifier,
    showDeliveryStatus: Boolean = false,
    bottomContentPadding: Dp = 0.dp,
) {
    val reversedItems = remember(items) { items.asReversed() }
    val listState = rememberLazyListState()
    val newestItem = reversedItems.firstOrNull()

    LaunchedEffect(newestItem?.key) {
        if (newestItem == null) return@LaunchedEffect

        val isOutgoingMessage = (newestItem as? TimelineItem.MessageItem)?.message?.isReceivedMessage() == false
        if (isOutgoingMessage || listState.firstVisibleItemIndex <= 2) {
            listState.animateScrollToItem(0)
        }
    }

    LaunchedEffect(listState, reversedItems.size) {
        if (reversedItems.isEmpty()) return@LaunchedEffect

        androidx.compose.runtime.snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        }.collect { lastVisibleIndex ->
            if (lastVisibleIndex >= reversedItems.lastIndex - 2) {
                onLoadOlder()
            }
        }
    }

    LazyColumn(
        state = listState,
        reverseLayout = true,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = 8.dp,
            bottom = 8.dp + bottomContentPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(
            items = reversedItems,
            key = { _, item -> item.key },
        ) { index, item ->
            val olderItem = reversedItems.getOrNull(index + 1)
            Column(modifier = Modifier.fillMaxWidth()) {
                if (shouldShowDateHeader(item, olderItem)) {
                    DateHeader(timestamp = item.timestamp)
                }

                when (item) {
                    is TimelineItem.MessageItem -> MessageBubble(
                        message = item.message,
                        showDeliveryStatus = showDeliveryStatus,
                        onDelete = { onDeleteMessage(item.message) },
                    )

                    is TimelineItem.CallItem -> CallTimelineItem(
                        call = item.call,
                        onClick = { onCallClick(item.call) },
                    )
                }
            }
        }
    }
}

private fun shouldShowDateHeader(item: TimelineItem, olderItem: TimelineItem?): Boolean {
    if (olderItem == null) return true
    return !isSameDay(item.timestamp, olderItem.timestamp)
}

private fun isSameDay(firstSeconds: Long, secondSeconds: Long): Boolean {
    val first = Calendar.getInstance().apply { time = Date(firstSeconds * 1000) }
    val second = Calendar.getInstance().apply { time = Date(secondSeconds * 1000) }
    return first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
        first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
}

@Composable
private fun DateHeader(timestamp: Long) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = formatDateHeader(timestamp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: Message,
    showDeliveryStatus: Boolean,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isOutgoing = !message.isReceivedMessage()
    var menuExpanded by remember { mutableStateOf(false) }
    var showTextSelection by remember { mutableStateOf(false) }
    var pressOffset by remember { mutableStateOf(Offset.Zero) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start,
    ) {
        Box {
            Column(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (isOutgoing) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                    .trackPressOffset { pressOffset = it }
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { menuExpanded = true },
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                if (message.attachment != null) {
                    Text(
                        text = stringResource(R.string.attachment_indicator),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (message.body.isNotBlank()) {
                    Text(
                        text = message.body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isOutgoing) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (message.isScheduled) {
                        Icon(
                            painter = painterResource(R.drawable.ic_symbol_schedule),
                            contentDescription = stringResource(R.string.scheduled_message_indicator),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                    }

                    Text(
                        text = formatMessageTime(message.date.toLong()),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (isOutgoing && (!message.isScheduled || message.status == Telephony.Sms.STATUS_FAILED)) {
                        Spacer(Modifier.width(4.dp))
                        SendStatusIcon(state = message.sendState())
                        if (showDeliveryStatus && !message.isMMS && !message.isScheduled) {
                            Spacer(Modifier.width(2.dp))
                            DeliveryStatusIcon(state = message.deliveryState())
                        }
                    }
                }
            }

            PressAnchorMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                pressOffset = pressOffset,
            ) {
                AppMenuItem(
                    text = stringResource(R.string.copy_message),
                    onClick = {
                        clipboardManager.setText(AnnotatedString(message.body))
                        context.toast(id = R.string.copied_to_clipboard)
                        menuExpanded = false
                    },
                    index = 0,
                    count = 3,
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_symbol_content_copy_w300),
                            contentDescription = null,
                        )
                    },
                )
                AppMenuItem(
                    text = stringResource(R.string.select_text),
                    onClick = {
                        menuExpanded = false
                        showTextSelection = true
                    },
                    index = 1,
                    count = 3,
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_symbol_crop_w300),
                            contentDescription = null,
                        )
                    },
                )
                AppMenuItem(
                    text = stringResource(R.string.delete_message),
                    onClick = {
                        menuExpanded = false
                        onDelete()
                    },
                    index = 2,
                    count = 3,
                    colors = MenuDefaults.itemColors(
                        textColor = MaterialTheme.colorScheme.error,
                        leadingIconColor = MaterialTheme.colorScheme.error,
                    ),
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_symbol_delete_w300),
                            contentDescription = null,
                        )
                    },
                )
            }
        }
    }

    if (showTextSelection) {
        AlertDialog(
            onDismissRequest = { showTextSelection = false },
            title = { Text(stringResource(R.string.select_text)) },
            text = {
                SelectionContainer {
                    Text(
                        text = message.body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showTextSelection = false }) {
                    Text(stringResource(io.github.kylinlee.chatsim.R.string.close))
                }
            },
        )
    }
}

private enum class MessageSendState {
    PENDING,
    SENT,
    FAILED,
}

private enum class MessageDeliveryState {
    PENDING,
    DELIVERED,
    FAILED,
}

/** 本机是否发出：优先看消息类型（系统库同步），定时消息以状态判断。 */
private fun Message.sendState(): MessageSendState = when {
    isScheduled -> if (status == Telephony.Sms.STATUS_FAILED) MessageSendState.FAILED else MessageSendState.PENDING
    type == Telephony.Sms.MESSAGE_TYPE_FAILED -> MessageSendState.FAILED
    type == Telephony.Sms.MESSAGE_TYPE_SENT -> MessageSendState.SENT
    type == Telephony.Sms.MESSAGE_TYPE_OUTBOX || type == Telephony.Sms.MESSAGE_TYPE_QUEUED -> MessageSendState.PENDING
    status == Telephony.Sms.STATUS_FAILED -> MessageSendState.FAILED
    status == Telephony.Sms.STATUS_PENDING -> MessageSendState.PENDING
    else -> MessageSendState.SENT
}

private fun Message.deliveryState(): MessageDeliveryState = when (deliveryStatus) {
    Telephony.Sms.STATUS_COMPLETE -> MessageDeliveryState.DELIVERED
    Telephony.Sms.STATUS_FAILED -> MessageDeliveryState.FAILED
    else -> MessageDeliveryState.PENDING
}

@Composable
private fun SendStatusIcon(state: MessageSendState) {
    when (state) {
        MessageSendState.FAILED -> StatusIcon(R.drawable.ic_symbol_error, MaterialTheme.colorScheme.error)
        MessageSendState.PENDING -> StatusIcon(R.drawable.ic_symbol_schedule, MaterialTheme.colorScheme.onSurfaceVariant)
        MessageSendState.SENT -> StatusIcon(R.drawable.ic_symbol_done, MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DeliveryStatusIcon(state: MessageDeliveryState) {
    when (state) {
        MessageDeliveryState.FAILED -> StatusIcon(R.drawable.ic_symbol_error, MaterialTheme.colorScheme.error)
        MessageDeliveryState.PENDING -> StatusIcon(R.drawable.ic_symbol_schedule, MaterialTheme.colorScheme.onSurfaceVariant)
        MessageDeliveryState.DELIVERED -> StatusIcon(R.drawable.ic_symbol_done, MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatusIcon(@DrawableRes drawableRes: Int, tint: Color) {
    Icon(
        painter = painterResource(drawableRes),
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(13.dp),
    )
}

@Composable
private fun CallTimelineItem(
    call: CallRecord,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isOutgoing = call.type == CallLog.Calls.OUTGOING_TYPE
    val missed = isMissedCallType(call.type)
    val containerColor = when {
        isOutgoing -> MaterialTheme.colorScheme.primaryContainer
        missed -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        isOutgoing -> MaterialTheme.colorScheme.onPrimaryContainer
        missed -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start,
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(containerColor)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = if (missed) painterResource(R.drawable.ic_symbol_call_missed) else painterResource(R.drawable.ic_symbol_call),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = stringResource(callStatusLabel(call.type, call.duration)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (call.duration > 0) {
                        Text(
                            text = stringResource(R.string.call_duration_with_value, formatDuration(call.duration)) + " · ",
                            style = MaterialTheme.typography.labelSmall,
                            color = contentColor.copy(alpha = 0.75f),
                        )
                    }
                    Text(
                        text = formatMessageTime(call.startTS.toLong()),
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.75f),
                    )
                }
            }
        }
    }
}

@Preview(name = "Phone", device = Devices.PHONE, showBackground = true)
@Preview(name = "Foldable", device = Devices.FOLDABLE, showBackground = true)
@Preview(name = "Tablet", device = Devices.TABLET, showBackground = true)
@Composable
private fun TimelineListPreview() {
    SmsMessengerTheme {
        TimelineList(
            items = listOf(
                TimelineItem.MessageItem(message = previewMessage(1L, "你好，这是收到的消息", received = true)),
                TimelineItem.CallItem(
                    call = CallRecord(
                        id = 1,
                        phoneNumber = "13800000000",
                        displayNumber = "13800000000",
                        name = "张三",
                        photoUri = "",
                        startTS = (System.currentTimeMillis() / 1000).toInt() - 60,
                        duration = 65,
                        type = android.provider.CallLog.Calls.OUTGOING_TYPE,
                        simID = 0,
                    )
                ),
                TimelineItem.MessageItem(message = previewMessage(2L, "这是发出的消息", received = false)),
            ),
            onLoadOlder = {},
            onCallClick = {},
            onDeleteMessage = {},
        )
    }
}

private fun previewMessage(id: Long, body: String, received: Boolean): Message = Message(
    id = id,
    body = body,
    type = if (received) Telephony.Sms.MESSAGE_TYPE_INBOX else Telephony.Sms.MESSAGE_TYPE_SENT,
    status = Telephony.Sms.STATUS_COMPLETE,
    participants = ArrayList(),
    date = (System.currentTimeMillis() / 1000).toInt(),
    read = true,
    threadId = 1L,
    isMMS = false,
    attachment = null,
    senderPhoneNumber = "13800000000",
    senderName = "张三",
    senderPhotoUri = "",
    subscriptionId = 0,
)
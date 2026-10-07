package io.github.kylinlee.chatsim.ui.conversations

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import io.github.kylinlee.chatsim.domain.model.ConversationThread
import io.github.kylinlee.chatsim.domain.model.Role
import io.github.kylinlee.chatsim.ui.common.callStatusLabel
import io.github.kylinlee.chatsim.ui.common.formatDuration
import io.github.kylinlee.chatsim.ui.common.formatTimestamp
import io.github.kylinlee.chatsim.ui.components.ConversationAvatar
import io.github.kylinlee.chatsim.ui.theme.SmsMessengerTheme
import io.github.kylinlee.chatsim.R

@Composable
fun ConversationRow(
    thread: ConversationThread,
    onClick: () -> Unit,
    isSelected: Boolean = false,
    showNumberSuffix: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val isUnread = !thread.read
    val lastCall = thread.lastCall
    val callIsLatest = lastCall != null && lastCall.startTS.toLong() >= thread.date

    val displayTitle = if (showNumberSuffix && thread.peerNumber.length >= 4) {
        "${thread.title} · ${thread.peerNumber.takeLast(4)}"
    } else {
        thread.title
    }

    val callLabel = lastCall?.let { record ->
        val label = stringResource(callStatusLabel(record.type, record.duration))
        if (record.duration > 0) "$label · ${formatDuration(record.duration)}" else label
    }

    val snippet = when {
        callIsLatest && callLabel != null -> callLabel
        thread.snippet.isNotBlank() -> thread.snippet
        callLabel != null -> callLabel
        else -> thread.snippet
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ConversationAvatar(
            title = thread.title,
            photoUri = thread.photoUri,
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )

                if (thread.isPinned) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        painter = painterResource(R.drawable.ic_symbol_push_pin),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }

            Spacer(Modifier.padding(top = 3.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = snippet,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                Spacer(Modifier.width(8.dp))

                Text(
                    text = formatTimestamp(thread.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isUnread) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }

        if (isUnread) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Preview(name = "Phone", device = Devices.PHONE, showBackground = true)
@Preview(name = "Foldable", device = Devices.FOLDABLE, showBackground = true)
@Preview(name = "Tablet", device = Devices.TABLET, showBackground = true)
@Composable
private fun ConversationRowPreview() {
    SmsMessengerTheme {
        ConversationRow(
            thread = ConversationThread(
                conversationId = 1L,
                roleId = 1L,
                role = Role(id = 1, label = "工作", smsPrefix = "12520", callPrefix = "17951"),
                peerNumber = "13800000000",
                title = "张三",
                photoUri = "",
                snippet = "你好，这是一条测试消息",
                date = System.currentTimeMillis() / 1000,
                read = false,
                isGroupConversation = false,
                isScheduled = false,
                isPinned = true,
                isAvailable = true,
            ),
            onClick = {},
        )
    }
}

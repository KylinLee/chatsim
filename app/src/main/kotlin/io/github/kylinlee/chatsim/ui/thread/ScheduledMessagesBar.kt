package io.github.kylinlee.chatsim.ui.thread

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.kylinlee.chatsim.R
import io.github.kylinlee.chatsim.data.model.Message
import io.github.kylinlee.chatsim.ui.common.formatDateTime

/**
 * 输入栏上方的定时消息胶囊：宽度跟随第一行时间，第二行内容按该宽度截断；
 * 多条定时消息时右侧按钮切换为 more_up（打开详情），单条时为删除。
 */
@Composable
fun ScheduledMessagesBar(
    messages: List<Message>,
    onCancel: (Long) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val message = messages.firstOrNull() ?: return
    val timeText = stringResource(R.string.scheduled_at, formatDateTime(message.date.toLong()))
    val timeStyle = MaterialTheme.typography.labelSmall
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val timeWidth = remember(timeText, timeStyle, density) {
        with(density) { textMeasurer.measure(timeText, timeStyle).size.width.toDp() }
    }

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp,
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClick) {
                Icon(
                    painter = painterResource(R.drawable.ic_symbol_schedule),
                    contentDescription = stringResource(R.string.scheduled_message),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(modifier = Modifier.width(timeWidth)) {
                Text(
                    text = timeText,
                    style = timeStyle,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
                if (message.body.isNotBlank()) {
                    Text(
                        text = message.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (messages.size > 1) {
                IconButton(onClick = onClick) {
                    Icon(
                        painter = painterResource(R.drawable.ic_symbol_more_up),
                        contentDescription = stringResource(R.string.scheduled_message),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            } else {
                IconButton(onClick = { onCancel(message.id) }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_symbol_delete),
                        contentDescription = stringResource(R.string.cancel_scheduled_message),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/** 定时消息详情弹窗内容：列出全部待发消息，可逐条取消。 */
@Composable
fun ScheduledMessagesContent(
    messages: List<Message>,
    onCancel: (Long) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = stringResource(R.string.scheduled_message),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        if (messages.isEmpty()) {
            Text(
                text = stringResource(R.string.scheduled_message_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        } else {
            messages.forEach { message ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.scheduled_at, formatDateTime(message.date.toLong())),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = message.body,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { onCancel(message.id) }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_symbol_delete),
                            contentDescription = stringResource(R.string.cancel_scheduled_message),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider()
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

package io.github.kylinlee.chatsim.ui.call

import android.telecom.Call
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import io.github.kylinlee.chatsim.R
import io.github.kylinlee.chatsim.data.legacy.NoCall
import io.github.kylinlee.chatsim.data.legacy.PhoneState
import io.github.kylinlee.chatsim.data.legacy.TwoCalls
import io.github.kylinlee.chatsim.data.model.AudioRoute
import io.github.kylinlee.chatsim.ui.common.formatDuration
import io.github.kylinlee.chatsim.ui.components.ConversationAvatar
import io.github.kylinlee.chatsim.viewmodel.CallUiState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val SwipeAnswerTriggerDistance = 72.dp
private val SwipeAnswerMaxDistance = 160.dp

@Composable
fun CallScreen(
    state: CallUiState,
    swipeToAnswer: Boolean,
    overlayEnabled: Boolean,
    onMinimize: () -> Unit,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onEndCall: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onToggleHold: () -> Unit,
    onDtmf: (Char) -> Unit,
    onSwap: () -> Unit,
    onMerge: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dialpadVisible by remember { mutableStateOf(false) }

    val isIncoming = state.callState == Call.STATE_RINGING
    val isOnHold = state.callState == Call.STATE_HOLDING
    val isSpeakerOn = state.audioRoute == AudioRoute.SPEAKER
    val hasTwoCalls = state.phoneState is TwoCalls

    Box(modifier = modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.navigationBars))
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(24.dp))
                    ConversationAvatar(
                        title = state.callerName.ifBlank { state.callerNumber },
                        photoUri = state.callerPhotoUri,
                        size = 112.dp,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = state.callerName.ifBlank { state.callerNumber.ifBlank { stringResource(R.string.unknown) } },
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (state.callerName.isNotBlank() && state.callerNumber.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = state.callerNumber,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.numberLabel.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = state.numberLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.roleLabel.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ) {
                            Text(
                                text = stringResource(
                                    io.github.kylinlee.chatsim.R.string.call_role_label,
                                    state.roleLabel,
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = callStateLabel(state),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

            if (dialpadVisible) {
                Dialpad(
                    onKey = onDtmf,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (isIncoming) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        val rejectIcon = painterResource(io.github.kylinlee.chatsim.R.drawable.ic_symbol_call_end)
                        val rejectLabel = stringResource(io.github.kylinlee.chatsim.R.string.decline)
                        val answerIcon = painterResource(io.github.kylinlee.chatsim.R.drawable.ic_symbol_call)
                        val answerLabel = stringResource(io.github.kylinlee.chatsim.R.string.answer)
                        if (swipeToAnswer) {
                            SwipeUpCallButton(
                                icon = rejectIcon,
                                label = rejectLabel,
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                                onTrigger = onReject,
                            )
                            SwipeUpCallButton(
                                icon = answerIcon,
                                label = answerLabel,
                                containerColor = Color(0xFF4CAF50),
                                contentColor = Color.White,
                                onTrigger = onAccept,
                            )
                        } else {
                            CallActionButton(
                                icon = rejectIcon,
                                label = rejectLabel,
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                                onClick = onReject,
                            )
                            CallActionButton(
                                icon = answerIcon,
                                label = answerLabel,
                                containerColor = Color(0xFF4CAF50),
                                contentColor = Color.White,
                                onClick = onAccept,
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        CallActionButton(
                            icon = if (state.isMuted) painterResource(io.github.kylinlee.chatsim.R.drawable.ic_symbol_mic_off) else painterResource(io.github.kylinlee.chatsim.R.drawable.ic_symbol_mic),
                            label = stringResource(
                                if (state.isMuted) {
                                    io.github.kylinlee.chatsim.R.string.unmute
                                } else {
                                    io.github.kylinlee.chatsim.R.string.mute
                                }
                            ),
                            active = state.isMuted,
                            onClick = onToggleMute,
                        )
                        CallActionButton(
                            icon = painterResource(io.github.kylinlee.chatsim.R.drawable.ic_symbol_volume_up),
                            label = stringResource(io.github.kylinlee.chatsim.R.string.speaker),
                            active = isSpeakerOn,
                            onClick = onToggleSpeaker,
                        )
                        CallActionButton(
                            icon = if (isOnHold) painterResource(io.github.kylinlee.chatsim.R.drawable.ic_symbol_play_arrow) else painterResource(io.github.kylinlee.chatsim.R.drawable.ic_symbol_pause),
                            label = stringResource(
                                if (isOnHold) {
                                    io.github.kylinlee.chatsim.R.string.unhold
                                } else {
                                    io.github.kylinlee.chatsim.R.string.hold
                                }
                            ),
                            active = isOnHold,
                            onClick = onToggleHold,
                        )
                        CallActionButton(
                            icon = painterResource(io.github.kylinlee.chatsim.R.drawable.ic_symbol_dialpad),
                            label = stringResource(io.github.kylinlee.chatsim.R.string.dialpad),
                            active = dialpadVisible,
                            onClick = { dialpadVisible = !dialpadVisible },
                        )
                    }

                    if (hasTwoCalls) {
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            CallActionButton(
                                icon = painterResource(io.github.kylinlee.chatsim.R.drawable.ic_symbol_swap_horiz),
                                label = stringResource(io.github.kylinlee.chatsim.R.string.swap),
                                onClick = onSwap,
                            )
                            CallActionButton(
                                icon = painterResource(io.github.kylinlee.chatsim.R.drawable.ic_symbol_call_merge),
                                label = stringResource(io.github.kylinlee.chatsim.R.string.merge),
                                onClick = onMerge,
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                    CallActionButton(
                        icon = painterResource(io.github.kylinlee.chatsim.R.drawable.ic_symbol_call_end),
                        label = stringResource(io.github.kylinlee.chatsim.R.string.hang_up),
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                        onClick = onEndCall,
                    )
                }
            }
            }
        }

        IconButton(
            onClick = onMinimize,
            enabled = overlayEnabled,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(8.dp),
        ) {
            Icon(
                painter = painterResource(io.github.kylinlee.chatsim.R.drawable.ic_symbol_picture_in_picture_mobile),
                contentDescription = stringResource(io.github.kylinlee.chatsim.R.string.minimize_to_overlay),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun callStateLabel(state: CallUiState): String = when {
    state.phoneState is NoCall -> stringResource(io.github.kylinlee.chatsim.R.string.call_ended)
    state.callState == Call.STATE_RINGING -> stringResource(io.github.kylinlee.chatsim.R.string.incoming_call)
    state.callState == Call.STATE_DIALING || state.callState == Call.STATE_CONNECTING -> {
        stringResource(io.github.kylinlee.chatsim.R.string.dialing)
    }

    state.callState == Call.STATE_HOLDING -> stringResource(io.github.kylinlee.chatsim.R.string.on_hold)
    state.callState == Call.STATE_ACTIVE -> formatDuration(state.durationSeconds)
    state.callState == Call.STATE_DISCONNECTED || state.callState == Call.STATE_DISCONNECTING -> {
        stringResource(io.github.kylinlee.chatsim.R.string.call_ended)
    }

    else -> stringResource(io.github.kylinlee.chatsim.R.string.ongoing_call)
}

@Composable
private fun CallActionButton(
    icon: Painter,
    label: String,
    onClick: () -> Unit,
    active: Boolean = false,
    containerColor: Color = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    },
    contentColor: Color = if (active) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    },
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(containerColor)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SwipeUpCallButton(
    icon: Painter,
    label: String,
    containerColor: Color,
    contentColor: Color,
    onTrigger: () -> Unit,
) {
    val triggerPx = with(LocalDensity.current) { SwipeAnswerTriggerDistance.toPx() }
    val maxDragPx = with(LocalDensity.current) { SwipeAnswerMaxDistance.toPx() }
    val offsetY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .offset { IntOffset(0, offsetY.value.roundToInt()) }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (offsetY.value <= -triggerPx) {
                            onTrigger()
                        } else {
                            scope.launch { offsetY.animateTo(0f) }
                        }
                    },
                    onDragCancel = { scope.launch { offsetY.animateTo(0f) } },
                ) { change, dragAmount ->
                    change.consume()
                    scope.launch { offsetY.snapTo((offsetY.value + dragAmount).coerceIn(-maxDragPx, 0f)) }
                }
            },
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(containerColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Dialpad(
    onKey: (Char) -> Unit,
    modifier: Modifier = Modifier,
) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("*", "0", "#"),
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        keys.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onKey(key.first()) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = key,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

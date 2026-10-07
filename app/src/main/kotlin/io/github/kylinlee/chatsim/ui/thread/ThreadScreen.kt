package io.github.kylinlee.chatsim.ui.thread

import android.provider.Telephony
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kylinlee.chatsim.R
import io.github.kylinlee.chatsim.common.extensions.toast
import io.github.kylinlee.chatsim.domain.model.TimelineItem
import io.github.kylinlee.chatsim.navigation.ThreadRoute
import io.github.kylinlee.chatsim.ui.common.AppMenu
import io.github.kylinlee.chatsim.ui.common.AppMenuItem
import io.github.kylinlee.chatsim.ui.common.isListDetailLayout
import io.github.kylinlee.chatsim.ui.components.ConversationAvatar
import io.github.kylinlee.chatsim.viewmodel.ThreadViewModel
import kotlinx.coroutines.delay

private val ScheduledBarReservedHeight = 72.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(
    route: ThreadRoute,
    onBack: () -> Unit,
    onCreateContact: (String) -> Unit,
    viewModel: ThreadViewModel = hiltViewModel<ThreadViewModel, ThreadViewModel.Factory>(
        creationCallback = { factory -> factory.create(route) }
    ),
) {
    val timeline by viewModel.timeline.collectAsStateWithLifecycle()
    val title by viewModel.title.collectAsStateWithLifecycle()
    val participants by viewModel.participants.collectAsStateWithLifecycle()
    val role by viewModel.role.collectAsStateWithLifecycle()
    val isRoleAvailable by viewModel.isRoleAvailable.collectAsStateWithLifecycle()
    val isContactSaved by viewModel.isContactSaved.collectAsStateWithLifecycle()
    val isBlocked by viewModel.isBlocked.collectAsStateWithLifecycle()
    val listDetailLayout = isListDetailLayout()
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsStateWithLifecycle()

    var messageText by rememberSaveable { mutableStateOf("") }
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showScheduledSheet by remember { mutableStateOf(false) }

    // 按时间倒序，胶囊展示最新一条（与会话列表摘要保持一致）
    val scheduledMessages = remember(timeline) {
        timeline.filterIsInstance<TimelineItem.MessageItem>()
            .map { it.message }
            .filter { it.isScheduled && it.status != Telephony.Sms.STATUS_FAILED }
            .sortedByDescending { it.date }
    }

    val context = LocalContext.current
    var pendingCallKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pendingCallKey) {
        val key = pendingCallKey ?: return@LaunchedEffect
        delay(4000L)
        if (pendingCallKey == key) {
            pendingCallKey = null
        }
    }

    val newestTimelineKey = timeline.lastOrNull()?.key

    LaunchedEffect(newestTimelineKey, lifecycleState) {
        if (lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) {
            viewModel.markThreadRead()
        }
    }

    val phoneNumber = participants.firstOrNull()?.phoneNumbers?.firstOrNull()?.value.orEmpty()
    val unavailableSuffix = stringResource(io.github.kylinlee.chatsim.R.string.role_unavailable)
    val currentRole = role
    val subtitleParts = remember(phoneNumber, currentRole, isRoleAvailable, unavailableSuffix) {
        buildList {
            if (phoneNumber.isNotEmpty()) add(phoneNumber)
            val roleLabel = currentRole?.label?.ifBlank { currentRole.displayPrefix }.orEmpty()
            if (roleLabel.isNotEmpty()) add(roleLabel)
            if (!isRoleAvailable && unavailableSuffix.isNotEmpty()) add(unavailableSuffix)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (!listDetailLayout) {
                        IconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(io.github.kylinlee.chatsim.R.drawable.ic_symbol_arrow_back),
                                contentDescription = stringResource(R.string.back),
                            )
                        }
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ConversationAvatar(
                            title = title.ifBlank { route.peerNumber },
                            photoUri = "",
                            size = 36.dp,
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = title.ifBlank { route.peerNumber },
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (subtitleParts.isNotEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    subtitleParts.forEachIndexed { index, part ->
                                        if (index > 0) {
                                            Icon(
                                                painter = painterResource(io.github.kylinlee.chatsim.R.drawable.ic_symbol_link),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier
                                                    .padding(horizontal = 4.dp)
                                                    .size(12.dp),
                                            )
                                        }
                                        Text(
                                            text = part,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.callNumber(phoneNumber)
                        },
                        enabled = isRoleAvailable,
                    ) {
                        Icon(
                            painter = painterResource(io.github.kylinlee.chatsim.R.drawable.ic_symbol_call),
                            contentDescription = stringResource(io.github.kylinlee.chatsim.R.string.call_back),
                        )
                    }
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            painter = painterResource(io.github.kylinlee.chatsim.R.drawable.ic_symbol_more_vert),
                            contentDescription = stringResource(io.github.kylinlee.chatsim.R.string.more),
                        )
                    }
                    AppMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        val menuItemCount = if (isContactSaved) 3 else 4
                        var menuIndex = 0
                        if (!isContactSaved) {
                            AppMenuItem(
                                text = stringResource(io.github.kylinlee.chatsim.R.string.save_contact),
                                onClick = {
                                    menuExpanded = false
                                    if (phoneNumber.isNotBlank()) {
                                        onCreateContact(phoneNumber)
                                    }
                                },
                                index = menuIndex++,
                                count = menuItemCount,
                            )
                        }
                        AppMenuItem(
                            text = stringResource(if (isBlocked) R.string.unblock_number else R.string.block_number),
                            onClick = {
                                menuExpanded = false
                                if (isBlocked) {
                                    viewModel.unblockNumber()
                                } else {
                                    viewModel.blockNumber()
                                    onBack()
                                }
                            },
                            index = menuIndex++,
                            count = menuItemCount,
                        )
                        AppMenuItem(
                            text = stringResource(io.github.kylinlee.chatsim.R.string.mark_as_unread),
                            onClick = {
                                menuExpanded = false
                                viewModel.markUnread()
                            },
                            index = menuIndex++,
                            count = menuItemCount,
                        )
                        AppMenuItem(
                            text = stringResource(R.string.delete),
                            onClick = {
                                menuExpanded = false
                                confirmDelete = true
                            },
                            index = menuIndex++,
                            count = menuItemCount,
                        )
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxHeight(),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    TimelineList(
                        items = timeline,
                        onLoadOlder = viewModel::loadOlderMessages,
                        showDeliveryStatus = viewModel.deliveryReportsEnabled,
                        onCallClick = { call ->
                            val key = "call:${call.id}"
                            if (pendingCallKey == key) {
                                pendingCallKey = null
                                viewModel.callNumber(call.displayNumber)
                            } else {
                                pendingCallKey = key
                                context.toast(id = io.github.kylinlee.chatsim.R.string.tap_again_to_call)
                            }
                        },
                        onDeleteMessage = { message ->
                            viewModel.deleteMessages(listOf(message))
                        },
                        modifier = Modifier.fillMaxSize(),
                        bottomContentPadding = if (scheduledMessages.isNotEmpty()) {
                            ScheduledBarReservedHeight
                        } else {
                            0.dp
                        },
                    )

                    if (scheduledMessages.isNotEmpty()) {
                        // 胶囊浮在消息列表上，列表底部预留高度，避免遮住最后一条消息
                        ScheduledMessagesBar(
                            messages = scheduledMessages,
                            onCancel = viewModel::cancelScheduledMessage,
                            onClick = { showScheduledSheet = true },
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 12.dp, end = 12.dp, bottom = 6.dp),
                        )
                    }
                }

                if (isRoleAvailable) {
                    MessageComposer(
                        text = messageText,
                        onTextChange = { messageText = it },
                        onSend = {
                            viewModel.sendMessage(messageText)
                            messageText = ""
                        },
                        onSchedule = { triggerAtMillis ->
                            viewModel.scheduleMessage(messageText, triggerAtMillis)
                            messageText = ""
                        },
                    )
                } else {
                    RoleUnavailableBar()
                }
            }
        }
    }

    if (showScheduledSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showScheduledSheet = false },
            sheetState = sheetState,
        ) {
            ScheduledMessagesContent(
                messages = scheduledMessages,
                onCancel = viewModel::cancelScheduledMessage,
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete)) },
            text = {
                Text(
                    stringResource(
                        io.github.kylinlee.chatsim.R.string.delete_whole_conversation_confirmation
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.deleteConversation()
                        onBack()
                    }
                ) {
                    Text(stringResource(R.string.yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.no))
                }
            },
        )
    }
}

@Composable
private fun RoleUnavailableBar(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
            .padding(horizontal = 16.dp, vertical = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(io.github.kylinlee.chatsim.R.string.role_unavailable),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

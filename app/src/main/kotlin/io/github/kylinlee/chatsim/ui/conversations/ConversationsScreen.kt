package io.github.kylinlee.chatsim.ui.conversations

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import io.github.kylinlee.chatsim.R
import io.github.kylinlee.chatsim.domain.model.ConversationThread
import io.github.kylinlee.chatsim.navigation.ThreadRoute
import io.github.kylinlee.chatsim.ui.common.AppMenu
import io.github.kylinlee.chatsim.ui.common.AppSelectableMenuItem
import io.github.kylinlee.chatsim.ui.common.SwipeActionButton
import io.github.kylinlee.chatsim.ui.common.SwipeActionContentColor
import io.github.kylinlee.chatsim.ui.common.SwipeActionWidth
import io.github.kylinlee.chatsim.ui.common.SwipeDeleteColor
import io.github.kylinlee.chatsim.ui.common.SwipePinColor
import io.github.kylinlee.chatsim.ui.common.SwipeReadColor
import io.github.kylinlee.chatsim.ui.common.SwipeRevealRow
import io.github.kylinlee.chatsim.viewmodel.ConversationsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    onConversationClick: (ConversationThread) -> Unit,
    onConversationDeleted: (ConversationThread) -> Unit,
    onNewConversationClick: () -> Unit,
    onDialpadClick: () -> Unit,
    onSettingsClick: (() -> Unit)?,
    selectedThread: ThreadRoute? = null,
    viewModel: ConversationsViewModel = hiltViewModel(),
) {
    val conversations by viewModel.conversations.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val roles by viewModel.roles.collectAsStateWithLifecycle()
    val selectedRoleId by viewModel.selectedRoleId.collectAsStateWithLifecycle()

    var searchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var revealedThreadId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pendingDelete by remember { mutableStateOf<ConversationThread?>(null) }
    var roleMenuExpanded by remember { mutableStateOf(false) }

    val selectedRole = roles.firstOrNull { it.id == selectedRoleId }
    val ambiguousTitles = remember(conversations) {
        conversations.groupingBy { it.title }.eachCount().filterValues { it > 1 }.keys
    }

    // 重新进入列表（如从会话返回）时按本地数据刷新，保证定时消息的排序与摘要即时生效
    LaunchedEffect(Unit) {
        viewModel.reloadConversations()
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Box {
                            Row(
                                modifier = Modifier.clickable { roleMenuExpanded = true },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = selectedRole?.label?.ifBlank { selectedRole.displayPrefix }
                                        ?: stringResource(io.github.kylinlee.chatsim.R.string.roles)
                                )
                                Icon(
                                    painter = painterResource(io.github.kylinlee.chatsim.R.drawable.ic_symbol_arrow_drop_down),
                                    contentDescription = null,
                                )
                            }
                            val roleCount = roles.size
                            AppMenu(
                                expanded = roleMenuExpanded,
                                onDismissRequest = { roleMenuExpanded = false },
                            ) {
                                roles.forEachIndexed { index, role ->
                                    AppSelectableMenuItem(
                                        text = role.label.ifBlank { role.displayPrefix },
                                        selected = role.id == selectedRole?.id,
                                        onClick = {
                                            roleMenuExpanded = false
                                            viewModel.selectRole(role.id)
                                        },
                                        index = index,
                                        count = roleCount,
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                searchActive = !searchActive
                                if (!searchActive) {
                                    searchQuery = ""
                                    viewModel.search("")
                                }
                            }
                        ) {
                            Icon(painterResource(io.github.kylinlee.chatsim.R.drawable.ic_symbol_search), contentDescription = stringResource(R.string.search))
                        }
                        if (onSettingsClick != null) {
                            IconButton(onClick = onSettingsClick) {
                                Icon(painterResource(io.github.kylinlee.chatsim.R.drawable.ic_symbol_settings), contentDescription = stringResource(R.string.settings))
                            }
                        }
                    },
                )

                AnimatedVisibility(
                    visible = searchActive,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            viewModel.search(it)
                        },
                        placeholder = {
                            Text(stringResource(io.github.kylinlee.chatsim.R.string.search_conversations))
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onDialpadClick,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 10.dp),
                modifier = Modifier
                    .size(58.dp)
                    .offset(y = (-36).dp),
            ) {
                Icon(
                    painter = painterResource(io.github.kylinlee.chatsim.R.drawable.ic_symbol_dialpad),
                    contentDescription = stringResource(io.github.kylinlee.chatsim.R.string.dialpad),
                    modifier = Modifier.size(26.dp),
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            AnimatedVisibility(
                visible = isLoading,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    conversations.isEmpty() && !isLoading -> {
                        EmptyConversations(
                            onNewConversationClick = onNewConversationClick,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }

                    else -> {
                        val listState = rememberLazyListState()
                        LaunchedEffect(listState.isScrollInProgress) {
                            if (listState.isScrollInProgress) {
                                revealedThreadId = null
                            }
                        }
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                            items(items = conversations, key = { it.conversationId }) { thread ->
                                val revealed = revealedThreadId == thread.conversationId
                                SwipeRevealRow(
                                    revealed = revealed,
                                    onRevealedChange = { open ->
                                        revealedThreadId = if (open) thread.conversationId else null
                                    },
                                    actionsWidth = SwipeActionWidth * 3,
                                    actions = {
                                        SwipeActionButton(
                                            text = stringResource(
                                                if (thread.isPinned) {
                                                    io.github.kylinlee.chatsim.R.string.unpin_conversation
                                                } else {
                                                    io.github.kylinlee.chatsim.R.string.pin_conversation
                                                }
                                            ),
                                            containerColor = SwipePinColor,
                                            contentColor = SwipeActionContentColor,
                                            onClick = {
                                                revealedThreadId = null
                                                if (thread.isPinned) {
                                                    viewModel.unpinConversation(thread.conversationId)
                                                } else {
                                                    viewModel.pinConversation(thread.conversationId)
                                                }
                                            },
                                        )
                                        SwipeActionButton(
                                            text = stringResource(
                                                if (thread.read) {
                                                    io.github.kylinlee.chatsim.R.string.mark_as_unread
                                                } else {
                                                    io.github.kylinlee.chatsim.R.string.mark_as_read
                                                }
                                            ),
                                            containerColor = SwipeReadColor,
                                            contentColor = SwipeActionContentColor,
                                            onClick = {
                                                revealedThreadId = null
                                                if (thread.read) {
                                                    viewModel.markUnread(thread.conversationId)
                                                } else {
                                                    viewModel.markRead(thread.conversationId)
                                                }
                                            },
                                        )
                                        SwipeActionButton(
                                            text = stringResource(R.string.delete),
                                            containerColor = SwipeDeleteColor,
                                            contentColor = SwipeActionContentColor,
                                            onClick = {
                                                revealedThreadId = null
                                                pendingDelete = thread
                                            },
                                        )
                                    },
                                ) {
                                    ConversationRow(
                                        thread = thread,
                                        showNumberSuffix = thread.title in ambiguousTitles,
                                        isSelected = selectedThread?.let {
                                            it.roleId == thread.roleId && it.peerNumber == thread.peerNumber
                                        } == true,
                                        onClick = {
                                            if (revealedThreadId != null) {
                                                revealedThreadId = null
                                            } else {
                                                onConversationClick(thread)
                                            }
                                        },
                                    )
                                }
                                HorizontalDivider(modifier = Modifier.padding(start = 76.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { thread ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
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
                        viewModel.deleteConversation(thread.conversationId)
                        onConversationDeleted(thread)
                        pendingDelete = null
                    }
                ) {
                    Text(stringResource(R.string.yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.no))
                }
            },
        )
    }
}

@Composable
private fun EmptyConversations(
    onNewConversationClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(io.github.kylinlee.chatsim.R.string.no_conversations_found),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onNewConversationClick) {
            Text(stringResource(io.github.kylinlee.chatsim.R.string.new_conversation))
        }
    }
}

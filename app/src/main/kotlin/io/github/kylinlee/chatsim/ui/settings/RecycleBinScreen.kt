package io.github.kylinlee.chatsim.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kylinlee.chatsim.R
import io.github.kylinlee.chatsim.ui.common.AppMenu
import io.github.kylinlee.chatsim.ui.common.AppSelectableMenuItem
import io.github.kylinlee.chatsim.ui.common.formatDateTime
import io.github.kylinlee.chatsim.ui.common.isListDetailLayout
import io.github.kylinlee.chatsim.viewmodel.RecycleBinRecord
import io.github.kylinlee.chatsim.viewmodel.RecycleBinViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RecycleBinScreen(
    onBack: () -> Unit,
    showBack: Boolean = true,
    viewModel: RecycleBinViewModel = hiltViewModel(),
) {
    val records by viewModel.records.collectAsStateWithLifecycle()
    val roles by viewModel.roles.collectAsStateWithLifecycle()
    val selectedRoleId by viewModel.selectedRoleId.collectAsStateWithLifecycle()
    val selectionMode by viewModel.selectionMode.collectAsStateWithLifecycle()
    val selectedIds by viewModel.selectedIds.collectAsStateWithLifecycle()
    val listDetailLayout = isListDetailLayout()

    val selectedRole = roles.firstOrNull { it.id == selectedRoleId } ?: roles.firstOrNull()

    var roleMenuExpanded by remember { mutableStateOf(false) }

    BackHandler(enabled = selectionMode) {
        viewModel.cancelSelection()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
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
                    navigationIcon = {
                        if (showBack && !listDetailLayout) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    painter = painterResource(io.github.kylinlee.chatsim.R.drawable.ic_symbol_arrow_back),
                                    contentDescription = stringResource(R.string.back),
                                )
                            }
                        }
                    },
                    actions = {
                        if (selectionMode) {
                            TextButton(onClick = viewModel::selectAll) {
                                Text(stringResource(io.github.kylinlee.chatsim.R.string.select_all))
                            }
                        } else {
                            TextButton(onClick = viewModel::startSelection) {
                                Text(stringResource(io.github.kylinlee.chatsim.R.string.select_multiple))
                            }
                        }
                    },
                )
            },
        ) { innerPadding ->
            if (records.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(io.github.kylinlee.chatsim.R.string.recycle_bin_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(bottom = if (selectionMode) 88.dp else 0.dp),
                ) {
                    items(items = records, key = { it.message.id }) { record ->
                        RecycleBinRow(
                            record = record,
                            selectionMode = selectionMode,
                            selected = record.message.id in selectedIds,
                            onToggle = { viewModel.toggleSelection(record.message.id) },
                        )
                    }
                }
            }
        }

        if (selectionMode) {
            HorizontalFloatingToolbar(
                expanded = true,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(bottom = FloatingToolbarDefaults.ScreenOffset),
            ) {
                IconButton(
                    onClick = viewModel::restoreSelected,
                    enabled = selectedIds.isNotEmpty(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_symbol_undo),
                        contentDescription = stringResource(R.string.restore),
                    )
                }
                IconButton(onClick = viewModel::cancelSelection) {
                    Icon(
                        painter = painterResource(io.github.kylinlee.chatsim.R.drawable.ic_symbol_close),
                        contentDescription = stringResource(R.string.cancel),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecycleBinRow(
    record: RecycleBinRecord,
    selectionMode: Boolean,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    val message = record.message
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = selectionMode, onClick = onToggle)
            .padding(
                start = 16.dp,
                end = if (selectionMode) 8.dp else 16.dp,
                top = 10.dp,
                bottom = 10.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(
                if (message.isCall) {
                    io.github.kylinlee.chatsim.R.drawable.ic_symbol_call
                } else {
                    io.github.kylinlee.chatsim.R.drawable.ic_symbol_sms
                }
            ),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = record.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val detail = when {
                message.isCall -> null
                message.body.isNotBlank() -> message.body
                message.attachment != null -> stringResource(io.github.kylinlee.chatsim.R.string.attachment_indicator)
                else -> null
            }
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = formatDateTime(message.date.toLong()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (message.isCall) {
            Text(
                text = callHitLabel(
                    callType = message.callType,
                    callDuration = message.callDuration,
                    blocked = false,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (selectionMode) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggle() },
            )
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
}

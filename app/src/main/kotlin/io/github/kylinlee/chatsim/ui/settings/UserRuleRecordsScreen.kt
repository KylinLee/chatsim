package io.github.kylinlee.chatsim.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kylinlee.chatsim.R
import io.github.kylinlee.chatsim.data.model.RuleHitEntity
import io.github.kylinlee.chatsim.domain.rule.user.RecordKind
import io.github.kylinlee.chatsim.domain.rule.user.UserRuleAction
import io.github.kylinlee.chatsim.ui.common.formatDateTime
import io.github.kylinlee.chatsim.ui.common.isListDetailLayout
import io.github.kylinlee.chatsim.viewmodel.RuleHitGroup
import io.github.kylinlee.chatsim.viewmodel.UserRuleRecordsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UserRuleRecordsScreen(
    onBack: () -> Unit,
    showBack: Boolean = true,
    onSettingsClick: (() -> Unit)? = null,
    onOpenSettings: () -> Unit = {},
    onRecycleBinClick: () -> Unit = {},
    viewModel: UserRuleRecordsViewModel = hiltViewModel(),
) {
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val selectedTag by viewModel.selectedTag.collectAsStateWithLifecycle()
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val collapsedRuleIds by viewModel.collapsedRuleIds.collectAsStateWithLifecycle()
    val selectionMode by viewModel.selectionMode.collectAsStateWithLifecycle()
    val selectedHitIds by viewModel.selectedHitIds.collectAsStateWithLifecycle()
    val recycleBinEnabled by viewModel.recycleBinEnabled.collectAsStateWithLifecycle()
    val listDetailLayout = isListDetailLayout()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val recycleBinDisabledHint = stringResource(io.github.kylinlee.chatsim.R.string.recycle_bin_disabled_snackbar)
    val goToSettingsLabel = stringResource(io.github.kylinlee.chatsim.R.string.go_to_settings)

    var pendingDelete by remember { mutableStateOf<PendingDelete?>(null) }

    LaunchedEffect(Unit) {
        viewModel.refreshRecycleBinEnabled()
    }

    BackHandler(enabled = selectionMode) {
        viewModel.cancelSelection()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(io.github.kylinlee.chatsim.R.string.rule_records)) },
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
                        IconButton(
                            onClick = {
                                if (recycleBinEnabled) {
                                    onRecycleBinClick()
                                } else {
                                    scope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = recycleBinDisabledHint,
                                            actionLabel = goToSettingsLabel,
                                            duration = SnackbarDuration.Long,
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            onOpenSettings()
                                        }
                                    }
                                }
                            },
                        ) {
                            Icon(
                                painter = painterResource(io.github.kylinlee.chatsim.R.drawable.ic_symbol_delete),
                                contentDescription = stringResource(io.github.kylinlee.chatsim.R.string.recycle_bin),
                                tint = if (recycleBinEnabled) {
                                    LocalContentColor.current
                                } else {
                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                },
                            )
                        }
                        if (onSettingsClick != null) {
                            IconButton(onClick = onSettingsClick) {
                                Icon(
                                    painter = painterResource(io.github.kylinlee.chatsim.R.drawable.ic_symbol_settings),
                                    contentDescription = stringResource(R.string.settings),
                                )
                            }
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                TabRow(selectedTabIndex = UserRuleAction.entries.indexOf(tab)) {
                    UserRuleAction.entries.forEach { action ->
                        Tab(
                            selected = action == tab,
                            onClick = { viewModel.selectTab(action) },
                            text = { Text(ruleActionLabel(action)) },
                        )
                    }
                }

                if (tab == UserRuleAction.ADD_TAG && tags.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(items = tags, key = { it }) { tag ->
                            FilterChip(
                                selected = selectedTag == tag,
                                onClick = { viewModel.selectTag(tag) },
                                label = { Text("#$tag") },
                            )
                        }
                    }
                }

                if (groups.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(io.github.kylinlee.chatsim.R.string.rule_records_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = if (selectionMode) 88.dp else 0.dp),
                    ) {
                        groups.forEach { group ->
                            item(key = "group_${group.ruleId}") {
                                RuleGroupHeader(
                                    group = group,
                                    collapsed = group.ruleId in collapsedRuleIds,
                                    selectionMode = selectionMode,
                                    selectedCount = group.hits.count { it.id in selectedHitIds },
                                    onToggleCollapse = { viewModel.toggleCollapsed(group.ruleId) },
                                    onStartSelection = viewModel::startSelection,
                                    onToggleAllSelected = { viewModel.toggleHits(group.hits.map { it.id }) },
                                )
                            }
                            if (group.ruleId !in collapsedRuleIds) {
                                items(items = group.hits, key = { it.id }) { hit ->
                                    RuleHitRow(
                                        hit = hit,
                                        selectionMode = selectionMode,
                                        selected = hit.id in selectedHitIds,
                                        onToggle = { viewModel.toggleHit(hit.id) },
                                    )
                                }
                            }
                        }
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
                trailingContent = {
                    IconButton(onClick = viewModel::cancelSelection) {
                        Icon(
                            painter = painterResource(io.github.kylinlee.chatsim.R.drawable.ic_symbol_close),
                            contentDescription = stringResource(R.string.cancel),
                        )
                    }
                },
            ) {
                IconButton(
                    onClick = { pendingDelete = PendingDelete.DELETE_FOREVER },
                    enabled = selectedHitIds.isNotEmpty(),
                ) {
                    Icon(
                        painter = painterResource(io.github.kylinlee.chatsim.R.drawable.ic_symbol_delete_forever),
                        contentDescription = stringResource(io.github.kylinlee.chatsim.R.string.rule_records_delete_forever),
                    )
                }
                IconButton(
                    onClick = { pendingDelete = PendingDelete.DELETE },
                    enabled = selectedHitIds.isNotEmpty(),
                ) {
                    Icon(
                        painter = painterResource(io.github.kylinlee.chatsim.R.drawable.ic_symbol_delete),
                        contentDescription = stringResource(R.string.delete),
                    )
                }
            }
        }
    }

    pendingDelete?.let { action ->
        val forever = action == PendingDelete.DELETE_FOREVER
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = {
                Text(
                    stringResource(
                        if (forever) {
                            io.github.kylinlee.chatsim.R.string.rule_records_delete_forever
                        } else {
                            R.string.delete
                        }
                    )
                )
            },
            text = {
                Text(
                    stringResource(
                        if (forever) {
                            io.github.kylinlee.chatsim.R.string.rule_records_delete_forever_confirmation
                        } else {
                            io.github.kylinlee.chatsim.R.string.rule_records_delete_confirmation
                        }
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSelected(toRecycleBin = !forever)
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

private enum class PendingDelete {
    DELETE,
    DELETE_FOREVER,
}

@Composable
private fun RuleGroupHeader(
    group: RuleHitGroup,
    collapsed: Boolean,
    selectionMode: Boolean,
    selectedCount: Int,
    onToggleCollapse: () -> Unit,
    onStartSelection: () -> Unit,
    onToggleAllSelected: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleCollapse)
            .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(io.github.kylinlee.chatsim.R.drawable.ic_symbol_arrow_drop_down),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(20.dp)
                .rotate(if (collapsed) -90f else 0f),
        )

        Text(
            text = group.ruleName,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
        )

        if (selectionMode) {
            TriStateCheckbox(
                state = when (selectedCount) {
                    0 -> ToggleableState.Off
                    group.hits.size -> ToggleableState.On
                    else -> ToggleableState.Indeterminate
                },
                onClick = onToggleAllSelected,
            )
        } else {
            TextButton(onClick = onStartSelection) {
                Text(stringResource(io.github.kylinlee.chatsim.R.string.rule_records_select))
            }
        }
    }
}

@Composable
private fun RuleHitRow(
    hit: RuleHitEntity,
    selectionMode: Boolean,
    selected: Boolean,
    onToggle: () -> Unit,
) {
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
                if (hit.recordKind == RecordKind.CALL.name) {
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
                text = hit.title.ifBlank { hit.address },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (hit.body.isNotBlank()) {
                Text(
                    text = hit.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = formatDateTime(hit.recordDate / 1000),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (hit.recordKind == RecordKind.CALL.name) {
            Text(
                text = callHitLabel(
                    callType = hit.callType,
                    callDuration = hit.callDuration,
                    blocked = hit.action == UserRuleAction.BLOCK.name,
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

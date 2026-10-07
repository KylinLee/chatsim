package io.github.kylinlee.chatsim.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kylinlee.chatsim.R
import io.github.kylinlee.chatsim.common.extensions.toast
import io.github.kylinlee.chatsim.data.model.UserTagEntity
import io.github.kylinlee.chatsim.domain.rule.user.UserRule
import io.github.kylinlee.chatsim.domain.rule.user.UserRuleAction
import io.github.kylinlee.chatsim.ui.common.isListDetailLayout
import io.github.kylinlee.chatsim.viewmodel.UserRulesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserRulesScreen(
    onBack: () -> Unit,
    showBack: Boolean = true,
    onAddRule: () -> Unit,
    onEditRule: (Long) -> Unit,
    onSettingsClick: (() -> Unit)? = null,
    viewModel: UserRulesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val runResult by viewModel.runResult.collectAsStateWithLifecycle()
    val blockedNumbers by viewModel.blockedNumbers.collectAsStateWithLifecycle()
    val listDetailLayout = isListDetailLayout()
    val context = LocalContext.current

    var showTagsSheet by remember { mutableStateOf(false) }
    var showBlockedSheet by remember { mutableStateOf(false) }
    var confirmDeleteTag by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<UserRule?>(null) }
    var confirmRun by remember { mutableStateOf<UserRule?>(null) }

    LaunchedEffect(runResult) {
        runResult?.let { count ->
            context.toast(msg = context.getString(io.github.kylinlee.chatsim.R.string.rule_run_result, count))
            viewModel.consumeRunResult()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(io.github.kylinlee.chatsim.R.string.custom_rules)) },
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
                    IconButton(onClick = {
                        viewModel.refreshBlockedNumbers()
                        showBlockedSheet = true
                    }) {
                        Icon(
                            painter = painterResource(io.github.kylinlee.chatsim.R.drawable.ic_symbol_person_cancel),
                            contentDescription = stringResource(io.github.kylinlee.chatsim.R.string.blacklist),
                        )
                    }
                    IconButton(onClick = { showTagsSheet = true }) {
                        Icon(
                            painter = painterResource(io.github.kylinlee.chatsim.R.drawable.ic_symbol_tag),
                            contentDescription = stringResource(io.github.kylinlee.chatsim.R.string.rule_tags_title),
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddRule,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 10.dp),
                modifier = Modifier
                    .size(58.dp)
                    .offset(y = (-36).dp),
            ) {
                Icon(
                    painter = painterResource(io.github.kylinlee.chatsim.R.drawable.ic_symbol_add),
                    contentDescription = stringResource(io.github.kylinlee.chatsim.R.string.add_rule),
                    modifier = Modifier.size(26.dp),
                )
            }
        },
    ) { innerPadding ->
        if (state.rules.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(io.github.kylinlee.chatsim.R.string.rule_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                UserRuleAction.entries.forEach { action ->
                    val rules = state.rules.filter { it.action == action }
                    if (rules.isEmpty()) return@forEach

                    item(key = "header_$action") {
                        Text(
                            text = ruleActionLabel(action),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    items(items = rules, key = { it.id }) { rule ->
                        UserRuleRow(
                            rule = rule,
                            roles = state.roles,
                            onToggle = { enabled -> viewModel.setEnabled(rule.id, enabled) },
                            onEdit = { onEditRule(rule.id) },
                            onRun = { confirmRun = rule },
                            onDelete = { confirmDelete = rule },
                        )
                    }
                }
            }
        }
    }

    if (showTagsSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showTagsSheet = false },
            sheetState = sheetState,
        ) {
            TagManagementContent(
                tags = state.tags,
                onAdd = viewModel::addTag,
                onDelete = { confirmDeleteTag = it },
            )
        }
    }

    if (showBlockedSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = { showBlockedSheet = false },
            sheetState = sheetState,
        ) {
            BlocklistContent(
                numbers = blockedNumbers,
                onRemove = viewModel::removeBlockedNumber,
            )
        }
    }

    confirmRun?.let { rule ->
        AlertDialog(
            onDismissRequest = { confirmRun = null },
            title = { Text(stringResource(io.github.kylinlee.chatsim.R.string.rule_run)) },
            text = { Text(stringResource(io.github.kylinlee.chatsim.R.string.rule_run_rule_confirmation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.runRule(rule.id)
                        confirmRun = null
                    }
                ) {
                    Text(stringResource(R.string.yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRun = null }) {
                    Text(stringResource(R.string.no))
                }
            },
        )
    }

    confirmDelete?.let { rule ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(io.github.kylinlee.chatsim.R.string.rule_delete_rule_confirmation)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteRule(rule.id)
                        confirmDelete = null
                    }
                ) {
                    Text(stringResource(R.string.yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) {
                    Text(stringResource(R.string.no))
                }
            },
        )
    }

    confirmDeleteTag?.let { name ->
        AlertDialog(
            onDismissRequest = { confirmDeleteTag = null },
            title = { Text(stringResource(R.string.delete)) },
            text = {
                Text(
                    stringResource(
                        io.github.kylinlee.chatsim.R.string.rule_delete_tag_confirmation,
                        name,
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteTag(name)
                        confirmDeleteTag = null
                    }
                ) {
                    Text(stringResource(R.string.yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteTag = null }) {
                    Text(stringResource(R.string.no))
                }
            },
        )
    }
}

@Composable
private fun TagManagementContent(
    tags: List<UserTagEntity>,
    onAdd: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var newTag by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = stringResource(io.github.kylinlee.chatsim.R.string.rule_tags_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = newTag,
            onValueChange = { newTag = it },
            label = { Text(stringResource(io.github.kylinlee.chatsim.R.string.rule_new_tag)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(
                    onClick = {
                        onAdd(newTag)
                        newTag = ""
                    },
                    enabled = newTag.isNotBlank(),
                ) {
                    Icon(
                        painter = painterResource(io.github.kylinlee.chatsim.R.drawable.ic_symbol_add),
                        contentDescription = null,
                    )
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        tags.forEach { tag ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "#${tag.name}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (tag.isBuiltin) {
                    Text(
                        text = stringResource(io.github.kylinlee.chatsim.R.string.rule_tag_builtin),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    IconButton(onClick = { onDelete(tag.name) }) {
                        Icon(
                            painter = painterResource(io.github.kylinlee.chatsim.R.drawable.ic_symbol_delete),
                            contentDescription = stringResource(R.string.delete),
                        )
                    }
                }
            }
            HorizontalDivider()
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun BlocklistContent(
    numbers: List<String>,
    onRemove: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = stringResource(io.github.kylinlee.chatsim.R.string.blacklist),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        if (numbers.isEmpty()) {
            Text(
                text = stringResource(io.github.kylinlee.chatsim.R.string.blacklist_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        } else {
            numbers.forEach { number ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = number,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    IconButton(onClick = { onRemove(number) }) {
                        Icon(
                            painter = painterResource(io.github.kylinlee.chatsim.R.drawable.ic_symbol_delete),
                            contentDescription = stringResource(io.github.kylinlee.chatsim.R.string.unblock_number),
                        )
                    }
                }
                HorizontalDivider()
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun UserRuleRow(
    rule: UserRule,
    roles: Map<Long, String>,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onRun: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Switch(
            checked = rule.enabled,
            onCheckedChange = onToggle,
            modifier = Modifier.scale(0.8f),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
        ) {
            Text(
                text = rule.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = ruleExpressionSummary(rule.conditions, roles),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (rule.tags.isNotEmpty()) {
                Text(
                    text = rule.tags.joinToString(" ") { "#$it" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        IconButton(
            onClick = onRun,
            enabled = rule.action != UserRuleAction.BLOCK,
        ) {
            Icon(
                painter = painterResource(io.github.kylinlee.chatsim.R.drawable.ic_symbol_play_arrow),
                contentDescription = stringResource(io.github.kylinlee.chatsim.R.string.rule_run),
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                painter = painterResource(io.github.kylinlee.chatsim.R.drawable.ic_symbol_delete),
                contentDescription = stringResource(R.string.delete),
            )
        }
    }
    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
}

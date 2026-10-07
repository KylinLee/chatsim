package io.github.kylinlee.chatsim.ui.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kylinlee.chatsim.R
import io.github.kylinlee.chatsim.data.model.SIMCard
import io.github.kylinlee.chatsim.domain.model.Role
import io.github.kylinlee.chatsim.ui.common.AppMenu
import io.github.kylinlee.chatsim.ui.common.AppMenuItem
import io.github.kylinlee.chatsim.ui.common.AppSelectableMenuItem
import io.github.kylinlee.chatsim.ui.common.PressAnchorMenu
import io.github.kylinlee.chatsim.ui.common.isListDetailLayout
import io.github.kylinlee.chatsim.ui.common.trackPressOffset
import io.github.kylinlee.chatsim.viewmodel.RolesViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RolesScreen(
    onBack: () -> Unit,
    viewModel: RolesViewModel = hiltViewModel(),
) {
    val roles by viewModel.roles.collectAsStateWithLifecycle()
    val activeRole by viewModel.activeRole.collectAsStateWithLifecycle()
    val simCards by viewModel.simCards.collectAsStateWithLifecycle()
    val listDetailLayout = isListDetailLayout()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingRole by remember { mutableStateOf<Role?>(null) }
    var pendingDelete by remember { mutableStateOf<Role?>(null) }
    var menuId by remember { mutableStateOf<Long?>(null) }
    var menuOffset by remember { mutableStateOf(Offset.Zero) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(io.github.kylinlee.chatsim.R.string.manage_roles)) },
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
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(
                    painter = painterResource(io.github.kylinlee.chatsim.R.drawable.ic_symbol_add),
                    contentDescription = stringResource(io.github.kylinlee.chatsim.R.string.add_role),
                )
            }
        },
    ) { innerPadding ->
        if (roles.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(io.github.kylinlee.chatsim.R.string.no_roles),
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
                items(items = roles, key = { it.id }) { role ->
                    val available = viewModel.isAvailable(role)
                    Box {
                        ListItem(
                            headlineContent = {
                                Text(role.label.ifBlank { role.displayPrefix })
                            },
                            supportingContent = {
                                Text(roleSupportingText(role, available, viewModel.simLabelFor(role.subscriptionId)))
                            },
                            leadingContent = {
                                if (role.id == activeRole?.id) {
                                    Icon(
                                        painter = painterResource(io.github.kylinlee.chatsim.R.drawable.ic_symbol_check),
                                        contentDescription = stringResource(
                                            io.github.kylinlee.chatsim.R.string.role_active
                                        ),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            },
                            modifier = Modifier
                                .trackPressOffset { menuOffset = it }
                                .combinedClickable(
                                    onClick = { viewModel.setActiveRole(role.id) },
                                    onLongClick = { menuId = role.id },
                                ),
                        )

                        PressAnchorMenu(
                            expanded = menuId == role.id,
                            onDismissRequest = { menuId = null },
                            pressOffset = menuOffset,
                        ) {
                            val itemCount = if (role.isVirtual) 2 else 1
                            AppMenuItem(
                                text = stringResource(
                                    io.github.kylinlee.chatsim.R.string.edit_role
                                ),
                                onClick = {
                                    menuId = null
                                    editingRole = role
                                },
                                index = 0,
                                count = itemCount,
                            )
                            if (role.isVirtual) {
                                AppMenuItem(
                                    text = stringResource(R.string.delete),
                                    onClick = {
                                        menuId = null
                                        pendingDelete = role
                                    },
                                    index = 1,
                                    count = itemCount,
                                )
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                }
            }
        }
    }

    if (showAddDialog || editingRole != null) {
        val current = editingRole
        RoleDialog(
            role = current,
            simCards = simCards,
            onConfirm = { label, smsPrefix, callPrefix, subscriptionId, isDefault ->
                if (current == null) {
                    viewModel.addVirtualRole(
                        label = label,
                        smsPrefix = smsPrefix,
                        callPrefix = callPrefix,
                        subscriptionId = subscriptionId,
                        isDefault = isDefault,
                    )
                } else {
                    viewModel.updateRole(
                        current.copy(
                            label = label,
                            smsPrefix = if (current.isVirtual) smsPrefix else "",
                            callPrefix = if (current.isVirtual) callPrefix else "",
                            subscriptionId = if (current.isVirtual) subscriptionId else current.subscriptionId,
                            isDefault = isDefault,
                        )
                    )
                }
                showAddDialog = false
                editingRole = null
            },
            onDismiss = {
                showAddDialog = false
                editingRole = null
            },
        )
    }

    pendingDelete?.let { role ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete)) },
            text = {
                Text(
                    stringResource(
                        io.github.kylinlee.chatsim.R.string.delete_role_confirmation
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteRole(role.id)
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
private fun roleSupportingText(role: Role, available: Boolean, simLabel: String): String {
    val kindLabel = stringResource(
        if (role.isVirtual) {
            io.github.kylinlee.chatsim.R.string.role_kind_virtual
        } else {
            io.github.kylinlee.chatsim.R.string.role_kind_sim
        }
    )
    val boundSim = simLabel.ifEmpty { role.subscriptionId?.toString().orEmpty() }
    val prefixes = if (role.isVirtual) {
        listOfNotNull(
            role.smsPrefix.takeIf { it.isNotBlank() }
                ?.let { "${stringResource(io.github.kylinlee.chatsim.R.string.role_sms_prefix)} $it" },
            role.callPrefix.takeIf { it.isNotBlank() }
                ?.let { "${stringResource(io.github.kylinlee.chatsim.R.string.role_call_prefix)} $it" },
        )
    } else {
        emptyList()
    }
    val detail = (listOf(kindLabel) + prefixes + boundSim.takeIf { it.isNotEmpty() }).joinToString(" · ")

    return if (available) {
        detail
    } else {
        "$detail · ${stringResource(io.github.kylinlee.chatsim.R.string.role_unavailable)}"
    }
}

@Composable
private fun RoleDialog(
    role: Role?,
    simCards: List<SIMCard>,
    onConfirm: (label: String, smsPrefix: String, callPrefix: String, subscriptionId: Int?, isDefault: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val isSimRole = role != null && !role.isVirtual
    var label by remember { mutableStateOf(role?.label.orEmpty()) }
    var smsPrefix by remember { mutableStateOf(role?.smsPrefix.orEmpty()) }
    var callPrefix by remember { mutableStateOf(role?.callPrefix.orEmpty()) }
    var subscriptionId by remember { mutableStateOf(role?.subscriptionId ?: simCards.firstOrNull()?.subscriptionId) }
    var isDefault by remember { mutableStateOf(role?.isDefault ?: false) }
    var simMenuExpanded by remember { mutableStateOf(false) }

    val selectedSimLabel = simCards.firstOrNull { it.subscriptionId == subscriptionId }?.label.orEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (role == null) {
                        io.github.kylinlee.chatsim.R.string.add_role
                    } else {
                        io.github.kylinlee.chatsim.R.string.edit_role
                    }
                )
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = {
                        Text(stringResource(io.github.kylinlee.chatsim.R.string.role_label))
                    },
                    singleLine = true,
                )
                if (!isSimRole) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = smsPrefix,
                        onValueChange = { smsPrefix = it },
                        label = {
                            Text(stringResource(io.github.kylinlee.chatsim.R.string.role_sms_prefix))
                        },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = callPrefix,
                        onValueChange = { callPrefix = it },
                        label = {
                            Text(stringResource(io.github.kylinlee.chatsim.R.string.role_call_prefix))
                        },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    Box {
                        OutlinedButton(onClick = { simMenuExpanded = true }) {
                            Text(
                                selectedSimLabel.ifEmpty {
                                    stringResource(io.github.kylinlee.chatsim.R.string.role_bind_sim)
                                }
                            )
                        }
                        val simCount = simCards.size
                        AppMenu(
                            expanded = simMenuExpanded,
                            onDismissRequest = { simMenuExpanded = false },
                        ) {
                            simCards.forEachIndexed { index, sim ->
                                AppSelectableMenuItem(
                                    text = sim.label,
                                    selected = sim.subscriptionId == subscriptionId,
                                    onClick = {
                                        subscriptionId = sim.subscriptionId
                                        simMenuExpanded = false
                                    },
                                    index = index,
                                    count = simCount,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = isDefault,
                        onCheckedChange = { isDefault = it },
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            io.github.kylinlee.chatsim.R.string.role_default
                        )
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(label.trim(), smsPrefix.trim(), callPrefix.trim(), subscriptionId, isDefault) },
                enabled = label.isNotBlank() && (isSimRole || smsPrefix.isNotBlank() || callPrefix.isNotBlank()),
            ) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

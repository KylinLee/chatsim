package io.github.kylinlee.chatsim.ui.newconversation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kylinlee.chatsim.domain.model.SimpleContact
import io.github.kylinlee.chatsim.R
import io.github.kylinlee.chatsim.navigation.ThreadRoute
import io.github.kylinlee.chatsim.ui.common.AppMenu
import io.github.kylinlee.chatsim.ui.common.AppSelectableMenuItem
import io.github.kylinlee.chatsim.ui.common.isListDetailLayout
import io.github.kylinlee.chatsim.ui.components.ConversationAvatar
import io.github.kylinlee.chatsim.viewmodel.NewConversationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewConversationScreen(
    onBack: () -> Unit,
    onConversationStarted: (ThreadRoute) -> Unit,
    viewModel: NewConversationViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val contacts by viewModel.filteredContacts.collectAsStateWithLifecycle()
    val roles by viewModel.roles.collectAsStateWithLifecycle()
    val activeRole by viewModel.activeRole.collectAsStateWithLifecycle()
    val selectedRoleId by viewModel.selectedRoleId.collectAsStateWithLifecycle()
    val listDetailLayout = isListDetailLayout()

    var roleMenuExpanded by remember { mutableStateOf(false) }

    val currentRole = roles.firstOrNull { it.id == (selectedRoleId ?: activeRole?.id) }
        ?: activeRole
        ?: roles.firstOrNull()

    fun startConversation(number: String, name: String) {
        val trimmedNumber = number.trim()
        if (trimmedNumber.isEmpty()) return

        val roleId = currentRole?.id ?: return
        onConversationStarted(
            ThreadRoute(
                roleId = roleId,
                peerNumber = trimmedNumber,
                title = name,
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.new_conversation)) },
                navigationIcon = {
                    if (!listDetailLayout) {
                        IconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(R.drawable.ic_symbol_arrow_back),
                                contentDescription = stringResource(io.github.kylinlee.chatsim.R.string.back),
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Box {
                ListItem(
                    headlineContent = {
                        Text(
                            stringResource(io.github.kylinlee.chatsim.R.string.roles)
                        )
                    },
                    supportingContent = {
                        Text(
                            currentRole?.label?.ifBlank { currentRole.displayPrefix }
                                ?: stringResource(io.github.kylinlee.chatsim.R.string.no_roles)
                        )
                    },
                    modifier = Modifier.clickable { roleMenuExpanded = true },
                )
                val roleCount = roles.size
                AppMenu(
                    expanded = roleMenuExpanded,
                    onDismissRequest = { roleMenuExpanded = false },
                ) {
                    roles.forEachIndexed { index, role ->
                        AppSelectableMenuItem(
                            text = role.label.ifBlank { role.displayPrefix },
                            selected = role.id == currentRole?.id,
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
            HorizontalDivider()

            OutlinedTextField(
                value = query,
                onValueChange = viewModel::search,
                placeholder = { Text(stringResource(R.string.search_or_enter_number)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            val trimmedQuery = query.trim()
            if (trimmedQuery.length >= 3 && contacts.none { it.name.equals(trimmedQuery, ignoreCase = true) }) {
                ListItem(
                    headlineContent = { Text(trimmedQuery) },
                    supportingContent = { Text(stringResource(R.string.start_conversation)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.ic_symbol_send),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    modifier = Modifier.clickable {
                        startConversation(trimmedQuery, "")
                    },
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
            }

            if (contacts.isEmpty()) {
                Text(
                    text = stringResource(io.github.kylinlee.chatsim.R.string.no_contacts_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(
                        items = contacts,
                        key = { "${it.contactId}:${it.name}" },
                    ) { contact ->
                        ContactRow(
                            contact = contact,
                            onClick = {
                                val number = viewModel.primaryNumberFor(contact).orEmpty()
                                startConversation(number, contact.name)
                            },
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 68.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactRow(
    contact: SimpleContact,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ConversationAvatar(
            title = contact.name,
            photoUri = contact.photoUri,
            size = 40.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = contact.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            contact.phoneNumbers.firstOrNull()?.let { phoneNumber ->
                Text(
                    text = phoneNumber.value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

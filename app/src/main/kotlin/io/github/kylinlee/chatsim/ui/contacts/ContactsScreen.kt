package io.github.kylinlee.chatsim.ui.contacts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kylinlee.chatsim.R
import io.github.kylinlee.chatsim.domain.model.contacts.Contact
import io.github.kylinlee.chatsim.domain.model.contacts.Group
import io.github.kylinlee.chatsim.domain.ContactSection
import io.github.kylinlee.chatsim.domain.displayName
import io.github.kylinlee.chatsim.domain.model.ContactItem
import io.github.kylinlee.chatsim.ui.common.AppMenu
import io.github.kylinlee.chatsim.ui.common.AppSelectableMenuItem
import io.github.kylinlee.chatsim.ui.common.SwipeActionButton
import io.github.kylinlee.chatsim.ui.common.SwipeActionContentColor
import io.github.kylinlee.chatsim.ui.common.SwipeActionWidth
import io.github.kylinlee.chatsim.ui.common.SwipeDeleteColor
import io.github.kylinlee.chatsim.ui.common.SwipePinColor
import io.github.kylinlee.chatsim.ui.common.SwipeRevealRow
import io.github.kylinlee.chatsim.ui.common.contactSourceTitle
import io.github.kylinlee.chatsim.viewmodel.ContactsViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val IndexBarWidth = 28.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ContactsScreen(
    onContactClick: (Int) -> Unit,
    onDialNumber: (String) -> Unit,
    onCreateContact: () -> Unit,
    onSettingsClick: (() -> Unit)?,
    viewModel: ContactsViewModel = hiltViewModel(),
) {
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    val groups by viewModel.visibleGroups.collectAsStateWithLifecycle()
    val contactSources by viewModel.contactSources.collectAsStateWithLifecycle()
    val selectedSourceName by viewModel.selectedSourceName.collectAsStateWithLifecycle()
    val selectedGroupId by viewModel.selectedGroupId.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    var searchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var revealedContactId by rememberSaveable { mutableStateOf<Int?>(null) }
    var sourceMenuExpanded by remember { mutableStateOf(false) }
    var pendingDeleteContact by remember { mutableStateOf<Contact?>(null) }
    var sheetContact by remember { mutableStateOf<Contact?>(null) }

    val selectedSource = contactSources.firstOrNull { it.name == selectedSourceName }
    val pinnedContacts = contacts.filter { it.isPinned }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Box {
                            Row(
                                modifier = Modifier.clickable { sourceMenuExpanded = true },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = selectedSource?.let { contactSourceTitle(it) }
                                        ?: stringResource(
                                            io.github.kylinlee.chatsim.R.string.all_accounts
                                        )
                                )
                                Icon(
                                    painter = painterResource(io.github.kylinlee.chatsim.R.drawable.ic_symbol_arrow_drop_down),
                                    contentDescription = null,
                                )
                            }
                            val sourceCount = contactSources.size + 1
                            AppMenu(
                                expanded = sourceMenuExpanded,
                                onDismissRequest = { sourceMenuExpanded = false },
                            ) {
                                AppSelectableMenuItem(
                                    text = stringResource(
                                        io.github.kylinlee.chatsim.R.string.all_accounts
                                    ),
                                    selected = selectedSource == null,
                                    onClick = {
                                        sourceMenuExpanded = false
                                        viewModel.selectSource(null)
                                    },
                                    index = 0,
                                    count = sourceCount,
                                )
                                contactSources.forEachIndexed { index, source ->
                                    AppSelectableMenuItem(
                                        text = contactSourceTitle(source),
                                        selected = selectedSource?.name == source.name,
                                        onClick = {
                                            sourceMenuExpanded = false
                                            viewModel.selectSource(source.name)
                                        },
                                        index = index + 1,
                                        count = sourceCount,
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
                            Text(stringResource(io.github.kylinlee.chatsim.R.string.search_contacts))
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
                onClick = onCreateContact,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 10.dp),
                modifier = Modifier
                    .size(58.dp)
                    .offset(y = (-36).dp),
            ) {
                Icon(
                    painter = painterResource(io.github.kylinlee.chatsim.R.drawable.ic_symbol_add),
                    contentDescription = stringResource(io.github.kylinlee.chatsim.R.string.new_contact),
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
                    contacts.isEmpty() && !isLoading -> {
                        Text(
                            text = stringResource(R.string.no_contacts_found),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }

                    else -> {
                        val listState = rememberLazyListState()
                        val scope = rememberCoroutineScope()
                        LaunchedEffect(listState.isScrollInProgress) {
                            if (listState.isScrollInProgress) {
                                revealedContactId = null
                            }
                        }
                        val entries = remember(pinnedContacts, sections) {
                            buildContactsListEntries(pinnedContacts, sections)
                        }
                        val sectionStarts = remember(entries) { sectionStartIndices(entries) }
                        val scrolledLetter by remember(listState, sectionStarts) {
                            derivedStateOf {
                                activeSectionLetter(listState.firstVisibleItemIndex, sectionStarts)
                            }
                        }
                        var scrollJob by remember { mutableStateOf<Job?>(null) }
                        var pressedLetter by remember { mutableStateOf<Char?>(null) }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(end = IndexBarWidth),
                        ) {
                            entries.forEach { entry ->
                                when (entry) {
                                    ContactsListEntry.PinnedHeader -> item(key = "pinned_header") {
                                        SectionHeader(
                                            stringResource(
                                                io.github.kylinlee.chatsim.R.string.pinned_contacts
                                            )
                                        )
                                    }

                                    is ContactsListEntry.PinnedContact -> item(key = entry.item.contact.id) {
                                        ContactListItem(
                                            item = entry.item,
                                            revealedContactId = revealedContactId,
                                            onContactClick = { sheetContact = entry.item.contact },
                                            onRevealedContactIdChange = { revealedContactId = it },
                                            onDelete = { pendingDeleteContact = entry.item.contact },
                                            viewModel = viewModel,
                                        )
                                    }

                                    ContactsListEntry.GroupChips -> item(key = "group_chips") {
                                        GroupChipsRow(
                                            groups = groups,
                                            selectedGroupId = selectedGroupId,
                                            onGroupSelected = viewModel::selectGroup,
                                        )
                                    }

                                    is ContactsListEntry.LetterHeader -> stickyHeader(key = "letter_${entry.letter}") {
                                        SectionHeader(entry.letter.toString())
                                    }

                                    is ContactsListEntry.ContactEntry -> item(key = entry.item.contact.id) {
                                        ContactListItem(
                                            item = entry.item,
                                            revealedContactId = revealedContactId,
                                            onContactClick = { sheetContact = entry.item.contact },
                                            onRevealedContactIdChange = { revealedContactId = it },
                                            onDelete = { pendingDeleteContact = entry.item.contact },
                                            viewModel = viewModel,
                                        )
                                    }
                                }
                            }
                        }

                        ContactIndexBar(
                            letters = sections.map { it.letter },
                            activeLetter = pressedLetter ?: scrolledLetter,
                            onLetterSelected = { letter, animate ->
                                val index = sectionStarts[letter] ?: return@ContactIndexBar
                                scrollJob?.cancel()
                                scrollJob = scope.launch {
                                    if (animate) {
                                        listState.animateScrollToItem(index)
                                    } else {
                                        listState.scrollToItem(index)
                                    }
                                }
                            },
                            onPressedLetterChange = { pressedLetter = it },
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 4.dp),
                        )

                        pressedLetter?.let { letter ->
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = letter.toString(),
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDeleteContact?.let { contact ->
        AlertDialog(
            onDismissRequest = { pendingDeleteContact = null },
            title = { Text(stringResource(R.string.delete)) },
            text = {
                Text(
                    stringResource(
                        io.github.kylinlee.chatsim.R.string.delete_contact_confirmation,
                        contact.displayName()
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteContact(contact)
                        pendingDeleteContact = null
                    }
                ) {
                    Text(stringResource(R.string.yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteContact = null }) {
                    Text(stringResource(R.string.no))
                }
            },
        )
    }

    sheetContact?.let { contact ->
        ContactInfoSheet(
            contact = contact,
            onDismiss = { sheetContact = null },
            onEdit = { onContactClick(contact.id) },
            onDial = onDialNumber,
        )
    }
}

@Composable
private fun ContactListItem(
    item: ContactItem,
    revealedContactId: Int?,
    onContactClick: (Int) -> Unit,
    onRevealedContactIdChange: (Int?) -> Unit,
    onDelete: () -> Unit,
    viewModel: ContactsViewModel,
) {
    val revealed = revealedContactId == item.contact.id
    SwipeRevealRow(
        revealed = revealed,
        onRevealedChange = { open ->
            onRevealedContactIdChange(if (open) item.contact.id else null)
        },
        actionsWidth = SwipeActionWidth * 2,
        actions = {
            SwipeActionButton(
                text = stringResource(
                    if (item.isPinned) {
                        io.github.kylinlee.chatsim.R.string.unpin
                    } else {
                        io.github.kylinlee.chatsim.R.string.pin_contact
                    }
                ),
                containerColor = SwipePinColor,
                contentColor = SwipeActionContentColor,
                onClick = {
                    onRevealedContactIdChange(null)
                    if (item.isPinned) {
                        viewModel.unpinContact(item.contact.id)
                    } else {
                        viewModel.pinContact(item.contact.id)
                    }
                },
            )
            SwipeActionButton(
                text = stringResource(R.string.delete),
                containerColor = SwipeDeleteColor,
                contentColor = SwipeActionContentColor,
                onClick = {
                    onRevealedContactIdChange(null)
                    onDelete()
                },
            )
        },
    ) {
        ContactRow(
            item = item,
            onClick = {
                if (revealedContactId != null) {
                    onRevealedContactIdChange(null)
                } else {
                    onContactClick(item.contact.id)
                }
            },
        )
    }
    HorizontalDivider(modifier = Modifier.padding(start = 76.dp))
}

@Composable
private fun GroupChipsRow(
    groups: List<Group>,
    selectedGroupId: Long?,
    onGroupSelected: (Long?) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "all_groups") {
            FilterChip(
                selected = selectedGroupId == null,
                onClick = { onGroupSelected(null) },
                label = {
                    Text(stringResource(io.github.kylinlee.chatsim.R.string.all_contacts))
                },
            )
        }
        items(items = groups, key = { it.id ?: it.title }) { group ->
            FilterChip(
                selected = selectedGroupId == group.id,
                onClick = { onGroupSelected(group.id) },
                label = { Text(group.title) },
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

private sealed interface ContactsListEntry {
    data object PinnedHeader : ContactsListEntry

    data class PinnedContact(val item: ContactItem) : ContactsListEntry

    data object GroupChips : ContactsListEntry

    data class LetterHeader(val letter: Char) : ContactsListEntry

    data class ContactEntry(val item: ContactItem) : ContactsListEntry
}

private fun buildContactsListEntries(
    pinnedContacts: List<ContactItem>,
    sections: List<ContactSection>,
): List<ContactsListEntry> = buildList {
    if (pinnedContacts.isNotEmpty()) {
        add(ContactsListEntry.PinnedHeader)
        pinnedContacts.forEach { add(ContactsListEntry.PinnedContact(it)) }
    }
    add(ContactsListEntry.GroupChips)
    sections.forEach { section ->
        add(ContactsListEntry.LetterHeader(section.letter))
        section.items.forEach { add(ContactsListEntry.ContactEntry(it)) }
    }
}

private fun sectionStartIndices(entries: List<ContactsListEntry>): Map<Char, Int> = buildMap {
    entries.forEachIndexed { index, entry ->
        if (entry is ContactsListEntry.LetterHeader) {
            put(entry.letter, index)
        }
    }
}

private fun activeSectionLetter(firstVisibleIndex: Int, sectionStarts: Map<Char, Int>): Char? =
    sectionStarts.entries
        .filter { it.value <= firstVisibleIndex }
        .maxByOrNull { it.value }
        ?.key

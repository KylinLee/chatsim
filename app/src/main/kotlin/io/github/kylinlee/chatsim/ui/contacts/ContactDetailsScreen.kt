package io.github.kylinlee.chatsim.ui.contacts

import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kylinlee.chatsim.R
import io.github.kylinlee.chatsim.common.extensions.normalizePhoneNumber
import io.github.kylinlee.chatsim.domain.model.PhoneNumber
import io.github.kylinlee.chatsim.domain.model.contacts.Contact
import io.github.kylinlee.chatsim.domain.model.contacts.ContactSource
import io.github.kylinlee.chatsim.domain.model.contacts.Group
import io.github.kylinlee.chatsim.navigation.ContactDetailsRoute
import io.github.kylinlee.chatsim.domain.nameOrEmpty
import io.github.kylinlee.chatsim.ui.common.contactSourceTitle
import io.github.kylinlee.chatsim.ui.common.isListDetailLayout
import io.github.kylinlee.chatsim.ui.components.ConversationAvatar
import io.github.kylinlee.chatsim.viewmodel.ContactAccountSelection
import io.github.kylinlee.chatsim.viewmodel.ContactDetailsViewModel

private data class EditablePhone(
    val value: String,
    val type: Int,
    val label: String,
)

private data class EditableAccount(
    val source: ContactSource,
    val rawContactId: Int,
    val selected: Boolean,
    val groupIds: Set<Long>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailsScreen(
    route: ContactDetailsRoute,
    onBack: () -> Unit,
    viewModel: ContactDetailsViewModel = hiltViewModel(),
) {
    val contact by viewModel.contact.collectAsStateWithLifecycle()
    val accountGroups by viewModel.accountGroups.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val isSaved by viewModel.isSaved.collectAsStateWithLifecycle()
    val listDetailLayout = isListDetailLayout()

    LaunchedEffect(route.contactId, route.phoneNumber) {
        viewModel.load(route.contactId, route.phoneNumber)
    }

    LaunchedEffect(isSaved) {
        if (isSaved) {
            viewModel.consumeSaved()
            onBack()
        }
    }

    var prefix by remember(contact) { mutableStateOf(contact?.prefix.orEmpty()) }
    var firstName by remember(contact) { mutableStateOf(contact?.firstName.orEmpty()) }
    var middleName by remember(contact) { mutableStateOf(contact?.middleName.orEmpty()) }
    var surname by remember(contact) { mutableStateOf(contact?.surname.orEmpty()) }
    var suffix by remember(contact) { mutableStateOf(contact?.suffix.orEmpty()) }
    val numbers = remember(contact) {
        mutableStateListOf<EditablePhone>().apply {
            contact?.phoneNumbers?.forEach { add(EditablePhone(it.value, it.type, it.label)) }
        }
    }
    val accountStates = remember(accounts, contact?.id) {
        val isNewContact = contact?.id == 0
        val defaultIndex = if (isNewContact) {
            accounts.indexOfFirst { it.source.isLocal }.takeIf { it >= 0 } ?: 0
        } else {
            -1
        }
        mutableStateListOf<EditableAccount>().apply {
            accounts.forEachIndexed { index, account ->
                add(
                    EditableAccount(
                        source = account.source,
                        rawContactId = account.rawContactId,
                        selected = account.exists || index == defaultIndex,
                        groupIds = account.groupIds,
                    )
                )
            }
        }
    }
    var groupPickerIndex by remember { mutableStateOf<Int?>(null) }

    val canSave = contact != null && !isSaving && accountStates.any { it.selected } && (
        firstName.isNotBlank() || middleName.isNotBlank() || surname.isNotBlank() ||
            numbers.any { it.value.isNotBlank() }
        )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (route.contactId == 0) {
                                io.github.kylinlee.chatsim.R.string.new_contact
                            } else {
                                R.string.contact_details
                            }
                        )
                    )
                },
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
                actions = {
                    if (isSaving) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier
                                .padding(end = 16.dp)
                                .size(24.dp),
                        )
                    } else {
                        IconButton(
                            enabled = canSave,
                            onClick = {
                                val edited = buildEditedContact(
                                    contact = contact ?: return@IconButton,
                                    prefix = prefix,
                                    firstName = firstName,
                                    middleName = middleName,
                                    surname = surname,
                                    suffix = suffix,
                                    numbers = numbers,
                                )
                                viewModel.save(
                                    contact = edited,
                                    selections = accountStates.map {
                                        ContactAccountSelection(
                                            source = it.source,
                                            rawContactId = it.rawContactId,
                                            isSelected = it.selected,
                                            groupIds = it.groupIds,
                                        )
                                    },
                                )
                            },
                        ) {
                            Icon(
                                painter = painterResource(io.github.kylinlee.chatsim.R.drawable.ic_symbol_check),
                                contentDescription = stringResource(R.string.save),
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val loaded = contact
            when {
                loaded == null && isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                loaded == null -> {
                    Text(
                        text = stringResource(R.string.no_contacts_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }

                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val headerName = loaded.copy(
                            prefix = prefix,
                            firstName = firstName,
                            middleName = middleName,
                            surname = surname,
                            suffix = suffix,
                        ).nameOrEmpty()

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ConversationAvatar(
                                title = headerName,
                                photoUri = loaded.photoUri,
                                size = 56.dp,
                            )
                            if (headerName.isNotBlank()) {
                                Spacer(Modifier.width(16.dp))
                                Text(
                                    text = headerName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }

                        OutlinedTextField(
                            value = firstName,
                            onValueChange = { firstName = it },
                            label = { Text(stringResource(R.string.first_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = middleName,
                            onValueChange = { middleName = it },
                            label = { Text(stringResource(R.string.middle_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = surname,
                            onValueChange = { surname = it },
                            label = { Text(stringResource(R.string.surname)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = prefix,
                                onValueChange = { prefix = it },
                                label = {
                                    Text(
                                        stringResource(
                                            io.github.kylinlee.chatsim.R.string.contact_prefix
                                        )
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = suffix,
                                onValueChange = { suffix = it },
                                label = {
                                    Text(
                                        stringResource(
                                            io.github.kylinlee.chatsim.R.string.contact_suffix
                                        )
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                        }

                        Text(
                            text = stringResource(io.github.kylinlee.chatsim.R.string.phone_numbers),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp),
                        )

                        numbers.forEachIndexed { index, phone ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = phone.value,
                                    onValueChange = { numbers[index] = phone.copy(value = it) },
                                    label = { Text(phoneTypeLabel(phone.type, phone.label)) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { numbers.removeAt(index) }) {
                                    Icon(
                                        painter = painterResource(io.github.kylinlee.chatsim.R.drawable.ic_symbol_delete),
                                        contentDescription = stringResource(R.string.delete),
                                    )
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = { numbers.add(EditablePhone("", Phone.TYPE_MOBILE, "")) },
                        ) {
                            Icon(painter = painterResource(io.github.kylinlee.chatsim.R.drawable.ic_symbol_add), contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(
                                    io.github.kylinlee.chatsim.R.string.add_new_number
                                )
                            )
                        }

                        Text(
                            text = stringResource(
                                io.github.kylinlee.chatsim.R.string.account_membership
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp),
                        )

                        accountStates.forEachIndexed { index, account ->
                            val sourceGroups = accountGroups.filter { it.accountName == account.source.name }
                            val selectedGroupTitles = sourceGroups
                                .filter { it.group.id in account.groupIds }
                                .joinToString(", ") { it.group.title }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        accountStates[index] = account.copy(selected = !account.selected)
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = account.selected,
                                    onCheckedChange = { checked ->
                                        accountStates[index] = account.copy(selected = checked)
                                    },
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = contactSourceTitle(account.source),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }

                            if (account.selected) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 48.dp)
                                        .clickable { groupPickerIndex = index }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = selectedGroupTitles.ifEmpty {
                                            stringResource(
                                                io.github.kylinlee.chatsim.R.string.no_groups
                                            )
                                        },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        text = stringResource(
                                            io.github.kylinlee.chatsim.R.string.add_to_group
                                        ),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val pickerIndex = groupPickerIndex
    val pickerAccount = pickerIndex?.let { accountStates.getOrNull(it) }
    if (pickerIndex != null && pickerAccount != null) {
        GroupPickerDialog(
            groups = accountGroups.filter { it.accountName == pickerAccount.source.name }.map { it.group },
            selectedGroupIds = pickerAccount.groupIds,
            onConfirm = { ids ->
                accountStates[pickerIndex] = pickerAccount.copy(groupIds = ids)
                groupPickerIndex = null
            },
            onDismiss = { groupPickerIndex = null },
        )
    }
}

@Composable
private fun phoneTypeLabel(type: Int, label: String): String {
    val context = LocalContext.current
    return remember(type, label) {
        Phone.getTypeLabel(context.resources, type, label).toString()
    }
}

@Composable
private fun GroupPickerDialog(
    groups: List<Group>,
    selectedGroupIds: Set<Long>,
    onConfirm: (Set<Long>) -> Unit,
    onDismiss: () -> Unit,
) {
    var selection by remember { mutableStateOf(selectedGroupIds) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(io.github.kylinlee.chatsim.R.string.add_to_group))
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                groups.forEach { group ->
                    val groupId = group.id ?: return@forEach
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selection = if (groupId in selection) {
                                    selection - groupId
                                } else {
                                    selection + groupId
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = groupId in selection,
                            onCheckedChange = { checked ->
                                selection = if (checked) selection + groupId else selection - groupId
                            },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = group.title,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selection) }) {
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

private fun buildEditedContact(
    contact: Contact,
    prefix: String,
    firstName: String,
    middleName: String,
    surname: String,
    suffix: String,
    numbers: List<EditablePhone>,
): Contact {
    val phoneNumbers = ArrayList<PhoneNumber>()
    numbers.forEach { phone ->
        val value = phone.value.trim()
        if (value.isEmpty()) return@forEach

        phoneNumbers.add(
            PhoneNumber(
                value = value,
                type = phone.type,
                label = phone.label,
                normalizedNumber = value.normalizePhoneNumber(),
                isPrimary = false,
            )
        )
    }

    return contact.copy(
        prefix = prefix.trim(),
        firstName = firstName.trim(),
        middleName = middleName.trim(),
        surname = surname.trim(),
        suffix = suffix.trim(),
        phoneNumbers = phoneNumbers,
    )
}

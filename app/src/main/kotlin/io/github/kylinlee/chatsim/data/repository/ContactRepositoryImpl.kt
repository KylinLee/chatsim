package io.github.kylinlee.chatsim.data.repository

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import io.github.kylinlee.chatsim.data.contacts.ContactsHelper
import io.github.kylinlee.chatsim.common.PHOTO_UNCHANGED
import io.github.kylinlee.chatsim.common.SMT_PRIVATE
import io.github.kylinlee.chatsim.data.contacts.SimpleContactsHelper
import io.github.kylinlee.chatsim.domain.model.SimpleContact
import io.github.kylinlee.chatsim.domain.model.contacts.Contact
import io.github.kylinlee.chatsim.domain.model.contacts.ContactSource
import io.github.kylinlee.chatsim.domain.model.contacts.Group
import io.github.kylinlee.chatsim.di.IoDispatcher
import io.github.kylinlee.chatsim.domain.ContactFilter
import io.github.kylinlee.chatsim.domain.ContactNameIndex
import io.github.kylinlee.chatsim.domain.ContactNames
import io.github.kylinlee.chatsim.domain.displayName
import io.github.kylinlee.chatsim.domain.model.AccountGroup
import io.github.kylinlee.chatsim.domain.model.ContactAccount
import io.github.kylinlee.chatsim.common.extensions.getSuggestedContacts
import io.github.kylinlee.chatsim.repository.ContactRepository
import io.github.kylinlee.chatsim.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class ContactRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ContactRepository {
    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())

    private val contactIndex = HashMap<String, Contact>()

    private val nameIndex = ContactNameIndex()

    override val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    override suspend fun loadContacts(showOnlyContactsWithNumbers: Boolean): List<Contact> = withContext(ioDispatcher) {
        val allContacts = ArrayList(queryContacts(showOnlyContactsWithNumbers))

        ContactNames.separator = settings.nameSeparator
        Contact.startWithSurname = settings.startNameWithSurname
        Contact.sorting = settings.sorting
        allContacts.sort()
        _contacts.value = allContacts
        rebuildContactIndex(allContacts)
        nameIndex.rebuild(allContacts) { it.displayName() }
        allContacts
    }

    override suspend fun loadAvailableSimpleContacts(showOnlyContactsWithNumbers: Boolean): List<SimpleContact> =
        withContext(ioDispatcher) {
            suspendCancellableCoroutine { continuation ->
                SimpleContactsHelper(context).getAvailableContacts(showOnlyContactsWithNumbers) { contacts ->
                    if (continuation.isActive) {
                        continuation.resume(contacts)
                    }
                }
            }
        }

    override suspend fun loadContactSources(): List<ContactSource> = withContext(ioDispatcher) {
        suspendCancellableCoroutine { continuation ->
            ContactsHelper(context).getContactSources { sources ->
                if (continuation.isActive) {
                    continuation.resume(sources)
                }
            }
        }
    }

    override suspend fun loadSaveableContactSources(): List<ContactSource> = withContext(ioDispatcher) {
        suspendCancellableCoroutine { continuation ->
            ContactsHelper(context).getSaveableContactSources { sources ->
                if (continuation.isActive) {
                    continuation.resume(sources)
                }
            }
        }
    }

    override suspend fun loadAccountGroups(): List<AccountGroup> = withContext(ioDispatcher) {
        val accountGroups = ArrayList<AccountGroup>()
        val projection = arrayOf(
            ContactsContract.Groups._ID,
            ContactsContract.Groups.TITLE,
            ContactsContract.Groups.ACCOUNT_NAME,
            ContactsContract.Groups.ACCOUNT_TYPE,
        )
        val selection = "${ContactsContract.Groups.AUTO_ADD} = ? AND ${ContactsContract.Groups.FAVORITES} = ?"
        val selectionArgs = arrayOf("0", "0")

        context.contentResolver.query(
            ContactsContract.Groups.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null,
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(ContactsContract.Groups._ID)
            val titleIndex = cursor.getColumnIndex(ContactsContract.Groups.TITLE)
            val accountNameIndex = cursor.getColumnIndex(ContactsContract.Groups.ACCOUNT_NAME)
            val accountTypeIndex = cursor.getColumnIndex(ContactsContract.Groups.ACCOUNT_TYPE)

            while (cursor.moveToNext()) {
                val accountName = cursor.getString(accountNameIndex).orEmpty()
                val accountType = cursor.getString(accountTypeIndex).orEmpty()

                val title = cursor.getString(titleIndex).orEmpty()
                if (title.isEmpty()) continue

                accountGroups.add(
                    AccountGroup(
                        group = Group(cursor.getLong(idIndex), title),
                        accountName = accountName,
                        accountType = accountType,
                    )
                )
            }
        }
        accountGroups
    }

    override suspend fun loadContactAccounts(contactId: Int): List<ContactAccount> = withContext(ioDispatcher) {
        if (contactId == 0) return@withContext emptyList()

        val rawContacts = HashMap<String, Int>()
        context.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(
                ContactsContract.RawContacts._ID,
                ContactsContract.RawContacts.ACCOUNT_NAME,
                ContactsContract.RawContacts.ACCOUNT_TYPE,
            ),
            "${ContactsContract.RawContacts.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            null,
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(ContactsContract.RawContacts._ID)
            val accountNameIndex = cursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_NAME)
            val accountTypeIndex = cursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_TYPE)

            while (cursor.moveToNext()) {
                val accountName = cursor.getString(accountNameIndex).orEmpty()
                val accountType = cursor.getString(accountTypeIndex).orEmpty()
                rawContacts["$accountName:$accountType"] = cursor.getInt(idIndex)
            }
        }

        val groupIdsByRawContact = HashMap<Int, MutableSet<Long>>()
        val rawContactIds = rawContacts.values.toList()
        if (rawContactIds.isNotEmpty()) {
            val selection = "${ContactsContract.Data.RAW_CONTACT_ID} IN (${rawContactIds.joinToString(",")}) AND " +
                "${ContactsContract.Data.MIMETYPE} = ?"
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(ContactsContract.Data.RAW_CONTACT_ID, ContactsContract.Data.DATA1),
                selection,
                arrayOf(ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE),
                null,
            )?.use { cursor ->
                val rawContactIdIndex = cursor.getColumnIndex(ContactsContract.Data.RAW_CONTACT_ID)
                val groupIdIndex = cursor.getColumnIndex(ContactsContract.Data.DATA1)

                while (cursor.moveToNext()) {
                    val rawContactId = cursor.getInt(rawContactIdIndex)
                    groupIdsByRawContact.getOrPut(rawContactId) { mutableSetOf() }.add(cursor.getLong(groupIdIndex))
                }
            }
        }

        val sources = loadContactSources()
        sources.map { source ->
            val rawContactId = rawContacts["${source.name}:${source.type}"] ?: 0
            ContactAccount(
                source = source,
                rawContactId = rawContactId,
                groupIds = if (rawContactId != 0) groupIdsByRawContact[rawContactId].orEmpty().toSet() else emptySet(),
            )
        }
    }

    override suspend fun loadSuggestedContacts(): List<SimpleContact> = withContext(ioDispatcher) {
        context.getSuggestedContacts(ArrayList())
    }

    override suspend fun saveContact(contact: Contact): Boolean = withContext(ioDispatcher) {
        val contactsHelper = ContactsHelper(context)
        if (contact.id == 0) {
            val source = getSaveableContactSource(contactsHelper) ?: return@withContext false
            contact.source = source
            contactsHelper.insertContact(contact)
        } else {
            contactsHelper.updateContact(contact, PHOTO_UNCHANGED)
        }
    }

    override suspend fun insertContact(contact: Contact, source: ContactSource): Boolean = withContext(ioDispatcher) {
        contact.source = source.name
        ContactsHelper(context).insertContact(contact)
    }

    override suspend fun deleteContact(contact: Contact): Boolean = withContext(ioDispatcher) {
        ContactsHelper(context).deleteContacts(arrayListOf(contact))
    }

    private suspend fun getSaveableContactSource(contactsHelper: ContactsHelper): String? =
        suspendCancellableCoroutine { continuation ->
            contactsHelper.getSaveableContactSources { sources ->
                if (continuation.isActive) {
                    continuation.resume(sources.firstOrNull()?.name)
                }
            }
        }

    override suspend fun refresh(): List<Contact> = loadContacts(showOnlyContactsWithNumbers = true)

    override fun getCachedContacts(): List<Contact> = _contacts.value

    override suspend fun getContactById(id: Int): Contact? = withContext(ioDispatcher) {
        _contacts.value.firstOrNull { it.id == id }
            ?: ContactsHelper(context).getContactWithId(id)
    }

    override fun search(query: String): List<Contact> = ContactFilter.filter(_contacts.value, query)

    override fun searchByName(query: String): List<Contact> = nameIndex.search(query)

    override fun getContactByNumber(number: String): Contact? {
        val queryDigits = ContactFilter.normalizePhoneNumber(number)
        if (queryDigits.isEmpty()) return null

        ContactFilter.phoneKeys(queryDigits).forEach { key ->
            contactIndex[key]?.let { return it }
        }

        return _contacts.value.firstOrNull { contact ->
            contact.phoneNumbers.any { phone ->
                val phoneNumber = phone.normalizedNumber.ifBlank { phone.value }
                ContactFilter.phoneMatches(phoneNumber, queryDigits)
            }
        }
    }

    private fun rebuildContactIndex(contacts: List<Contact>) {
        contactIndex.clear()
        contacts.forEach { contact ->
            contact.phoneNumbers.forEach { phone ->
                val normalized = ContactFilter.normalizePhoneNumber(phone.normalizedNumber.ifBlank { phone.value })
                if (normalized.isEmpty()) return@forEach

                ContactFilter.phoneKeys(normalized).forEach { key ->
                    contactIndex.putIfAbsent(key, contact)
                }
            }
        }
    }

    override suspend fun getContactFromUri(uri: Uri): Contact? = withContext(ioDispatcher) {
        ContactsHelper(context).getContactFromUri(uri)
    }

    override suspend fun getContactByLookupKey(lookupKey: String): Contact? = withContext(ioDispatcher) {
        ContactsHelper(context).getContactWithLookupKey(lookupKey)
    }

    override fun getNameByNumber(number: String): String? {
        if (number.isBlank()) return null
        return SimpleContactsHelper(context).getNameFromPhoneNumber(number)
    }

    override fun getPhotoUriByNumber(number: String): String? {
        if (number.isBlank()) return null
        return SimpleContactsHelper(context).getPhotoUriFromPhoneNumber(number)
    }

    private suspend fun queryContacts(showOnlyContactsWithNumbers: Boolean): List<Contact> =
        suspendCancellableCoroutine { continuation ->
            ContactsHelper(context).getContacts(showOnlyContactsWithNumbers = showOnlyContactsWithNumbers) { contacts ->
                if (continuation.isActive) {
                    continuation.resume(contacts)
                }
            }
        }
}

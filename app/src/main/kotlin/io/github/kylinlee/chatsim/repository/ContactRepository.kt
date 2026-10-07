package io.github.kylinlee.chatsim.repository

import android.net.Uri
import io.github.kylinlee.chatsim.domain.model.SimpleContact
import io.github.kylinlee.chatsim.domain.model.contacts.Contact
import io.github.kylinlee.chatsim.domain.model.contacts.ContactSource
import io.github.kylinlee.chatsim.domain.model.AccountGroup
import io.github.kylinlee.chatsim.domain.model.ContactAccount
import kotlinx.coroutines.flow.StateFlow

interface ContactRepository {
    val contacts: StateFlow<List<Contact>>

    suspend fun loadContacts(showOnlyContactsWithNumbers: Boolean = true): List<Contact>

    suspend fun loadAccountGroups(): List<AccountGroup>

    suspend fun loadContactAccounts(contactId: Int): List<ContactAccount>

    suspend fun loadContactSources(): List<ContactSource>

    suspend fun loadSaveableContactSources(): List<ContactSource>

    suspend fun loadAvailableSimpleContacts(showOnlyContactsWithNumbers: Boolean = false): List<SimpleContact>

    suspend fun loadSuggestedContacts(): List<SimpleContact>

    suspend fun refresh(): List<Contact>

    suspend fun saveContact(contact: Contact): Boolean

    suspend fun insertContact(contact: Contact, source: ContactSource): Boolean

    suspend fun deleteContact(contact: Contact): Boolean

    fun getCachedContacts(): List<Contact>

    suspend fun getContactById(id: Int): Contact?

    fun search(query: String): List<Contact>

    /** Prefix search over the contact name index, matching from the first character on. */
    fun searchByName(query: String): List<Contact>

    fun getContactByNumber(number: String): Contact?

    suspend fun getContactFromUri(uri: Uri): Contact?

    suspend fun getContactByLookupKey(lookupKey: String): Contact?

    fun getNameByNumber(number: String): String?

    fun getPhotoUriByNumber(number: String): String?
}

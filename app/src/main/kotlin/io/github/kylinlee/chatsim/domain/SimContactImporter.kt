package io.github.kylinlee.chatsim.domain

import android.provider.ContactsContract.CommonDataKinds.Phone
import io.github.kylinlee.chatsim.domain.model.PhoneNumber
import io.github.kylinlee.chatsim.domain.model.contacts.Contact

data class SimContactRecord(
    val name: String,
    val number: String,
)

data class SimPerson(
    val name: String,
    val numbers: List<PhoneNumber>,
)

data class SimImportPlan(
    val newContacts: List<SimPerson>,
    val numbersToMerge: Map<Int, List<PhoneNumber>>,
    val ignoredRecords: Int,
)

/**
 * Builds an import plan for the records read from the SIM card.
 *
 * Rules:
 * 1. records whose concatenated name matches are treated as the same person and their numbers are merged
 * 2. a number that already exists on a device contact makes that SIM record ignored
 * 3. a number can only be owned by a single person, one person can own multiple numbers
 */
object SimContactImporter {
    fun plan(records: List<SimContactRecord>, deviceContacts: List<Contact>): SimImportPlan {
        val existingNumbers = HashSet<String>()
        deviceContacts.forEach { contact ->
            contact.phoneNumbers.forEach { phone ->
                numberKey(phone.normalizedNumber.ifBlank { phone.value })?.let { existingNumbers.add(it) }
            }
        }

        val claimedNumbers = HashSet<String>()
        val newPeople = LinkedHashMap<String, MutableList<PhoneNumber>>()
        val numbersToMerge = LinkedHashMap<Int, MutableList<PhoneNumber>>()
        var ignoredRecords = 0

        records.groupBy { it.name.trim() }.forEach { (name, nameRecords) ->
            if (name.isEmpty()) {
                ignoredRecords += nameRecords.size
                return@forEach
            }

            val targetContact = deviceContacts.firstOrNull { contactNameMatches(it, name) }
            nameRecords.forEach { record ->
                val number = record.number.trim()
                val key = numberKey(number)
                if (key == null) {
                    ignoredRecords++
                    return@forEach
                }

                // rule 2: the number is already stored on a device contact
                if (key in existingNumbers) {
                    ignoredRecords++
                    return@forEach
                }

                // rule 3: every number can only belong to a single person
                if (!claimedNumbers.add(key)) {
                    ignoredRecords++
                    return@forEach
                }

                val phoneNumber = PhoneNumber(
                    value = number,
                    type = Phone.TYPE_MOBILE,
                    label = "",
                    normalizedNumber = ContactFilter.normalizePhoneNumber(number),
                    isPrimary = false,
                )

                if (targetContact != null) {
                    numbersToMerge.getOrPut(targetContact.id) { mutableListOf() }.add(phoneNumber)
                } else {
                    newPeople.getOrPut(name) { mutableListOf() }.add(phoneNumber)
                }
            }
        }

        return SimImportPlan(
            newContacts = newPeople.map { (name, numbers) -> SimPerson(name, numbers) },
            numbersToMerge = numbersToMerge,
            ignoredRecords = ignoredRecords,
        )
    }

    private fun contactNameMatches(contact: Contact, simName: String): Boolean {
        val contactName = concatenate(
            contact.prefix + contact.firstName + contact.middleName + contact.surname + contact.suffix
        )
        return contactName.isNotEmpty() && contactName == concatenate(simName)
    }

    private fun concatenate(name: String) = name.filterNot { it.isWhitespace() }

    private fun numberKey(number: String): String? {
        val digits = ContactFilter.normalizePhoneNumber(number).removePrefix("+")
        if (digits.isEmpty()) return null
        return if (digits.length > 11 && digits.startsWith("86")) digits.substring(2) else digits
    }
}

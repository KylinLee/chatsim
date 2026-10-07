package io.github.kylinlee.chatsim.domain

import io.github.kylinlee.chatsim.domain.model.contacts.Contact

/** Holds the separator used when joining the parts of a contact name. */
object ContactNames {
    var separator: String = ""
}

/**
 * Builds the name of the contact from its parts, falling back to the organization or email.
 * Returns an empty string when the contact has no name at all (e.g. a brand new contact).
 */
fun Contact.nameOrEmpty(): String {
    val separator = ContactNames.separator
    val parts = ArrayList<String>(5)
    if (prefix.isNotBlank()) parts.add(prefix.trim())

    if (Contact.startWithSurname) {
        if (surname.isNotBlank()) parts.add(surname.trim())
        if (firstName.isNotBlank()) parts.add(firstName.trim())
        if (middleName.isNotBlank()) parts.add(middleName.trim())
    } else {
        if (firstName.isNotBlank()) parts.add(firstName.trim())
        if (middleName.isNotBlank()) parts.add(middleName.trim())
        if (surname.isNotBlank()) parts.add(surname.trim())
    }

    if (suffix.isNotBlank()) parts.add(suffix.trim())

    val name = parts.joinToString(separator)
    if (name.isNotBlank()) return name

    val organization = getFullCompany()
    if (organization.isNotBlank()) return organization

    return emails.firstOrNull()?.value?.trim().orEmpty()
}

/**
 * Builds the name to display for the contact, joining its parts with [ContactNames.separator].
 * Falls back to the organization, email or phone number when the name is empty.
 */
fun Contact.displayName(): String {
    val name = nameOrEmpty()
    if (name.isNotBlank()) return name

    return phoneNumbers.firstOrNull()?.normalizedNumber.orEmpty()
}

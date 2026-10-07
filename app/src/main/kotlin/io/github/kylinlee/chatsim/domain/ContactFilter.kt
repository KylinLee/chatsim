package io.github.kylinlee.chatsim.domain

import io.github.kylinlee.chatsim.domain.model.contacts.Contact
import java.text.Normalizer

object ContactFilter {
    fun filter(contacts: List<Contact>, query: String): List<Contact> {
        if (query.isBlank()) return contacts

        val normalizedQuery = normalizeText(query)
        val queryDigits = normalizePhoneNumber(query)

        return contacts
            .filter { matches(it, normalizedQuery, queryDigits) }
            .sortedBy { !startsWithQuery(it, normalizedQuery) }
    }

    fun matches(contact: Contact, normalizedQuery: String, queryDigits: String): Boolean {
        if (matchesName(contact, normalizedQuery)) return true
        if (normalizeText(contact.notes).contains(normalizedQuery)) return true
        if (normalizeText(contact.organization.company).contains(normalizedQuery)) return true
        if (normalizeText(contact.organization.jobPosition).contains(normalizedQuery)) return true

        if (queryDigits.isNotEmpty() && contact.phoneNumbers.any {
                val phone = it.normalizedNumber.ifBlank { it.value }
                normalizePhoneNumber(phone).contains(queryDigits) || phoneMatches(phone, queryDigits)
            }
        ) {
            return true
        }

        if (contact.emails.any { normalizeText(it.value).contains(normalizedQuery) }) return true
        if (contact.addresses.any { normalizeText(it.value).contains(normalizedQuery) }) return true
        if (contact.IMs.any { normalizeText(it.value).contains(normalizedQuery) }) return true
        if (contact.websites.any { normalizeText(it).contains(normalizedQuery) }) return true

        return false
    }

    /**
     * Matches the displayed name ([displayName], which joins the name parts with the configured
     * separator), the raw name parts and the nickname, ignoring separators on both sides, so that
     * "张三" matches "张 三" and vice versa.
     */
    private fun matchesName(contact: Contact, normalizedQuery: String): Boolean {
        if (normalizedQuery.isEmpty()) return false

        val compactQuery = compactName(normalizedQuery)
        return nameCandidates(contact).any { name ->
            val normalizedName = normalizeText(name)
            normalizedName.contains(normalizedQuery) ||
                (compactQuery.isNotEmpty() && compactName(normalizedName).contains(compactQuery))
        }
    }

    private fun nameCandidates(contact: Contact): List<String> = listOf(
        contact.displayName(),
        contact.getNameToDisplay(),
        contact.nickname,
    )

    /** Drops whitespace and separators so that "张三" and "张 三" compare equal. */
    private fun compactName(text: String): String = text.filter { it.isLetterOrDigit() }

    fun normalizeText(text: String): String {
        if (text.isBlank()) return ""
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{Mn}+"), "").lowercase()
    }

    fun normalizePhoneNumber(number: String): String {
        val trimmed = number.trim()
        val hasPlus = trimmed.startsWith("+")
        val digits = trimmed.filter { it.isDigit() }
        return if (hasPlus) "+$digits" else digits
    }

    /**
     * Comparison keys of a number: the digits themselves plus the variants without the country code
     * and the leading trunk zero, so that "057126883077" matches "+8657126883077".
     */
    fun phoneKeys(number: String): List<String> {
        val digits = normalizePhoneNumber(number).removePrefix("+")
        if (digits.isEmpty()) return emptyList()

        val keys = linkedSetOf(digits)
        val withoutCountry = if (digits.length > 11 && digits.startsWith("86")) digits.removePrefix("86") else digits
        keys.add(withoutCountry)
        keys.add(withoutCountry.removePrefix("0"))
        return keys.toList()
    }

    fun phoneMatches(phoneNumber: String, queryDigits: String): Boolean {
        val phone = normalizePhoneNumber(phoneNumber)
        if (phone.isEmpty() || queryDigits.isEmpty()) return false
        if (phone == queryDigits) return true

        val queryKeys = phoneKeys(queryDigits)
        if (queryKeys.isNotEmpty() && phoneKeys(phone).any { it in queryKeys }) return true

        val shorter = minOf(phone.length, queryDigits.length)
        if (shorter < 7) return false

        return phone.endsWith(queryDigits) || queryDigits.endsWith(phone)
    }

    private fun startsWithQuery(contact: Contact, normalizedQuery: String): Boolean {
        val query = compactName(normalizedQuery)
        if (query.isEmpty()) return false

        return nameCandidates(contact).any { compactName(normalizeText(it)).startsWith(query) }
    }
}

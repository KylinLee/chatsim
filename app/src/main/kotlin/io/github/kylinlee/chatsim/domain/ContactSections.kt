package io.github.kylinlee.chatsim.domain

import io.github.kylinlee.chatsim.domain.model.contacts.Contact
import io.github.kylinlee.chatsim.domain.model.ContactItem

/** Contacts of one initial letter group, sorted with the unified name key. */
data class ContactSection(val letter: Char, val items: List<ContactItem>)

/**
 * Groups the contacts by their initial letter, Chinese and English mixed within a group, and puts
 * everything that is neither Chinese nor English into the '#' group at the end.
 */
fun buildContactSections(items: List<ContactItem>, nameOf: (Contact) -> String): List<ContactSection> {
    if (items.isEmpty()) return emptyList()

    val keyed = items.map { item ->
        val name = nameOf(item.contact)
        KeyedContactItem(item, ContactIndex.sortKey(name), name)
    }

    return keyed
        .groupBy { ContactIndex.sectionOfKey(it.key) }
        .map { (letter, group) ->
            ContactSection(
                letter = letter,
                items = group
                    .sortedWith(compareBy({ it.key }, { it.name }))
                    .map { it.item },
            )
        }
        .sortedWith(compareBy({ it.letter == '#' }, { it.letter }))
}

private data class KeyedContactItem(val item: ContactItem, val key: String, val name: String)

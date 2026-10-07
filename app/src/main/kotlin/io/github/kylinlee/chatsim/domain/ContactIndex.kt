package io.github.kylinlee.chatsim.domain

import io.github.kylinlee.chatsim.domain.model.contacts.Contact
import java.nio.charset.Charset

/**
 * Builds the sort/search keys of contact names and the inverted index used by the dialpad search.
 *
 * Pinyin initials are resolved by comparing the GB2312 code of a character against the boundaries
 * of the 23 pinyin initials, which is the simple, dependency free way of doing it. Comparing the
 * raw Unicode code points cannot work for this, because the Unicode order of Chinese characters is
 * unrelated to their pinyin order.
 */
object ContactIndex {
    private val gbk: Charset? = runCatching { Charset.forName("GBK") }.getOrNull()

    private val PINYIN_BOUNDARIES = intArrayOf(
        0xB0A1, 0xB0C5, 0xB2C1, 0xB4EE, 0xB6EA, 0xB7A2, 0xB8C1, 0xB9FE,
        0xBBF7, 0xBFA6, 0xC0AC, 0xC2E8, 0xC4C3, 0xC5B6, 0xC5BE, 0xC6DA,
        0xC8BB, 0xC8F6, 0xCBFA, 0xCDDA, 0xCEF4, 0xD1B9, 0xD4D1, 0xD8A0,
    )
    private const val PINYIN_INITIALS = "ABCDEFGHJKLMNOPQRSTWXYZ"
    private const val KEYPAD_LETTERS = "22233344455566677778889999"

    /** The initial letter of a single character, or null when it has none. */
    fun initialOf(char: Char): Char? = when {
        char in 'a'..'z' -> char.uppercaseChar()
        char in 'A'..'Z' -> char
        char in '\u4E00'..'\u9FFF' -> pinyinInitialOf(char)
        else -> null
    }

    /** The unified sort key of a name: English letters, digits and pinyin initials, all lowercase. */
    fun sortKey(name: String): String {
        if (name.isBlank()) return ""

        val key = StringBuilder(name.length)
        name.forEach { char ->
            when {
                char in 'a'..'z' -> key.append(char)
                char in 'A'..'Z' -> key.append(char.lowercaseChar())
                char in '0'..'9' -> key.append(char)
                else -> initialOf(char)?.let { key.append(it.lowercaseChar()) }
            }
        }
        return key.toString()
    }

    /** The group a name belongs to: A..Z, or '#' for everything that is not Chinese or English. */
    fun sectionOf(name: String): Char = sectionOfKey(sortKey(name))

    fun sectionOfKey(key: String): Char {
        val first = key.firstOrNull() ?: return '#'
        return if (first in 'a'..'z') first.uppercaseChar() else '#'
    }

    /** Converts a sort key into the digits reachable on a dialpad. */
    fun digitsOf(text: String): String {
        if (text.isBlank()) return ""

        val digits = StringBuilder(text.length)
        text.forEach { char ->
            when {
                char in '0'..'9' -> digits.append(char)
                char in 'a'..'z' -> digits.append(KEYPAD_LETTERS[char - 'a'])
                char in 'A'..'Z' -> digits.append(KEYPAD_LETTERS[char.lowercaseChar() - 'a'])
            }
        }
        return digits.toString()
    }

    private fun pinyinInitialOf(char: Char): Char? {
        val bytes = gbk?.let { char.toString().toByteArray(it) } ?: return null
        if (bytes.size != 2) return null

        val code = ((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)
        if (code < PINYIN_BOUNDARIES.first()) return null

        PINYIN_INITIALS.forEachIndexed { index, initial ->
            if (code < PINYIN_BOUNDARIES[index + 1]) return initial
        }
        return null
    }
}

/**
 * Inverted index of contact names: every prefix of the name key (letters and dialpad digits) maps
 * to the contacts whose name starts with it, so lookups match from the first character on.
 */
class ContactNameIndex {
    private val letterIndex = HashMap<String, MutableList<Contact>>()
    private val digitIndex = HashMap<String, MutableList<Contact>>()

    fun rebuild(contacts: List<Contact>, nameOf: (Contact) -> String) {
        letterIndex.clear()
        digitIndex.clear()

        contacts.forEach { contact ->
            val key = ContactIndex.sortKey(nameOf(contact))
            if (key.isEmpty()) return@forEach

            index(letterIndex, key, contact)
            index(digitIndex, ContactIndex.digitsOf(key), contact)
        }
    }

    fun search(query: String): List<Contact> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        val isDigits = trimmed.all { it in '0'..'9' }
        val index = if (isDigits) digitIndex else letterIndex
        val key = if (isDigits) trimmed else trimmed.lowercase()
        return index[key].orEmpty()
    }

    private fun index(target: MutableMap<String, MutableList<Contact>>, key: String, contact: Contact) {
        for (length in 1..key.length) {
            target.getOrPut(key.substring(0, length)) { ArrayList(1) }.add(contact)
        }
    }
}

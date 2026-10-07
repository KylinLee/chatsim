package io.github.kylinlee.chatsim.domain

import io.github.kylinlee.chatsim.domain.model.PhoneNumber
import io.github.kylinlee.chatsim.domain.model.contacts.Contact
import io.github.kylinlee.chatsim.domain.model.contacts.Email
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactFilterTest {
    @After
    fun tearDown() {
        ContactNames.separator = ""
        Contact.startWithSurname = false
    }
    @Test
    fun normalizeTextRemovesDiacriticsAndLowercases() {
        assertEquals("jose", ContactFilter.normalizeText("José"))
        assertEquals("zhang san", ContactFilter.normalizeText("Zhang Sàn"))
    }

    @Test
    fun normalizePhoneNumberKeepsPlusAndDigits() {
        assertEquals("+8613800000000", ContactFilter.normalizePhoneNumber("+86 138-0000-0000"))
        assertEquals("13800000000", ContactFilter.normalizePhoneNumber("138 0000 0000"))
    }

    @Test
    fun phoneMatchesComparesSuffixes() {
        assertTrue(ContactFilter.phoneMatches("8613800000000", "13800000000"))
        assertTrue(ContactFilter.phoneMatches("13800000000", "13800000000"))
        assertFalse(ContactFilter.phoneMatches("110", "13800000000"))
    }

    @Test
    fun phoneMatchesIgnoresCountryCodeAndTrunkZero() {
        assertTrue(ContactFilter.phoneMatches("+8657126883077", "057126883077"))
        assertTrue(ContactFilter.phoneMatches("057126883077", "+8657126883077"))
        assertTrue(ContactFilter.phoneMatches("8613800000000", "+8613800000000"))
        assertTrue(ContactFilter.phoneMatches("+8613800000000", "13800000000"))
        assertFalse(ContactFilter.phoneMatches("+8657126883077", "057126883078"))
    }

    @Test
    fun phoneKeysCoverCountryCodeAndTrunkZeroVariants() {
        assertTrue(ContactFilter.phoneKeys("+8657126883077").contains("57126883077"))
        assertTrue(ContactFilter.phoneKeys("057126883077").contains("57126883077"))
        assertTrue(ContactFilter.phoneKeys("+8613800000000").contains("13800000000"))
        assertTrue(ContactFilter.phoneKeys("").isEmpty())
    }

    @Test
    fun filterMatchesLandlineStoredInInternationalFormat() {
        val alilang = contact(name = "阿里郎电话", numbers = listOf("+8657126883077"))

        assertEquals(listOf(alilang), ContactFilter.filter(listOf(alilang), "057126883077"))
    }

    @Test
    fun filterMatchesNameNumberAndEmail() {
        val alice = contact(
            name = "Alice Zhang",
            numbers = listOf("13800000000"),
            emails = listOf("alice@example.com"),
        )
        val bob = contact(name = "Bob Li", numbers = listOf("13900000000"))
        val contacts = listOf(alice, bob)

        assertEquals(listOf(alice), ContactFilter.filter(contacts, "alice"))
        assertEquals(listOf(alice), ContactFilter.filter(contacts, "13800000000"))
        assertEquals(listOf(alice), ContactFilter.filter(contacts, "alice@example.com"))
        assertEquals(listOf(bob), ContactFilter.filter(contacts, "1390"))
    }

    @Test
    fun filterReturnsAllForBlankQuery() {
        val contacts = listOf(contact(name = "Alice"), contact(name = "Bob"))
        assertEquals(contacts, ContactFilter.filter(contacts, " "))
    }

    @Test
    fun filterMatchesNameWithoutSeparator() {
        ContactNames.separator = ""
        Contact.startWithSurname = true
        val zhangSan = namedContact(surname = "张", firstName = "三")

        assertEquals(listOf(zhangSan), ContactFilter.filter(listOf(zhangSan), "张三"))
        assertEquals(listOf(zhangSan), ContactFilter.filter(listOf(zhangSan), "张 三"))
    }

    @Test
    fun filterMatchesNameWithConfiguredSeparator() {
        ContactNames.separator = " "
        Contact.startWithSurname = true
        val zhangSan = namedContact(surname = "张", firstName = "三")

        assertEquals(listOf(zhangSan), ContactFilter.filter(listOf(zhangSan), "张三"))
        assertEquals(listOf(zhangSan), ContactFilter.filter(listOf(zhangSan), "张 三"))
    }

    @Test
    fun filterMatchesNameWhenSurnameIsLast() {
        ContactNames.separator = "·"
        Contact.startWithSurname = false
        val zhangSan = namedContact(surname = "张", firstName = "三")

        assertEquals(listOf(zhangSan), ContactFilter.filter(listOf(zhangSan), "三·张"))
        assertEquals(listOf(zhangSan), ContactFilter.filter(listOf(zhangSan), "三张"))
    }

    private fun namedContact(
        prefix: String = "",
        firstName: String = "",
        middleName: String = "",
        surname: String = "",
        suffix: String = "",
    ): Contact {
        val contact = mockk<Contact>(relaxed = true)
        every { contact.prefix } returns prefix
        every { contact.firstName } returns firstName
        every { contact.middleName } returns middleName
        every { contact.surname } returns surname
        every { contact.suffix } returns suffix
        every { contact.getNameToDisplay() } returns listOf(prefix, firstName, middleName, surname, suffix)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        every { contact.nickname } returns ""
        every { contact.notes } returns ""
        every { contact.phoneNumbers } returns ArrayList()
        every { contact.emails } returns ArrayList()
        every { contact.addresses } returns ArrayList()
        every { contact.IMs } returns ArrayList()
        every { contact.websites } returns ArrayList()
        return contact
    }

    private fun contact(
        name: String,
        numbers: List<String> = emptyList(),
        emails: List<String> = emptyList(),
    ): Contact {
        val contact = mockk<Contact>(relaxed = true)
        every { contact.getNameToDisplay() } returns name
        every { contact.name } returns name
        every { contact.nickname } returns ""
        every { contact.notes } returns ""
        every { contact.phoneNumbers } returns ArrayList(numbers.map { PhoneNumber(it, 0, "", it) })
        every { contact.emails } returns ArrayList(emails.map { Email(it, 0, "") })
        every { contact.addresses } returns ArrayList()
        every { contact.IMs } returns ArrayList()
        every { contact.websites } returns ArrayList()
        return contact
    }
}

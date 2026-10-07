package io.github.kylinlee.chatsim.domain

import io.github.kylinlee.chatsim.domain.model.PhoneNumber
import io.github.kylinlee.chatsim.domain.model.contacts.Contact
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class ContactNamesTest {
    @After
    fun tearDown() {
        ContactNames.separator = ""
        Contact.startWithSurname = false
    }

    @Test
    fun namelessContactOnlyFallsBackToThePhoneNumberForDisplayName() {
        val contact = contact(phoneNumber = "13800000000")

        assertEquals("", contact.nameOrEmpty())
        assertEquals("13800000000", contact.displayName())
    }

    @Test
    fun namePartsAreJoinedWithTheConfiguredSeparator() {
        val contact = contact(firstName = "San", surname = "Zhang")

        ContactNames.separator = " "
        assertEquals("San Zhang", contact.nameOrEmpty())

        ContactNames.separator = ""
        assertEquals("SanZhang", contact.nameOrEmpty())
    }

    @Test
    fun surnameFirstSettingChangesTheOrder() {
        val contact = contact(firstName = "San", surname = "Zhang")

        Contact.startWithSurname = true
        assertEquals("ZhangSan", contact.nameOrEmpty())
    }

    @Test
    fun organizationIsUsedWhenTheNameIsEmpty() {
        val contact = contact(phoneNumber = "13800000000").copy(
            organization = io.github.kylinlee.chatsim.domain.model.contacts.Organization("Acme", ""),
        )

        assertEquals("Acme", contact.nameOrEmpty())
    }

    private fun contact(firstName: String = "", surname: String = "", phoneNumber: String = ""): Contact = Contact(
        id = 1,
        contactId = 1,
        firstName = firstName,
        surname = surname,
        phoneNumbers = ArrayList<PhoneNumber>().apply {
            if (phoneNumber.isNotEmpty()) add(PhoneNumber(phoneNumber, 0, "", phoneNumber))
        },
    )
}

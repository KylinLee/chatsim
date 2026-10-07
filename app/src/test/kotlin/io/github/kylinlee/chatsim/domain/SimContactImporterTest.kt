package io.github.kylinlee.chatsim.domain

import io.github.kylinlee.chatsim.domain.model.PhoneNumber
import io.github.kylinlee.chatsim.domain.model.contacts.Contact
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SimContactImporterTest {
    @Test
    fun mergesNumbersOfRecordsWithTheSameName() {
        val plan = SimContactImporter.plan(
            records = listOf(
                SimContactRecord("张三", "13800000001"),
                SimContactRecord("张三", "13800000002"),
            ),
            deviceContacts = emptyList(),
        )

        assertEquals(1, plan.newContacts.size)
        assertEquals("张三", plan.newContacts.first().name)
        assertEquals(
            listOf("13800000001", "13800000002"),
            plan.newContacts.first().numbers.map { it.value },
        )
        assertEquals(0, plan.ignoredRecords)
    }

    @Test
    fun mergesNumbersIntoAnExistingContactWithTheSameName() {
        val existing = contact(id = 5, firstName = "王五", numbers = listOf("13900000001"))
        val plan = SimContactImporter.plan(
            records = listOf(SimContactRecord("王 五", "13900000002")),
            deviceContacts = listOf(existing),
        )

        assertEquals(0, plan.newContacts.size)
        assertEquals(listOf("13900000002"), plan.numbersToMerge[5]?.map { it.value })
        assertEquals(0, plan.ignoredRecords)
    }

    @Test
    fun ignoresRecordsWithNumbersAlreadyOnTheDevice() {
        val existing = contact(id = 1, firstName = "李四", numbers = listOf("13800000001"))
        val plan = SimContactImporter.plan(
            records = listOf(
                SimContactRecord("李四", "13800000001"),
                SimContactRecord("李四", "13800000002"),
            ),
            deviceContacts = listOf(existing),
        )

        assertEquals(0, plan.newContacts.size)
        assertEquals(listOf("13800000002"), plan.numbersToMerge[1]?.map { it.value })
        assertEquals(1, plan.ignoredRecords)
    }

    @Test
    fun ignoresTheCountryCodeWhenMatchingExistingNumbers() {
        val existing = contact(id = 2, firstName = "赵六", numbers = listOf("+8613800000003"))
        val plan = SimContactImporter.plan(
            records = listOf(SimContactRecord("赵六", "13800000003")),
            deviceContacts = listOf(existing),
        )

        assertEquals(0, plan.newContacts.size)
        assertTrue(plan.numbersToMerge.isEmpty())
        assertEquals(1, plan.ignoredRecords)
    }

    @Test
    fun aNumberBelongsToASinglePersonOnly() {
        val plan = SimContactImporter.plan(
            records = listOf(
                SimContactRecord("张三", "13800000001"),
                SimContactRecord("李四", "13800000001"),
            ),
            deviceContacts = emptyList(),
        )

        assertEquals(1, plan.newContacts.size)
        assertEquals("张三", plan.newContacts.first().name)
        assertEquals(1, plan.ignoredRecords)
    }

    @Test
    fun ignoresRecordsWithoutANumber() {
        val plan = SimContactImporter.plan(
            records = listOf(
                SimContactRecord("", ""),
                SimContactRecord("张三", ""),
            ),
            deviceContacts = emptyList(),
        )

        assertEquals(0, plan.newContacts.size)
        assertEquals(2, plan.ignoredRecords)
    }

    private fun contact(id: Int, firstName: String, numbers: List<String>): Contact {
        val contact = mockk<Contact>(relaxed = true)
        every { contact.id } returns id
        every { contact.prefix } returns ""
        every { contact.firstName } returns firstName
        every { contact.middleName } returns ""
        every { contact.surname } returns ""
        every { contact.suffix } returns ""
        every { contact.phoneNumbers } returns ArrayList(
            numbers.map { PhoneNumber(it, 2, "", ContactFilter.normalizePhoneNumber(it)) }
        )
        return contact
    }
}

package io.github.kylinlee.chatsim.domain

import io.github.kylinlee.chatsim.domain.model.contacts.Contact
import io.github.kylinlee.chatsim.domain.model.ContactItem
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ContactIndexTest {
    @Before
    fun setUp() {
        Contact.startWithSurname = true
    }

    @After
    fun tearDown() {
        Contact.startWithSurname = false
    }

    @Test
    fun englishNamesUseTheirOwnInitial() {
        assertEquals('A', ContactIndex.sectionOf("Alice"))
        assertEquals('Z', ContactIndex.sectionOf("zoe"))
        assertEquals('D', ContactIndex.sectionOf("Dr. Alice"))
    }

    @Test
    fun chineseNamesUseTheirPinyinInitial() {
        assertEquals('A', ContactIndex.sectionOf("阿姨"))
        assertEquals('L', ContactIndex.sectionOf("李四"))
        assertEquals('W', ContactIndex.sectionOf("王彪"))
        assertEquals('Z', ContactIndex.sectionOf("张三"))
    }

    @Test
    fun namesWithoutLettersGoToTheHashGroup() {
        assertEquals('#', ContactIndex.sectionOf("10655736"))
        assertEquals('#', ContactIndex.sectionOf("★★★"))
        assertEquals('#', ContactIndex.sectionOf(""))
        assertEquals('V', ContactIndex.sectionOf("★VIP"))
    }

    @Test
    fun sortKeyMixesChineseInitialsAndEnglishLetters() {
        assertEquals("alice", ContactIndex.sortKey("Alice"))
        assertEquals("zs", ContactIndex.sortKey("张三"))
        assertEquals("zsalice", ContactIndex.sortKey("张三 Alice"))
    }

    @Test
    fun digitsFollowTheDialpadMapping() {
        assertEquals("25423", ContactIndex.digitsOf("alice"))
        assertEquals("97", ContactIndex.digitsOf("zs"))
        assertEquals("21", ContactIndex.digitsOf("a1"))
    }

    @Test
    fun indexMatchesNamesFromTheFirstCharacter() {
        val zhang = contact(id = 1, firstName = "三", surname = "张")
        val wang = contact(id = 2, firstName = "彪", surname = "王")
        val alice = contact(id = 3, firstName = "Alice")
        val index = ContactNameIndex()
        index.rebuild(listOf(zhang, wang, alice)) { it.displayName() }

        assertEquals(listOf(zhang, wang), index.search("9"))
        assertEquals(listOf(zhang), index.search("97"))
        assertEquals(listOf(alice), index.search("254"))
        assertTrue(index.search("5").isEmpty())
        assertEquals(listOf(alice), index.search("ali"))
        assertTrue(index.search("lice").isEmpty())
    }

    @Test
    fun sectionsGroupByInitialWithHashLast() {
        val items = listOf(
            item(contact(id = 1, firstName = "三", surname = "张")),
            item(contact(id = 2, firstName = "Alice")),
            item(contact(id = 3, firstName = "彪", surname = "王")),
            item(contact(id = 4, firstName = "10655736")),
        )

        val sections = buildContactSections(items) { it.displayName() }

        assertEquals(listOf('A', 'W', 'Z', '#'), sections.map { it.letter })
        assertEquals(listOf(2), sections.first { it.letter == 'A' }.items.map { it.contact.id })
        assertEquals(listOf(4), sections.last().items.map { it.contact.id })
    }

    @Test
    fun surnameFirstSettingChangesTheIndexKey() {
        val contact = contact(firstName = "三", surname = "张")
        val original = Contact.startWithSurname
        try {
            Contact.startWithSurname = true
            assertEquals("zs", ContactIndex.sortKey(contact.displayName()))

            Contact.startWithSurname = false
            assertEquals("sz", ContactIndex.sortKey(contact.displayName()))
        } finally {
            Contact.startWithSurname = original
        }
    }

    private fun item(contact: Contact) = ContactItem(contact = contact, isPinned = false, lastContactTime = 0L)

    private fun contact(id: Int = 0, firstName: String = "", surname: String = ""): Contact {
        val contact = mockk<Contact>(relaxed = true)
        every { contact.id } returns id
        every { contact.firstName } returns firstName
        every { contact.surname } returns surname
        every { contact.prefix } returns ""
        every { contact.middleName } returns ""
        every { contact.suffix } returns ""
        every { contact.emails } returns ArrayList()
        every { contact.phoneNumbers } returns ArrayList()
        return contact
    }
}

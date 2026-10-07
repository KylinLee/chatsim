package io.github.kylinlee.chatsim.data.legacy


import io.github.kylinlee.chatsim.common.*
import android.content.Context
import android.net.Uri
import android.telecom.Call
import io.github.kylinlee.chatsim.common.extensions.getPhoneNumberTypeText
import io.github.kylinlee.chatsim.data.contacts.ContactsHelper
import io.github.kylinlee.chatsim.common.ensureBackgroundThread
import io.github.kylinlee.chatsim.R
import io.github.kylinlee.chatsim.common.extensions.isConference
import io.github.kylinlee.chatsim.data.local.SharedPrefsRoleStore
import io.github.kylinlee.chatsim.data.model.CallContact
import io.github.kylinlee.chatsim.domain.RolePrefixer
import io.github.kylinlee.chatsim.domain.displayName
import io.github.kylinlee.chatsim.domain.model.RoleChannel

fun getCallContact(context: Context, call: Call?, callback: (CallContact) -> Unit) {
    if (call.isConference()) {
        callback(CallContact(context.getString(R.string.conference), "", "", ""))
        return
    }

    ensureBackgroundThread {
        val callContact = CallContact("", "", "", "")
        val handle = try {
            call?.details?.handle?.toString()
        } catch (e: NullPointerException) {
            null
        }

        if (handle == null) {
            callback(callContact)
            return@ensureBackgroundThread
        }

        val uri = Uri.decode(handle)
        if (uri.startsWith("tel:")) {
            val rawNumber = uri.substringAfter("tel:")
            // 小号来电/拨号的号码带前缀，界面只显示真实号码
            val roles = SharedPrefsRoleStore(context).loadRoles()
            val number = RolePrefixer.stripPrefix(rawNumber, roles, RoleChannel.CALL)
            ContactsHelper(context).getContacts(showOnlyContactsWithNumbers = true) { contacts ->
                val contactsWithMultipleNumbers = contacts.filter { it.phoneNumbers.size > 1 }
                val numbersToContactIDMap = HashMap<String, Int>()
                contactsWithMultipleNumbers.forEach { contact ->
                    contact.phoneNumbers.forEach { phoneNumber ->
                        numbersToContactIDMap[phoneNumber.value] = contact.contactId
                        numbersToContactIDMap[phoneNumber.normalizedNumber] = contact.contactId
                    }
                }

                callContact.number = number
                val contact = contacts.firstOrNull { it.doesHavePhoneNumber(number) }
                if (contact != null) {
                    callContact.name = contact.displayName()
                    callContact.photoUri = contact.photoUri

                    if (contact.phoneNumbers.size > 1) {
                        val specificPhoneNumber = contact.phoneNumbers.firstOrNull { it.value == number }
                        if (specificPhoneNumber != null) {
                            callContact.numberLabel = context.getPhoneNumberTypeText(specificPhoneNumber.type, specificPhoneNumber.label)
                        }
                    }
                } else {
                    callContact.name = number
                }
                callback(callContact)
            }
        }
    }
}

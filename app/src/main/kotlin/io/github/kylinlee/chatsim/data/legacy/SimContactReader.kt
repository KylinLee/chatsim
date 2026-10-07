package io.github.kylinlee.chatsim.data.legacy

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.SubscriptionManager
import io.github.kylinlee.chatsim.domain.SimContactRecord

/** Reads the contacts stored on the SIM card. */
class SimContactReader(private val context: Context) {
    fun readContacts(): List<SimContactRecord> {
        val records = ArrayList<SimContactRecord>()
        records.addAll(readFromIccProvider())
        if (records.isEmpty()) {
            records.addAll(readFromContactsProvider())
        }

        return records
            .map { SimContactRecord(it.name.trim(), it.number.trim()) }
            .filter { it.name.isNotEmpty() || it.number.isNotEmpty() }
            .distinctBy { it.name to it.number }
    }

    private fun readFromIccProvider(): List<SimContactRecord> {
        val records = ArrayList<SimContactRecord>()
        adnUris().forEach { uri ->
            records.addAll(queryAdn(uri))
        }
        return records
    }

    private fun queryAdn(uri: Uri): List<SimContactRecord> {
        val records = ArrayList<SimContactRecord>()
        val projections = listOf(
            arrayOf("_id", "name", "number"),
            arrayOf("name", "number"),
            null,
        )

        projections.forEach { projection ->
            runCatching {
                val cursor = context.contentResolver.query(uri, projection, null, null, null) ?: return@runCatching
                cursor.use {
                    val nameIndex = it.getColumnIndex("name").takeIf { index -> index >= 0 }
                        ?: it.getColumnIndex("tag")
                    val numberIndex = it.getColumnIndex("number")
                    if (nameIndex < 0 || numberIndex < 0) return@use

                    while (it.moveToNext()) {
                        val name = it.getString(nameIndex).orEmpty().trim()
                        val number = it.getString(numberIndex).orEmpty().trim()
                        if (name.isNotEmpty() || number.isNotEmpty()) {
                            records.add(SimContactRecord(name, number))
                        }
                    }
                }
            }
            if (records.isNotEmpty()) return records
        }
        return records
    }

    private fun adnUris(): List<Uri> {
        val uris = arrayListOf(Uri.parse("content://icc/adn"))
        runCatching {
            val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            subscriptionManager?.activeSubscriptionInfoList?.forEach { info ->
                uris.add(Uri.parse("content://icc/adn/subId/${info.subscriptionId}"))
            }
        }
        return uris
    }

    private fun readFromContactsProvider(): List<SimContactRecord> {
        val records = ArrayList<SimContactRecord>()
        runCatching {
            val rawContactIds = ArrayList<Long>()
            context.contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(ContactsContract.RawContacts._ID),
                "${ContactsContract.RawContacts.ACCOUNT_TYPE} LIKE ?",
                arrayOf("%sim%"),
                null,
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    rawContactIds.add(cursor.getLong(0))
                }
            }
            if (rawContactIds.isEmpty()) return records

            val names = HashMap<Long, String>()
            val numbers = HashMap<Long, MutableList<String>>()
            val selection = "${ContactsContract.Data.RAW_CONTACT_ID} IN (${rawContactIds.joinToString(",")}) AND " +
                "(${ContactsContract.Data.MIMETYPE} = ? OR ${ContactsContract.Data.MIMETYPE} = ?)"
            val selectionArgs = arrayOf(
                ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
            )

            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                null,
                selection,
                selectionArgs,
                null,
            )?.use { cursor ->
                val rawIdIndex = cursor.getColumnIndex(ContactsContract.Data.RAW_CONTACT_ID)
                val mimeTypeIndex = cursor.getColumnIndex(ContactsContract.Data.MIMETYPE)
                val dataIndex = cursor.getColumnIndex(ContactsContract.Data.DATA1)
                if (rawIdIndex < 0 || mimeTypeIndex < 0 || dataIndex < 0) return@use

                while (cursor.moveToNext()) {
                    val rawId = cursor.getLong(rawIdIndex)
                    val value = cursor.getString(dataIndex).orEmpty()
                    when (cursor.getString(mimeTypeIndex)) {
                        ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> names[rawId] = value
                        ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE ->
                            numbers.getOrPut(rawId) { mutableListOf() }.add(value)
                    }
                }
            }

            rawContactIds.forEach { id ->
                val name = names[id].orEmpty()
                val phones = numbers[id].orEmpty()
                if (phones.isEmpty()) {
                    if (name.isNotEmpty()) records.add(SimContactRecord(name, ""))
                } else {
                    phones.forEach { records.add(SimContactRecord(name, it)) }
                }
            }
        }
        return records
    }
}

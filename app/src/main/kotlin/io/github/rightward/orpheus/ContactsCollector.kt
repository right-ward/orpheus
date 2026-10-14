package io.github.rightward.orpheus

import android.content.ContentResolver
import android.provider.ContactsContract
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class ContactsCollector(
    private val resolver: ContentResolver
) {
    fun collect(): ContactsSnapshot {
        val types = CONTACT_MIME_TYPES
        val projection = arrayOf(
            ContactsContract.Data.CONTACT_ID,
            ContactsContract.Data.MIMETYPE,
            ContactsContract.Data.DATA1,
            ContactsContract.Data.DATA2,
            ContactsContract.Data.DATA3,
            ContactsContract.Data.DATA4,
            ContactsContract.Data.DATA5,
            ContactsContract.Data.DATA6
        )
        val selection = ContactsContract.Data.MIMETYPE + " IN (" +
            types.joinToString(",") { "?" } + ")"

        val grouped = LinkedHashMap<Long, MutableContact>()
        val cursor = resolver.query(
            ContactsContract.Data.CONTENT_URI,
            projection,
            selection,
            types.toTypedArray(),
            ContactsContract.Data.CONTACT_ID + " ASC"
        ) ?: throw IOException("Contacts Provider returned no cursor.")

        cursor.use { rows ->
            val idIndex = rows.getColumnIndexOrThrow(ContactsContract.Data.CONTACT_ID)
            val mimeIndex = rows.getColumnIndexOrThrow(ContactsContract.Data.MIMETYPE)
            val data1Index = rows.getColumnIndexOrThrow(ContactsContract.Data.DATA1)
            val data2Index = rows.getColumnIndexOrThrow(ContactsContract.Data.DATA2)
            val data3Index = rows.getColumnIndexOrThrow(ContactsContract.Data.DATA3)
            val data4Index = rows.getColumnIndexOrThrow(ContactsContract.Data.DATA4)
            val data5Index = rows.getColumnIndexOrThrow(ContactsContract.Data.DATA5)
            val data6Index = rows.getColumnIndexOrThrow(ContactsContract.Data.DATA6)

            while (rows.moveToNext()) {
                val id = rows.getLong(idIndex)
                val mime = rows.getString(mimeIndex) ?: continue
                val data1 = rows.getString(data1Index)
                val data2 = rows.getString(data2Index)?.toIntOrNull()
                val data3 = rows.getString(data3Index)?.takeIf { it.isNotBlank() }
                val data4 = rows.getString(data4Index)?.takeIf { it.isNotBlank() }
                val data5 = rows.getString(data5Index)?.takeIf { it.isNotBlank() }
                val data6 = rows.getString(data6Index)?.takeIf { it.isNotBlank() }
                val contact = grouped.getOrPut(id) { MutableContact() }

                when (mime) {
                    ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE -> {
                        if (contact.displayName.isNullOrBlank()) {
                            contact.displayName = data1?.takeIf { it.isNotBlank() }
                                ?: listOfNotNull(data4, data2?.let { null }, data5, data6)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" ")
                                    .takeIf { it.isNotBlank() }
                        }
                    }

                    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE ->
                        addField(contact, "phone", data1, data2, data3)

                    ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE ->
                        addField(contact, "email", data1, data2, data3)

                    ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE ->
                        addField(contact, "postal_address", data1, data2, data3)

                    ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE ->
                        addField(
                            contact,
                            "organization",
                            data1,
                            data2,
                            data3,
                            secondaryValue = data4
                        )

                    ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE ->
                        addField(contact, "nickname", data1, data2, data3)

                    ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE ->
                        addField(contact, "event", data1, data2, data3)

                    ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE ->
                        addField(contact, "website", data1, data2, data3)

                    ContactsContract.CommonDataKinds.Relation.CONTENT_ITEM_TYPE ->
                        addField(contact, "relation", data1, data2, data3)
                }
            }
        }

        val records = grouped.values
            .filter { !it.displayName.isNullOrBlank() || it.fields.isNotEmpty() }
            .map { ContactRecord(it.displayName, it.fields.toList()) }

        return ContactsSnapshotCodec.encode(records, nowUtc())
    }

    private fun addField(
        contact: MutableContact,
        kind: String,
        value: String?,
        type: Int?,
        label: String?,
        secondaryValue: String? = null
    ) {
        val normalized = value?.trim()?.takeIf { it.isNotEmpty() } ?: return
        contact.fields += ContactField(
            kind = kind,
            value = normalized,
            type = type,
            label = label,
            secondaryValue = secondaryValue
        )
    }

    private fun nowUtc(): String =
        SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            Locale.US
        ).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

    private class MutableContact {
        var displayName: String? = null
        val fields = ArrayList<ContactField>()
    }

    private companion object {
        val CONTACT_MIME_TYPES = listOf(
            ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE,
            ContactsContract.CommonDataKinds.Relation.CONTENT_ITEM_TYPE
        )
    }
}

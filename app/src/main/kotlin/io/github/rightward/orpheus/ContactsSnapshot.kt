package io.github.rightward.orpheus

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

enum class ContactsExportOutcome {
    NOT_RUN,
    SUCCEEDED,
    FAILED
}

data class ContactField(
    val kind: String,
    val value: String,
    val type: Int? = null,
    val label: String? = null,
    val secondaryValue: String? = null
)

data class ContactRecord(
    val displayName: String?,
    val fields: List<ContactField>
)

data class ContactsSnapshot(
    val json: String,
    val contactCount: Int,
    val fieldCount: Int
)

object ContactsSnapshotCodec {
    const val SCHEMA = "orpheus.android.contacts.snapshot"
    const val SCHEMA_VERSION = 1

    private val LIMITATIONS = listOf(
        "Export includes only the documented contact data types.",
        "Contact photos, notes, account metadata, raw-contact IDs, and unsupported fields are omitted.",
        "This snapshot is not a complete Contacts Provider image.",
        "Automatic restoration is not implemented."
    )

    fun encode(
        contacts: List<ContactRecord>,
        collectedAtUtc: String
    ): ContactsSnapshot {
        val normalizedContacts = contacts
            .map { contact ->
                contact.copy(
                    displayName = contact.displayName
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() },
                    fields = contact.fields
                        .filter { it.value.isNotBlank() }
                        .sortedWith(
                            compareBy<ContactField>(
                                { it.kind },
                                { it.type ?: -1 },
                                { it.label.orEmpty().lowercase(Locale.ROOT) },
                                { it.value }
                            )
                        )
                )
            }
            .sortedWith(
                compareBy<ContactRecord>(
                    { it.displayName.orEmpty().lowercase(Locale.ROOT) },
                    { it.fields.firstOrNull()?.value.orEmpty() }
                )
            )

        val contactArray = JSONArray()
        var fieldCount = 0

        normalizedContacts.forEach { contact ->
            val fieldArray = JSONArray()
            contact.fields.forEach { field ->
                val item = JSONObject()
                    .put("kind", field.kind)
                    .put("value", field.value)
                field.type?.let { item.put("type", it) }
                field.label?.takeIf { it.isNotBlank() }?.let {
                    item.put("label", it)
                }
                field.secondaryValue?.takeIf { it.isNotBlank() }?.let {
                    item.put("secondary_value", it)
                }
                fieldArray.put(item)
                fieldCount++
            }

            val item = JSONObject()
                .put("display_name", contact.displayName ?: JSONObject.NULL)
                .put("fields", fieldArray)
            contactArray.put(item)
        }

        val limitations = JSONArray()
        LIMITATIONS.forEach { limitations.put(it) }

        val json = JSONObject()
            .put("schema", SCHEMA)
            .put("schema_version", SCHEMA_VERSION)
            .put("collected_at", collectedAtUtc)
            .put("source", "android.contacts")
            .put("coverage", "allowlisted_contact_data_types")
            .put("restore_supported", false)
            .put("limitations", limitations)
            .put("contact_count", normalizedContacts.size)
            .put("field_count", fieldCount)
            .put("contacts", contactArray)
            .toString(2)

        return ContactsSnapshot(
            json = json,
            contactCount = normalizedContacts.size,
            fieldCount = fieldCount
        )
    }
}

package io.github.rightward.orpheus

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactsSnapshotCodecTest {
    @Test
    fun encodesNormalizedContactsAndDocumentsPartialCoverage() {
        val snapshot = ContactsSnapshotCodec.encode(
            contacts = listOf(
                ContactRecord(
                    displayName = "  Zoe Example  ",
                    fields = listOf(
                        ContactField("phone", "+1 555 0100", type = 2, label = "Mobile"),
                        ContactField("email", "zoe@example.test")
                    )
                ),
                ContactRecord(
                    displayName = "Ada Example",
                    fields = listOf(ContactField("nickname", "Ada"))
                )
            ),
            collectedAtUtc = "2026-10-15T00:00:00Z"
        )

        val root = JSONObject(snapshot.json)
        val contacts = root.getJSONArray("contacts")
        val first = contacts.getJSONObject(0)

        assertEquals(ContactsSnapshotCodec.SCHEMA, root.getString("schema"))
        assertEquals(1, root.getInt("schema_version"))
        assertEquals("allowlisted_contact_data_types", root.getString("coverage"))
        assertEquals(false, root.getBoolean("restore_supported"))
        assertEquals(2, snapshot.contactCount)
        assertEquals(3, snapshot.fieldCount)
        assertEquals(2, contacts.length())
        assertEquals("Ada Example", first.getString("display_name"))
        assertTrue(root.getJSONArray("limitations").length() >= 3)
        assertFalse(snapshot.json.contains("raw_contact_id"))
    }

    @Test
    fun supportsAnEmptyContactsProviderResult() {
        val snapshot = ContactsSnapshotCodec.encode(
            contacts = emptyList(),
            collectedAtUtc = "2026-10-15T00:00:00Z"
        )

        val root = JSONObject(snapshot.json)
        assertEquals(0, snapshot.contactCount)
        assertEquals(0, snapshot.fieldCount)
        assertEquals(0, root.getJSONArray("contacts").length())
    }

    @Test
    fun omitsBlankFieldsAndNormalizesOptionalLabels() {
        val snapshot = ContactsSnapshotCodec.encode(
            contacts = listOf(
                ContactRecord(
                    displayName = "",
                    fields = listOf(
                        ContactField("phone", "   "),
                        ContactField("email", "a@example.test", label = "Work")
                    )
                )
            ),
            collectedAtUtc = "2026-10-15T00:00:00Z"
        )

        val contact = JSONObject(snapshot.json)
            .getJSONArray("contacts")
            .getJSONObject(0)
        assertTrue(contact.isNull("display_name"))
        assertEquals(1, contact.getJSONArray("fields").length())
    }
}

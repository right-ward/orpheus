package io.github.rightward.orpheus

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShizukuSystemSettingsCollectorTest {
    @Test
    fun normalizesOnlyTheAllowListAndKeepsPerSettingStatuses() {
        val raw = rawReport(
            statuses = mapOf(
                "system.font_scale" to "available",
                "system.screen_off_timeout" to "failed"
            ),
            values = mapOf("system.font_scale" to "1.25"),
            extraSecret = true
        )

        val snapshot = ShizukuSystemSettingsCollector.normalize(
            rawReport = raw,
            expectedUid = 2000,
            collectedAtUtc = "2026-10-09T00:00:00Z"
        )
        val json = JSONObject(snapshot.json)
        val system = json.getJSONObject("settings").getJSONObject("system")

        assertEquals(1, snapshot.readableCount)
        assertEquals(9, snapshot.notSetCount)
        assertEquals(1, snapshot.failedCount)
        assertEquals(11, snapshot.totalCount)
        assertEquals("1.25", system.getJSONObject("font_scale").getString("value"))
        assertEquals(
            "failed",
            system.getJSONObject("screen_off_timeout").getString("status")
        )
        assertEquals(
            "SETTING_READ_FAILED",
            system.getJSONObject("screen_off_timeout").getString("error_code")
        )
        assertFalse(system.has("password"))
        assertFalse(snapshot.json.contains("must-not-be-exported"))
        assertEquals(false, json.getBoolean("restore_supported"))
    }

    @Test
    fun rejectsAUserServiceUidThatDoesNotMatchTheReportedServerUid() {
        val error = runCatching {
            ShizukuSystemSettingsCollector.normalize(
                rawReport = rawReport(uid = 0),
                expectedUid = 2000,
                collectedAtUtc = "2026-10-09T00:00:00Z"
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun rejectsNonScalarAllowListedValues() {
        val error = runCatching {
            ShizukuSystemSettingsCollector.normalize(
                rawReport(
                    statuses = mapOf("system.font_scale" to "available"),
                    values = mapOf("system.font_scale" to "1.0\\npassword=secret")
                ),
                expectedUid = 2000,
                collectedAtUtc = "2026-10-09T00:00:00Z"
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun rejectsAnOversizedAllowListedValue() {
        val error = runCatching {
            ShizukuSystemSettingsCollector.normalize(
                rawReport = rawReport(
                    statuses = mapOf("system.font_scale" to "available"),
                    values = mapOf("system.font_scale" to "x".repeat(4097))
                ),
                expectedUid = 2000,
                collectedAtUtc = "2026-10-09T00:00:00Z"
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }

    private fun rawReport(
        uid: Int = 2000,
        statuses: Map<String, String> = emptyMap(),
        values: Map<String, String> = emptyMap(),
        extraSecret: Boolean = false
    ): String {
        val allSettings = JSONObject()
        ShizukuSystemSettingsCollector.ALLOWLIST.forEach { (namespace, keys) ->
            val namespaceSettings = JSONObject()
            keys.forEach { key ->
                val id = "$namespace.$key"
                when (statuses[id] ?: "not_set") {
                    "available" -> namespaceSettings.put(
                        key,
                        JSONObject()
                            .put("status", "available")
                            .put("value", values[id] ?: "1")
                    )
                    "failed" -> namespaceSettings.put(
                        key,
                        JSONObject()
                            .put("status", "failed")
                            .put("error_code", "SETTING_READ_FAILED")
                    )
                    else -> namespaceSettings.put(
                        key,
                        JSONObject().put("status", "not_set")
                    )
                }
            }
            if (extraSecret && namespace == "system") {
                namespaceSettings.put(
                    "password",
                    JSONObject()
                        .put("status", "available")
                        .put("value", "must-not-be-exported")
                )
            }
            allSettings.put(namespace, namespaceSettings)
        }

        return JSONObject()
            .put("uid", uid)
            .put("settings", allSettings)
            .toString()
    }
}

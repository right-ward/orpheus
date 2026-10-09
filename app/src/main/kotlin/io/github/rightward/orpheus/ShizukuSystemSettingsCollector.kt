package io.github.rightward.orpheus

import androidx.annotation.Keep
import org.json.JSONObject

/**
 * Normalizes the small, explicit settings allow-list returned by the Shizuku UserService.
 *
 * This is an export-only snapshot. It deliberately excludes credentials, account identifiers,
 * accessibility service names, input-method names, network settings, and ringtone URIs.
 */
@Keep
object ShizukuSystemSettingsCollector {
    const val SCHEMA = "orpheus.android.settings.snapshot"
    const val SCHEMA_VERSION = 1

    val ALLOWLIST: Map<String, List<String>> = linkedMapOf(
        "system" to listOf(
            "font_scale",
            "screen_off_timeout",
            "screen_brightness",
            "screen_brightness_mode",
            "accelerometer_rotation",
            "user_rotation",
            "haptic_feedback_enabled",
            "sound_effects_enabled"
        ),
        "global" to listOf(
            "window_animation_scale",
            "transition_animation_scale",
            "animator_duration_scale"
        )
    )

    data class Snapshot(
        val json: String,
        val readableCount: Int,
        val notSetCount: Int,
        val failedCount: Int,
        val totalCount: Int
    )

    fun normalize(
        rawReport: String,
        expectedUid: Int,
        collectedAtUtc: String
    ): Snapshot {
        require(expectedUid == 0 || expectedUid == 2000) {
            "Unexpected Shizuku server UID."
        }

        val report = JSONObject(rawReport)
        require(report.getInt("uid") == expectedUid) {
            "Settings UserService UID did not match the Shizuku server UID."
        }

        val suppliedSettings = report.getJSONObject("settings")
        val normalizedSettings = JSONObject()
        var readableCount = 0
        var notSetCount = 0
        var failedCount = 0
        var totalCount = 0

        ALLOWLIST.forEach { (namespace, keys) ->
            val suppliedNamespace = suppliedSettings.getJSONObject(namespace)
            val normalizedNamespace = JSONObject()

            keys.forEach { key ->
                totalCount++
                val supplied = suppliedNamespace.optJSONObject(key)
                    ?: throw IllegalArgumentException(
                        "Settings response omitted an allow-listed key."
                    )

                when (supplied.optString("status")) {
                    "available" -> {
                        require(supplied.has("value") && !supplied.isNull("value")) {
                            "Settings response contained a value without a value field."
                        }
                        val value = supplied.getString("value")
                        require(value.length <= MAX_SETTING_VALUE_LENGTH) {
                            "Settings value exceeded the allowed size."
                        }
                        require(!value.contains('\u0000')) {
                            "Settings value contained an invalid character."
                        }

                        normalizedNamespace.put(
                            key,
                            JSONObject()
                                .put("status", "available")
                                .put("value", value)
                        )
                        readableCount++
                    }

                    "not_set" -> {
                        normalizedNamespace.put(
                            key,
                            JSONObject().put("status", "not_set")
                        )
                        notSetCount++
                    }

                    "failed" -> {
                        normalizedNamespace.put(
                            key,
                            JSONObject()
                                .put("status", "failed")
                                .put("error_code", "SETTING_READ_FAILED")
                        )
                        failedCount++
                    }

                    else -> throw IllegalArgumentException(
                        "Settings response contained an unknown status."
                    )
                }
            }

            normalizedSettings.put(namespace, normalizedNamespace)
        }

        val normalized = JSONObject()
            .put("schema", SCHEMA)
            .put("schema_version", SCHEMA_VERSION)
            .put("collected_at", collectedAtUtc)
            .put("source", "shizuku")
            .put("coverage", "explicit_allowlist_only")
            .put("restore_supported", false)
            .put("settings", normalizedSettings)
            .toString(2)

        return Snapshot(
            json = normalized,
            readableCount = readableCount,
            notSetCount = notSetCount,
            failedCount = failedCount,
            totalCount = totalCount
        )
    }

    private const val MAX_SETTING_VALUE_LENGTH = 4096
}

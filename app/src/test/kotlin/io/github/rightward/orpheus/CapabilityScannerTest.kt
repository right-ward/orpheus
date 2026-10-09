package io.github.rightward.orpheus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CapabilityScannerTest {
    @Test
    fun missingShizukuIsNotReportedAsAnAndroidLimitation() {
        val snapshot = CapabilityScanner().scan()
        assertEquals(
            CapabilityAvailability.NOT_TESTED,
            snapshot.forId(CapabilityId.SHIZUKU_BACKEND)?.availability
        )
        assertEquals(
            CapabilityAvailability.NOT_TESTED,
            snapshot.forId(CapabilityId.SHIZUKU_READ_ONLY_DIAGNOSTICS)?.availability
        )
        assertEquals(
            CapabilityAvailability.NOT_TESTED,
            snapshot.forId(CapabilityId.SHIZUKU_PACKAGE_APK_FALLBACK)?.availability
        )
        assertEquals(
            CapabilityAvailability.NOT_TESTED,
            snapshot.forId(CapabilityId.SHIZUKU_SYSTEM_SETTINGS_SNAPSHOT)?.availability
        )
    }

    @Test
    fun permissionDoesNotImplyTheReadOnlyProbeHasPassed() {
        val snapshot = CapabilityScanner(
            ShizukuBackendStatus(
                binderConnected = true, serverApiSupported = true,
                permissionGranted = true, serverUid = 2000
            )
        ).scan()
        assertEquals(
            CapabilityAvailability.AVAILABLE,
            snapshot.forId(CapabilityId.SHIZUKU_BACKEND)?.availability
        )
        assertEquals(
            CapabilityAvailability.NOT_TESTED,
            snapshot.forId(CapabilityId.SHIZUKU_READ_ONLY_DIAGNOSTICS)?.availability
        )
        assertEquals(
            CapabilityAvailability.NOT_TESTED,
            snapshot.forId(CapabilityId.SHIZUKU_PACKAGE_APK_FALLBACK)?.availability
        )
    }

    @Test
    fun successfulProbeDoesNotAssumeEveryApkIsReadable() {
        val snapshot = CapabilityScanner(
            ShizukuBackendStatus(
                binderConnected = true, serverApiSupported = true,
                permissionGranted = true, serverUid = 2000,
                probeOutcome = ShizukuProbeOutcome.SUCCEEDED,
                probeSummary = "UserService UID 2000."
            )
        ).scan()
        assertEquals(
            CapabilityAvailability.AVAILABLE,
            snapshot.forId(CapabilityId.SHIZUKU_BACKEND)?.availability
        )
        assertEquals(
            CapabilityAvailability.AVAILABLE,
            snapshot.forId(CapabilityId.SHIZUKU_READ_ONLY_DIAGNOSTICS)?.availability
        )
        assertEquals(
            CapabilityAvailability.NOT_TESTED,
            snapshot.forId(CapabilityId.SHIZUKU_PACKAGE_APK_FALLBACK)?.availability
        )
        assertEquals(
            CapabilityAvailability.NOT_TESTED,
            snapshot.forId(CapabilityId.SHIZUKU_SYSTEM_SETTINGS_SNAPSHOT)?.availability
        )
        assertNotNull(snapshot.forId(CapabilityId.PACKAGE_INVENTORY))

        val afterFallback = CapabilityScanner(
            ShizukuBackendStatus(
                binderConnected = true, serverApiSupported = true,
                permissionGranted = true, serverUid = 2000,
                probeOutcome = ShizukuProbeOutcome.SUCCEEDED,
                apkFallbackOutcome = ShizukuApkFallbackOutcome.SUCCEEDED,
                apkFallbackSummary = "Shizuku read one APK."
            )
        ).scan()
        assertEquals(
            CapabilityAvailability.AVAILABLE,
            afterFallback.forId(CapabilityId.SHIZUKU_PACKAGE_APK_FALLBACK)?.availability
        )
        assertEquals(
            CapabilityAvailability.NOT_TESTED,
            afterFallback.forId(CapabilityId.SHIZUKU_SYSTEM_SETTINGS_SNAPSHOT)?.availability
        )
    }
}

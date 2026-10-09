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
    }

    @Test
    fun permissionDoesNotImplyTheReadOnlyProbeHasPassed() {
        val snapshot = CapabilityScanner(
            ShizukuBackendStatus(
                binderConnected = true,
                serverApiSupported = true,
                permissionGranted = true,
                serverUid = 2000
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
    }

    @Test
    fun successfulProbeEnablesOnlyTheDiagnosticsCapability() {
        val snapshot = CapabilityScanner(
            ShizukuBackendStatus(
                binderConnected = true,
                serverApiSupported = true,
                permissionGranted = true,
                serverUid = 2000,
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
        assertNotNull(snapshot.forId(CapabilityId.PACKAGE_INVENTORY))
    }
}

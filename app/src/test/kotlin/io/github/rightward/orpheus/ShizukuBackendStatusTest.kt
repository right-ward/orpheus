package io.github.rightward.orpheus

import org.junit.Assert.assertEquals
import org.junit.Test

class ShizukuBackendStatusTest {
    @Test
    fun classifiesKnownServerUidsWithoutGuessingUnknownValues() {
        assertEquals(ShizukuPrivilege.ROOT, ShizukuPrivilegeClassifier.classify(0))
        assertEquals(ShizukuPrivilege.SHELL, ShizukuPrivilegeClassifier.classify(2000))
        assertEquals(ShizukuPrivilege.UNKNOWN, ShizukuPrivilegeClassifier.classify(10000))
        assertEquals(ShizukuPrivilege.UNKNOWN, ShizukuPrivilegeClassifier.classify(null))
    }

    @Test
    fun disconnectedBackendDoesNotClaimItIsAvailableOrUnavailable() {
        val status = ShizukuBackendStatus()

        assertEquals(CapabilityAvailability.NOT_TESTED, status.backendAvailability())
        assertEquals(CapabilityAvailability.NOT_TESTED, status.diagnosticsAvailability())
    }

    @Test
    fun authorizationIsRequiredBeforeTheBackendIsAvailable() {
        val status = ShizukuBackendStatus(
            binderConnected = true,
            serverApiSupported = true,
            permissionGranted = false,
            serverUid = 2000
        )

        assertEquals(CapabilityAvailability.LIMITED, status.backendAvailability())
        assertEquals(CapabilityAvailability.LIMITED, status.diagnosticsAvailability())
    }

    @Test
    fun diagnosticCapabilityRequiresASuccessfulProbe() {
        val authorized = ShizukuBackendStatus(
            binderConnected = true,
            serverApiSupported = true,
            permissionGranted = true,
            serverUid = 2000
        )
        assertEquals(CapabilityAvailability.AVAILABLE, authorized.backendAvailability())
        assertEquals(CapabilityAvailability.NOT_TESTED, authorized.diagnosticsAvailability())

        assertEquals(
            CapabilityAvailability.AVAILABLE,
            authorized.copy(
                probeOutcome = ShizukuProbeOutcome.SUCCEEDED,
                probeSummary = "Probe passed."
            ).diagnosticsAvailability()
        )
        assertEquals(
            CapabilityAvailability.LIMITED,
            authorized.copy(
                probeOutcome = ShizukuProbeOutcome.FAILED,
                probeFailure = "Permission denied."
            ).diagnosticsAvailability()
        )
    }

    @Test
    fun oldServerApiIsReportedAsUnsupported() {
        val status = ShizukuBackendStatus(
            binderConnected = true,
            serverApiSupported = false,
            permissionGranted = false
        )

        assertEquals(CapabilityAvailability.UNAVAILABLE, status.backendAvailability())
        assertEquals(CapabilityAvailability.UNAVAILABLE, status.diagnosticsAvailability())
    }
}

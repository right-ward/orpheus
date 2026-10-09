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
        assertEquals(CapabilityAvailability.NOT_TESTED, status.apkFallbackAvailability())
    }

    @Test
    fun unknownPrivilegeDoesNotClaimTheBackendIsFullyAvailable() {
        val status = ShizukuBackendStatus(
            binderConnected = true, serverApiSupported = true,
            permissionGranted = true, serverUid = 10000
        )
        assertEquals(CapabilityAvailability.LIMITED, status.backendAvailability())
        assertEquals(CapabilityAvailability.NOT_TESTED, status.diagnosticsAvailability())
        assertEquals(CapabilityAvailability.LIMITED, status.apkFallbackAvailability())
    }

    @Test
    fun authorizationIsRequiredBeforeTheBackendIsAvailable() {
        val status = ShizukuBackendStatus(
            binderConnected = true, serverApiSupported = true,
            permissionGranted = false, serverUid = 2000
        )
        assertEquals(CapabilityAvailability.LIMITED, status.backendAvailability())
        assertEquals(CapabilityAvailability.LIMITED, status.diagnosticsAvailability())
        assertEquals(CapabilityAvailability.LIMITED, status.apkFallbackAvailability())
    }

    @Test
    fun diagnosticCapabilityRequiresASuccessfulProbe() {
        val authorized = ShizukuBackendStatus(
            binderConnected = true, serverApiSupported = true,
            permissionGranted = true, serverUid = 2000
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
    fun apkFallbackRequiresSuccessfulProbeAndAnObservedFallbackRead() {
        val authorized = ShizukuBackendStatus(
            binderConnected = true, serverApiSupported = true,
            permissionGranted = true, serverUid = 2000,
            probeOutcome = ShizukuProbeOutcome.SUCCEEDED,
            probeSummary = "Probe passed."
        )
        assertEquals(CapabilityAvailability.NOT_TESTED, authorized.apkFallbackAvailability())
        assertEquals(
            CapabilityAvailability.AVAILABLE,
            authorized.copy(
                apkFallbackOutcome = ShizukuApkFallbackOutcome.SUCCEEDED,
                apkFallbackSummary = "Read 1 APK."
            ).apkFallbackAvailability()
        )
        assertEquals(
            CapabilityAvailability.LIMITED,
            authorized.copy(
                apkFallbackOutcome = ShizukuApkFallbackOutcome.FAILED,
                apkFallbackFailure = "Permission denied."
            ).apkFallbackAvailability()
        )
        assertEquals(
            CapabilityAvailability.NOT_TESTED,
            authorized.copy(probeOutcome = ShizukuProbeOutcome.NOT_RUN)
                .apkFallbackAvailability()
        )
    }

    @Test
    fun settingsSnapshotCapabilityRequiresProbeAndReportsPartialCollection() {
        val authorized = ShizukuBackendStatus(
            binderConnected = true,
            serverApiSupported = true,
            permissionGranted = true,
            serverUid = 2000
        )
        assertEquals(
            CapabilityAvailability.NOT_TESTED,
            authorized.systemSettingsSnapshotAvailability()
        )

        val probed = authorized.copy(
            probeOutcome = ShizukuProbeOutcome.SUCCEEDED,
            probeSummary = "Probe passed."
        )
        assertEquals(
            CapabilityAvailability.NOT_TESTED,
            probed.systemSettingsSnapshotAvailability()
        )
        assertEquals(
            CapabilityAvailability.AVAILABLE,
            probed.copy(
                settingsSnapshotOutcome = ShizukuSettingsSnapshotOutcome.SUCCEEDED,
                settingsSnapshotSummary = "Snapshot collected."
            ).systemSettingsSnapshotAvailability()
        )
        assertEquals(
            CapabilityAvailability.LIMITED,
            probed.copy(
                settingsSnapshotOutcome = ShizukuSettingsSnapshotOutcome.PARTIAL,
                settingsSnapshotFailure = "Some values failed."
            ).systemSettingsSnapshotAvailability()
        )
        assertEquals(
            CapabilityAvailability.LIMITED,
            probed.copy(
                settingsSnapshotOutcome = ShizukuSettingsSnapshotOutcome.FAILED,
                settingsSnapshotFailure = "Read failed."
            ).systemSettingsSnapshotAvailability()
        )
    }

    @Test
    fun oldServerApiIsReportedAsUnsupported() {
        val status = ShizukuBackendStatus(
            binderConnected = true, serverApiSupported = false,
            permissionGranted = false
        )
        assertEquals(CapabilityAvailability.UNAVAILABLE, status.backendAvailability())
        assertEquals(CapabilityAvailability.UNAVAILABLE, status.diagnosticsAvailability())
        assertEquals(CapabilityAvailability.UNAVAILABLE, status.apkFallbackAvailability())
    }
}

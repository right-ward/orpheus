package io.github.rightward.orpheus

enum class ShizukuPrivilege(val displayName: String) {
    ROOT("root"),
    SHELL("shell / ADB"),
    UNKNOWN("unknown")
}

object ShizukuPrivilegeClassifier {
    fun classify(uid: Int?): ShizukuPrivilege = when (uid) {
        0 -> ShizukuPrivilege.ROOT
        2000 -> ShizukuPrivilege.SHELL
        else -> ShizukuPrivilege.UNKNOWN
    }
}

enum class ShizukuProbeOutcome {
    NOT_RUN,
    SUCCEEDED,
    FAILED
}

data class ShizukuBackendStatus(
    val binderConnected: Boolean = false,
    val serverApiSupported: Boolean = false,
    val permissionGranted: Boolean = false,
    val serverUid: Int? = null,
    val probeOutcome: ShizukuProbeOutcome = ShizukuProbeOutcome.NOT_RUN,
    val probeSummary: String? = null,
    val probeFailure: String? = null
) {
    val privilege: ShizukuPrivilege
        get() = ShizukuPrivilegeClassifier.classify(serverUid)

    fun backendAvailability(): CapabilityAvailability = when {
        !binderConnected -> CapabilityAvailability.NOT_TESTED
        !serverApiSupported -> CapabilityAvailability.UNAVAILABLE
        !permissionGranted -> CapabilityAvailability.LIMITED
        else -> CapabilityAvailability.AVAILABLE
    }

    fun diagnosticsAvailability(): CapabilityAvailability = when {
        !binderConnected -> CapabilityAvailability.NOT_TESTED
        !serverApiSupported -> CapabilityAvailability.UNAVAILABLE
        !permissionGranted -> CapabilityAvailability.LIMITED
        probeOutcome == ShizukuProbeOutcome.SUCCEEDED ->
            CapabilityAvailability.AVAILABLE
        probeOutcome == ShizukuProbeOutcome.FAILED ->
            CapabilityAvailability.LIMITED
        else -> CapabilityAvailability.NOT_TESTED
    }

    fun backendDetail(): String = when {
        !binderConnected ->
            "Shizuku/Sui is not connected. Start the service before requesting privileged operations."
        !serverApiSupported ->
            "A Shizuku service is connected, but its API is older than v11; UserService operations are unsupported."
        !permissionGranted ->
            "Shizuku is connected, but Orpheus has not been granted Shizuku permission."
        serverUid == null ->
            "Shizuku is connected and authorized, but its server UID could not be determined."
        else ->
            "Shizuku is connected and authorized. Reported server privilege: " +
                privilege.displayName + " (UID " + serverUid + ")."
    }

    fun diagnosticsDetail(): String = when {
        !binderConnected ->
            "The read-only shell probe has not run because Shizuku/Sui is not connected."
        !serverApiSupported ->
            "The read-only shell probe requires Shizuku API v11 or later."
        !permissionGranted ->
            "Grant Orpheus permission in Shizuku before running the read-only probe."
        probeOutcome == ShizukuProbeOutcome.SUCCEEDED ->
            probeSummary ?: "The read-only probe completed successfully."
        probeOutcome == ShizukuProbeOutcome.FAILED ->
            "The read-only probe failed: " + (probeFailure ?: "unknown error")
        else ->
            "Authorization is ready. Run the read-only probe to verify the isolated UserService path."
    }

    fun summary(
        permissionRequestPending: Boolean = false,
        probeInProgress: Boolean = false
    ): String = when {
        permissionRequestPending ->
            "Waiting for the Shizuku permission result…"
        probeInProgress ->
            "Running the read-only probe through an isolated Shizuku UserService…"
        !binderConnected ->
            "Shizuku/Sui is not connected."
        !serverApiSupported ->
            "Connected, but the Shizuku server API is too old."
        !permissionGranted ->
            "Connected; permission is required."
        probeOutcome == ShizukuProbeOutcome.SUCCEEDED ->
            "Connected · " + privilege.displayName + " · read-only probe passed."
        probeOutcome == ShizukuProbeOutcome.FAILED ->
            "Connected · " + privilege.displayName + " · read-only probe failed."
        else ->
            "Connected · " + privilege.displayName + " · ready to probe."
    }
}

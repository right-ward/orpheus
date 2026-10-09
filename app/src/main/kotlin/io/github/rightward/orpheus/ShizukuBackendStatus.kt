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

enum class ShizukuApkFallbackOutcome {
    NOT_RUN,
    SUCCEEDED,
    FAILED
}

enum class ShizukuSettingsSnapshotOutcome {
    NOT_RUN,
    SUCCEEDED,
    PARTIAL,
    FAILED
}

data class ShizukuBackendStatus(
    val binderConnected: Boolean = false,
    val serverApiSupported: Boolean = false,
    val permissionGranted: Boolean = false,
    val serverUid: Int? = null,
    val probeOutcome: ShizukuProbeOutcome = ShizukuProbeOutcome.NOT_RUN,
    val probeSummary: String? = null,
    val probeFailure: String? = null,
    val apkFallbackOutcome: ShizukuApkFallbackOutcome = ShizukuApkFallbackOutcome.NOT_RUN,
    val apkFallbackSummary: String? = null,
    val apkFallbackFailure: String? = null,
    val settingsSnapshotOutcome: ShizukuSettingsSnapshotOutcome =
        ShizukuSettingsSnapshotOutcome.NOT_RUN,
    val settingsSnapshotSummary: String? = null,
    val settingsSnapshotFailure: String? = null
) {
    val privilege: ShizukuPrivilege
        get() = ShizukuPrivilegeClassifier.classify(serverUid)

    fun backendAvailability(): CapabilityAvailability = when {
        !binderConnected -> CapabilityAvailability.NOT_TESTED
        !serverApiSupported -> CapabilityAvailability.UNAVAILABLE
        !permissionGranted -> CapabilityAvailability.LIMITED
        privilege == ShizukuPrivilege.UNKNOWN -> CapabilityAvailability.LIMITED
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

    fun apkFallbackAvailability(): CapabilityAvailability = when {
        !binderConnected -> CapabilityAvailability.NOT_TESTED
        !serverApiSupported -> CapabilityAvailability.UNAVAILABLE
        !permissionGranted -> CapabilityAvailability.LIMITED
        privilege == ShizukuPrivilege.UNKNOWN -> CapabilityAvailability.LIMITED
        probeOutcome == ShizukuProbeOutcome.FAILED -> CapabilityAvailability.LIMITED
        probeOutcome != ShizukuProbeOutcome.SUCCEEDED -> CapabilityAvailability.NOT_TESTED
        apkFallbackOutcome == ShizukuApkFallbackOutcome.SUCCEEDED ->
            CapabilityAvailability.AVAILABLE
        apkFallbackOutcome == ShizukuApkFallbackOutcome.FAILED ->
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

    fun apkFallbackDetail(): String = when {
        !binderConnected ->
            "Shizuku APK fallback has not been tested because the service is not connected."
        !serverApiSupported ->
            "Shizuku APK fallback requires UserService support (server API v11 or later)."
        !permissionGranted ->
            "Grant Orpheus Shizuku permission before APK fallback can be attempted."
        privilege == ShizukuPrivilege.UNKNOWN ->
            "APK fallback is blocked because the Shizuku server UID is unknown."
        probeOutcome == ShizukuProbeOutcome.FAILED ->
            "APK fallback is blocked because the read-only UserService probe failed."
        probeOutcome != ShizukuProbeOutcome.SUCCEEDED ->
            "APK fallback has not been tested. Run the read-only probe first."
        apkFallbackOutcome == ShizukuApkFallbackOutcome.SUCCEEDED ->
            apkFallbackSummary
                ?: "Shizuku successfully read at least one APK that the normal app process could not open."
        apkFallbackOutcome == ShizukuApkFallbackOutcome.FAILED ->
            "Shizuku APK fallback was attempted but could not read an APK: " +
                (apkFallbackFailure ?: "unknown error")
        else ->
            "After the probe succeeds, inaccessible PackageManager-reported APK paths under /data/app may be tried through a read-only Shizuku file descriptor."
    }

    fun systemSettingsSnapshotAvailability(): CapabilityAvailability = when {
        !binderConnected -> CapabilityAvailability.NOT_TESTED
        !serverApiSupported -> CapabilityAvailability.UNAVAILABLE
        !permissionGranted -> CapabilityAvailability.LIMITED
        privilege == ShizukuPrivilege.UNKNOWN -> CapabilityAvailability.LIMITED
        probeOutcome == ShizukuProbeOutcome.FAILED -> CapabilityAvailability.LIMITED
        probeOutcome != ShizukuProbeOutcome.SUCCEEDED ->
            CapabilityAvailability.NOT_TESTED
        settingsSnapshotOutcome == ShizukuSettingsSnapshotOutcome.SUCCEEDED ->
            CapabilityAvailability.AVAILABLE
        settingsSnapshotOutcome == ShizukuSettingsSnapshotOutcome.PARTIAL ||
            settingsSnapshotOutcome == ShizukuSettingsSnapshotOutcome.FAILED ->
            CapabilityAvailability.LIMITED
        else -> CapabilityAvailability.NOT_TESTED
    }

    fun systemSettingsSnapshotDetail(): String = when {
        !binderConnected ->
            "The allow-listed settings snapshot has not been tested because Shizuku/Sui is not connected."
        !serverApiSupported ->
            "The settings snapshot requires Shizuku UserService support (server API v11 or later)."
        !permissionGranted ->
            "Grant Orpheus Shizuku permission before collecting the allow-listed settings snapshot."
        privilege == ShizukuPrivilege.UNKNOWN ->
            "The settings snapshot is blocked because the Shizuku server UID is unknown."
        probeOutcome == ShizukuProbeOutcome.FAILED ->
            "The settings snapshot is blocked because the read-only UserService probe failed."
        probeOutcome != ShizukuProbeOutcome.SUCCEEDED ->
            "Run the read-only Shizuku probe before collecting allow-listed system settings."
        settingsSnapshotOutcome == ShizukuSettingsSnapshotOutcome.SUCCEEDED ->
            settingsSnapshotSummary
                ?: "The allow-listed settings snapshot was collected."
        settingsSnapshotOutcome == ShizukuSettingsSnapshotOutcome.PARTIAL ->
            settingsSnapshotSummary
                ?: "The settings snapshot was partial; one or more settings could not be read."
        settingsSnapshotOutcome == ShizukuSettingsSnapshotOutcome.FAILED ->
            "The settings snapshot failed: " +
                (settingsSnapshotFailure ?: "unknown error")
        else ->
            "Not collected. The optional snapshot reads only a small allow-list of display and interaction preferences; it does not change or restore settings."
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

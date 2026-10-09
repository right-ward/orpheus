package io.github.rightward.orpheus

class CapabilityScanner(
    private val shizukuStatus: ShizukuBackendStatus = ShizukuBackendStatus()
) {
    fun scan(): CapabilitySnapshot {
        val normal = CapabilityProvider.NORMAL_ANDROID
        val shizuku = CapabilityProvider.SHIZUKU

        return CapabilitySnapshot(
            capabilities = listOf(
                Capability(
                    id = CapabilityId.DEVICE_METADATA,
                    provider = normal,
                    availability = CapabilityAvailability.AVAILABLE,
                    detail = "Public Android build/device metadata is readable."
                ),
                Capability(
                    id = CapabilityId.STORAGE_INSPECTION,
                    provider = normal,
                    availability = CapabilityAvailability.AVAILABLE,
                    detail = "Filesystem capacity statistics are readable."
                ),
                Capability(
                    id = CapabilityId.USER_SELECTED_FILE_ACCESS,
                    provider = normal,
                    availability = CapabilityAvailability.AVAILABLE,
                    detail = "Android user-mediated document selection APIs are available."
                ),
                Capability(
                    id = CapabilityId.SHARED_STORAGE_ENUMERATION,
                    provider = normal,
                    availability = CapabilityAvailability.NOT_IMPLEMENTED,
                    detail = "Full user-storage enumeration beyond explicitly selected document trees is not implemented."
                ),
                Capability(
                    id = CapabilityId.PACKAGE_INVENTORY,
                    provider = normal,
                    availability = CapabilityAvailability.AVAILABLE,
                    detail = "Installed packages and exposed package metadata are inventoried through PackageManager."
                ),
                Capability(
                    id = CapabilityId.PACKAGE_APK_PRESERVATION,
                    provider = normal,
                    availability = CapabilityAvailability.LIMITED,
                    detail = "APK preservation targets base and split APKs; inaccessible paths may use the separately capability-gated Shizuku fallback under /data/app/."
                ),
                Capability(
                    id = CapabilityId.SHIZUKU_BACKEND,
                    provider = shizuku,
                    availability = shizukuStatus.backendAvailability(),
                    detail = shizukuStatus.backendDetail()
                ),
                Capability(
                    id = CapabilityId.SHIZUKU_READ_ONLY_DIAGNOSTICS,
                    provider = shizuku,
                    availability = shizukuStatus.diagnosticsAvailability(),
                    detail = shizukuStatus.diagnosticsDetail()
                ),
                Capability(
                    id = CapabilityId.SHIZUKU_PACKAGE_APK_FALLBACK,
                    provider = shizuku,
                    availability = shizukuStatus.apkFallbackAvailability(),
                    detail = shizukuStatus.apkFallbackDetail()
                ),
                Capability(
                    id = CapabilityId.SHIZUKU_SYSTEM_SETTINGS_SNAPSHOT,
                    provider = shizuku,
                    availability = shizukuStatus.systemSettingsSnapshotAvailability(),
                    detail = shizukuStatus.systemSettingsSnapshotDetail()
                ),
                Capability(
                    id = CapabilityId.ROOT_BACKEND,
                    provider = CapabilityProvider.ROOT,
                    availability = CapabilityAvailability.NOT_IMPLEMENTED,
                    detail = "A separate root capability backend is not implemented. A Shizuku service may itself run as root, but this is recorded under the Shizuku provider."
                )
            )
        )
    }
}

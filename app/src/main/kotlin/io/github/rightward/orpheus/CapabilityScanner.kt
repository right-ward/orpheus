package io.github.rightward.orpheus

class CapabilityScanner {
    fun scan(): CapabilitySnapshot {
        val normal = CapabilityProvider.NORMAL_ANDROID

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
                    detail = "Full user-storage enumeration is deferred to file-preservation work."
                ),
                Capability(
                    id = CapabilityId.PACKAGE_INVENTORY,
                    provider = normal,
                    availability = CapabilityAvailability.NOT_IMPLEMENTED,
                    detail = "Package inventory is deferred to the package-preservation phase."
                ),
                Capability(
                    id = CapabilityId.SHIZUKU_BACKEND,
                    provider = CapabilityProvider.SHIZUKU,
                    availability = CapabilityAvailability.NOT_IMPLEMENTED,
                    detail = "Shizuku detection and integration are planned for a later phase."
                ),
                Capability(
                    id = CapabilityId.ROOT_BACKEND,
                    provider = CapabilityProvider.ROOT,
                    availability = CapabilityAvailability.NOT_IMPLEMENTED,
                    detail = "Root is a future capability backend."
                )
            )
        )
    }
}

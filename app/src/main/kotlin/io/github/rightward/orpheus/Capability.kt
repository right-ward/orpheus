package io.github.rightward.orpheus

enum class CapabilityProvider {
    NORMAL_ANDROID,
    SHIZUKU,
    ROOT
}

enum class CapabilityAvailability {
    AVAILABLE,
    LIMITED,
    UNAVAILABLE,
    NOT_IMPLEMENTED,
    NOT_TESTED
}

enum class CapabilityId(
    val displayName: String
) {
    DEVICE_METADATA("Device metadata"),
    STORAGE_INSPECTION("Storage inspection"),
    USER_SELECTED_FILE_ACCESS("User-selected file access"),
    SHARED_STORAGE_ENUMERATION("Shared-storage enumeration"),
    PACKAGE_INVENTORY("Package inventory"),
    PACKAGE_APK_PRESERVATION("Package APK preservation"),
    SHIZUKU_BACKEND("Shizuku backend"),
    SHIZUKU_READ_ONLY_DIAGNOSTICS("Shizuku read-only diagnostics"),
    SHIZUKU_PACKAGE_APK_FALLBACK("Shizuku APK read fallback"),
    ROOT_BACKEND("Root backend")
}

data class Capability(
    val id: CapabilityId,
    val provider: CapabilityProvider,
    val availability: CapabilityAvailability,
    val detail: String
)

data class CapabilitySnapshot(
    val capabilities: List<Capability>
) {
    fun forId(id: CapabilityId): Capability? =
        capabilities.firstOrNull { it.id == id }
}

package io.github.rightward.orpheus

enum class ArtifactStatus {
    RESTORABLE,
    PARTIAL,
    REQUIRES_PRIVILEGE,
    UNAVAILABLE,
    FAILED
}

data class ArchiveArtifact(
    val id: String,
    val kind: String,
    val archivePath: String,
    val source: String,
    val status: ArtifactStatus,
    val sizeBytes: Long,
    val sha256: String?,
    val modifiedAtEpochMs: Long?,
    val errorCode: String? = null
)

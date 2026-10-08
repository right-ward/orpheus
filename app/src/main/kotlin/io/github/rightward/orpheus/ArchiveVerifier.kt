package io.github.rightward.orpheus

import android.content.ContentResolver
import android.net.Uri
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipInputStream

data class ArchiveVerificationResult(
    val valid: Boolean,
    val artifactCount: Int,
    val verifiedBytes: Long,
    val failedArtifacts: Int,
    val error: String? = null
)

private data class ActualFile(
    val sizeBytes: Long,
    val sha256: String
)

class ArchiveVerifier(
    private val resolver: ContentResolver
) {
    fun verify(uri: Uri): ArchiveVerificationResult {
        return try {
            val input = resolver.openInputStream(uri)
                ?: throw IOException("Unable to open archive")

            var manifestJson: String? = null
            var checksumsText: String? = null
            var completeMarkerFound = false
            var verifiedBytes = 0L
            val actualFiles = LinkedHashMap<String, ActualFile>()
            val seenEntries = HashSet<String>()

            ZipInputStream(BufferedInputStream(input, 64 * 1024)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break

                    if (!seenEntries.add(entry.name)) {
                        throw IOException("Duplicate archive entry: " + entry.name)
                    }

                    if (entry.isDirectory) {
                        zip.closeEntry()
                        continue
                    }

                    when (entry.name) {
                        ArchivePaths.MANIFEST -> {
                            manifestJson = zip.readEntryText()
                        }

                        ArchivePaths.CHECKSUMS -> {
                            checksumsText = zip.readEntryText()
                        }

                        ArchivePaths.COMPLETE_MARKER -> {
                            zip.readEntryText()
                            completeMarkerFound = true
                        }

                        ArchivePaths.DEVICE,
                        ArchivePaths.CAPABILITIES -> {
                            zip.readEntryText()
                        }

                        else -> {
                            if (isArtifactPath(entry.name)) {
                                val digest = Sha256.newDigest()
                                val buffer = ByteArray(64 * 1024)
                                var size = 0L

                                while (true) {
                                    val read = zip.read(buffer)
                                    if (read < 0) break
                                    if (read == 0) continue

                                    digest.update(buffer, 0, read)
                                    size += read
                                    verifiedBytes += read
                                }

                                actualFiles[entry.name] = ActualFile(
                                    sizeBytes = size,
                                    sha256 = Sha256.finish(digest)
                                )
                            } else {
                                zip.readEntryText(256 * 1024)
                            }
                        }
                    }

                    zip.closeEntry()
                }
            }

            if (!completeMarkerFound) {
                return ArchiveVerificationResult(
                    valid = false,
                    artifactCount = 0,
                    verifiedBytes = verifiedBytes,
                    failedArtifacts = 0,
                    error = "Archive has no completion marker."
                )
            }

            val manifest = manifestJson?.let(ArchiveManifestCodec::decode)
                ?: return ArchiveVerificationResult(
                    valid = false,
                    artifactCount = 0,
                    verifiedBytes = verifiedBytes,
                    failedArtifacts = 0,
                    error = "Archive manifest is missing."
                )

            val expectedFiles = manifest.artifacts
                .filter { it.status.expectsPayload() }
                .associateBy { it.archivePath }

            val failedPaths = manifest.artifacts
                .filter { it.status == ArtifactStatus.FAILED }
                .map { it.archivePath }
                .toSet()

            val failedArtifacts = manifest.artifacts.count {
                it.status == ArtifactStatus.FAILED
            }

            val missingPaths = expectedFiles.keys - actualFiles.keys
            if (missingPaths.isNotEmpty()) {
                return ArchiveVerificationResult(
                    valid = false,
                    artifactCount = manifest.artifacts.size,
                    verifiedBytes = verifiedBytes,
                    failedArtifacts = failedArtifacts,
                    error = "Missing artifact: " + missingPaths.first()
                )
            }

            val unexpectedPaths =
                actualFiles.keys - expectedFiles.keys - failedPaths
            if (unexpectedPaths.isNotEmpty()) {
                return ArchiveVerificationResult(
                    valid = false,
                    artifactCount = manifest.artifacts.size,
                    verifiedBytes = verifiedBytes,
                    failedArtifacts = failedArtifacts,
                    error = "Unexpected artifact entry: " +
                        unexpectedPaths.first()
                )
            }

            expectedFiles.forEach { (path, artifact) ->
                val actual = actualFiles[path]
                    ?: throw IOException("Missing artifact: " + path)

                if (actual.sizeBytes != artifact.sizeBytes) {
                    throw IOException("Size mismatch: " + path)
                }

                if (!artifact.sha256.equals(actual.sha256, ignoreCase = true)) {
                    throw IOException("Checksum mismatch: " + path)
                }
            }

            val checksumLines = checksumsText
                ?.lineSequence()
                ?.filter { it.isNotBlank() }
                ?.map { it.trim() }
                ?.toSet()
                .orEmpty()

            manifest.artifacts
                .filter { it.sha256 != null }
                .forEach { artifact ->
                    val expectedLine =
                        artifact.sha256 + "  " + artifact.archivePath
                    if (expectedLine !in checksumLines) {
                        throw IOException(
                            "Checksum record missing: " + artifact.archivePath
                        )
                    }
                }

            ArchiveVerificationResult(
                valid = true,
                artifactCount = manifest.artifacts.size,
                verifiedBytes = verifiedBytes,
                failedArtifacts = failedArtifacts,
                error = null
            )
        } catch (error: Exception) {
            ArchiveVerificationResult(
                valid = false,
                artifactCount = 0,
                verifiedBytes = 0L,
                failedArtifacts = 0,
                error = error.message ?: error.javaClass.simpleName
            )
        }
    }

    private fun ArtifactStatus.expectsPayload(): Boolean =
        when (this) {
            ArtifactStatus.RESTORABLE,
            ArtifactStatus.PARTIAL -> true
            ArtifactStatus.REQUIRES_PRIVILEGE,
            ArtifactStatus.UNAVAILABLE,
            ArtifactStatus.FAILED -> false
        }

    private fun isArtifactPath(path: String): Boolean =
        path.startsWith(ArchivePaths.FILES_PREFIX) ||
            path.startsWith(ArchivePaths.PACKAGES_PREFIX) ||
            path.startsWith(ArchivePaths.DATA_PREFIX) ||
            path.startsWith(ArchivePaths.EXPORTS_PREFIX)

    private fun ZipInputStream.readEntryText(
        maxBytes: Int = 32 * 1024 * 1024
    ): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(32 * 1024)
        var total = 0

        while (true) {
            val read = read(buffer)
            if (read < 0) break
            if (read == 0) continue

            total += read
            if (total > maxBytes) {
                throw IOException("Archive metadata entry is too large")
            }

            output.write(buffer, 0, read)
        }

        return output.toString(Charsets.UTF_8.name())
    }
}

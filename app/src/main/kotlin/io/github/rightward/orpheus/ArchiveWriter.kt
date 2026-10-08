package io.github.rightward.orpheus

import android.content.ContentResolver
import android.net.Uri
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

interface BackupProgressListener {
    fun onFileStarted(path: String)
    fun onBytesProcessed(delta: Long, totalBytes: Long)
}

data class BackupWriteResult(
    val archiveId: String,
    val artifacts: List<ArchiveArtifact>,
    val selectionErrors: List<String>
)

class ArchiveWriter(
    private val resolver: ContentResolver,
    private val deviceInfo: DeviceInfo,
    private val capabilities: CapabilitySnapshot
) {
    fun write(
        outputUri: Uri,
        selectedTrees: List<SelectedTree>,
        listener: BackupProgressListener
    ): BackupWriteResult {
        require(selectedTrees.isNotEmpty()) {
            "At least one folder must be selected"
        }

        val archiveId = UUID.randomUUID().toString()
        val artifacts = ArrayList<ArchiveArtifact>()
        val selectionErrors = ArrayList<String>()
        var artifactIndex = 0
        var processedBytes = 0L

        val rawOutput = resolver.openOutputStream(outputUri)
            ?: throw IOException("Unable to open archive output")

        BufferedOutputStream(rawOutput, 64 * 1024).use { buffered ->
            ZipOutputStream(buffered).use { zip ->
                zip.setLevel(6)

                writeEntry(zip, ArchivePaths.DEVICE, ArchiveJson.device(deviceInfo))
                writeEntry(
                    zip,
                    ArchivePaths.CAPABILITIES,
                    ArchiveJson.capabilities(capabilities)
                )

                selectedTrees.forEachIndexed { rootIndex, selected ->
                    val rootName = "root-%03d-%s".format(
                        Locale.US,
                        rootIndex + 1,
                        ArchivePath.sanitizeComponent(selected.label)
                    )

                    try {
                        DocumentTreeWalker(resolver).walk(selected.uri) { file ->
                            val source = rootName + "/" + file.relativePath
                            val archivePath = ArchivePaths.FILES_PREFIX + source
                            val artifactId = "file-%06d".format(
                                Locale.US,
                                artifactIndex + 1
                            )
                            artifactIndex++

                            listener.onFileStarted(source)

                            val input = try {
                                resolver.openInputStream(file.documentUri)?.let {
                                    BufferedInputStream(it, 64 * 1024)
                                }
                            } catch (_: Exception) {
                                null
                            }

                            if (input == null) {
                                artifacts += ArchiveArtifact(
                                    id = artifactId,
                                    kind = "file",
                                    archivePath = archivePath,
                                    source = source,
                                    status = ArtifactStatus.FAILED,
                                    sizeBytes = file.sizeBytes ?: 0L,
                                    sha256 = null,
                                    modifiedAtEpochMs = file.modifiedAtEpochMs,
                                    errorCode = "SOURCE_OPEN_FAILED"
                                )
                                return@walk
                            }

                            zip.putNextEntry(ZipEntry(archivePath))

                            try {
                                val digest = Sha256.newDigest()
                                val bytes = input.use { stream ->
                                    stream.copyToWithDigest(
                                        output = zip,
                                        digest = digest,
                                        onBytes = { delta ->
                                            processedBytes += delta
                                            listener.onBytesProcessed(
                                                delta,
                                                processedBytes
                                            )
                                        }
                                    )
                                }

                                zip.closeEntry()

                                artifacts += ArchiveArtifact(
                                    id = artifactId,
                                    kind = "file",
                                    archivePath = archivePath,
                                    source = source,
                                    status = ArtifactStatus.RESTORABLE,
                                    sizeBytes = bytes,
                                    sha256 = Sha256.finish(digest),
                                    modifiedAtEpochMs = file.modifiedAtEpochMs
                                )
                            } catch (error: Exception) {
                                runCatching { zip.closeEntry() }
                                throw IOException(
                                    "Failed while reading " + source,
                                    error
                                )
                            }
                        }
                    } catch (_: Exception) {
                        selectionErrors += rootName + ": ENUMERATION_OR_READ_FAILED"
                    }
                }

                val manifest = ArchiveManifest(
                    archiveId = archiveId,
                    createdAt = nowUtc(),
                    source = ArchiveSource(
                        androidApi = deviceInfo.apiLevel,
                        androidRelease = deviceInfo.androidRelease,
                        manufacturer = deviceInfo.manufacturer,
                        model = deviceInfo.model
                    ),
                    selectedRoots = selectedTrees.mapIndexed { index, selected ->
                        "root-%03d-%s".format(
                            Locale.US,
                            index + 1,
                            ArchivePath.sanitizeComponent(selected.label)
                        )
                    },
                    artifacts = artifacts,
                    selectionErrors = selectionErrors
                )

                val checksums = artifacts
                    .filter { it.sha256 != null }
                    .joinToString("
") { it.sha256 + "  " + it.archivePath } +
                    "
"

                writeEntry(zip, ArchivePaths.CHECKSUMS, checksums)
                writeEntry(
                    zip,
                    ArchivePaths.MANIFEST,
                    ArchiveManifestCodec.encode(manifest)
                )
                writeEntry(
                    zip,
                    ArchivePaths.COMPLETE_MARKER,
                    "writer_complete=true
"
                )
            }
        }

        return BackupWriteResult(
            archiveId = archiveId,
            artifacts = artifacts,
            selectionErrors = selectionErrors
        )
    }

    private fun writeEntry(
        zip: ZipOutputStream,
        name: String,
        content: String
    ) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun nowUtc(): String =
        SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            Locale.US
        ).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
}

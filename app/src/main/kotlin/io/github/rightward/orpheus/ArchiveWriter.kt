package io.github.rightward.orpheus

import android.content.ContentResolver
import android.content.pm.PackageManager
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
    fun onPackageStarted(packageName: String) {}
    fun onFileStarted(path: String)
    fun onBytesProcessed(delta: Long, totalBytes: Long)
}

data class BackupWriteResult(
    val archiveId: String,
    val artifacts: List<ArchiveArtifact>,
    val selectionErrors: List<String>,
    val packageCount: Int,
    val shizukuApkFallbackAttempts: Int = 0,
    val shizukuApkFallbackSuccesses: Int = 0,
    val shizukuSettingsSnapshotOutcome: ShizukuSettingsSnapshotOutcome =
        ShizukuSettingsSnapshotOutcome.NOT_RUN,
    val shizukuSettingsSnapshotSummary: String? = null,
    val shizukuSettingsSnapshotFailure: String? = null
)

class ArchiveWriter(
    private val resolver: ContentResolver,
    private val deviceInfo: DeviceInfo,
    private val capabilities: CapabilitySnapshot,
    private val packageManager: PackageManager
) {
    fun write(
        outputUri: Uri,
        selectedTrees: List<SelectedTree>,
        includePackages: Boolean,
        includePackageApks: Boolean,
        selectedPackageNames: Set<String>?,
        listener: BackupProgressListener,
        shizukuApkReader: PackageApkSourceReader? = null,
        includeSystemSettings: Boolean = false,
        shizukuSettingsProvider: (() -> String)? = null,
        expectedShizukuUid: Int? = null
    ): BackupWriteResult {
        require(selectedTrees.isNotEmpty() || includePackages || includeSystemSettings) {
            "At least one preservation source must be selected"
        }

        val archiveId = UUID.randomUUID().toString()
        val artifacts = ArrayList<ArchiveArtifact>()
        val selectionErrors = ArrayList<String>()
        var artifactIndex = 0
        var processedBytes = 0L
        var packageCount = 0
        var shizukuApkFallbackAttempts = 0
        var shizukuApkFallbackSuccesses = 0
        var shizukuSettingsSnapshotOutcome =
            ShizukuSettingsSnapshotOutcome.NOT_RUN
        var shizukuSettingsSnapshotSummary: String? = null
        var shizukuSettingsSnapshotFailure: String? = null

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

                if (includePackages) {
                    val inventory = try {
                        PackageInventoryCollector(packageManager).collect()
                    } catch (_: Exception) {
                        selectionErrors +=
                            "packages: PACKAGE_INVENTORY_FAILED"
                        null
                    }

                    if (inventory != null) {
                        val packageResult = PackageArchiveWriter().write(
                            zip = zip,
                            inventory = inventory,
                            includeApkContent = includePackageApks,
                            selectedPackageNames = selectedPackageNames,
                            listener = object : BackupProgressListener {
                                override fun onPackageStarted(
                                    packageName: String
                                ) {
                                    listener.onPackageStarted(packageName)
                                }

                                override fun onFileStarted(path: String) {
                                    listener.onFileStarted(path)
                                }

                                override fun onBytesProcessed(
                                    delta: Long,
                                    totalBytes: Long
                                ) {
                                    processedBytes += delta
                                    listener.onBytesProcessed(
                                        delta,
                                        processedBytes
                                    )
                                }
                            },
                            shizukuApkReader = shizukuApkReader
                        )

                        artifacts += packageResult.artifacts
                        packageCount = packageResult.packageCount
                        shizukuApkFallbackAttempts = packageResult.shizukuApkFallbackAttempts
                        shizukuApkFallbackSuccesses = packageResult.shizukuApkFallbackSuccesses
                    }
                }

                if (includeSystemSettings) {
                    val archivePath = ArchivePaths.SYSTEM_SETTINGS
                    val artifactId = "system-settings-snapshot"
                    val source = "shizuku:system-settings"

                    if (shizukuSettingsProvider == null || expectedShizukuUid == null) {
                        artifacts += ArchiveArtifact(
                            id = artifactId,
                            kind = "system_settings_snapshot",
                            archivePath = archivePath,
                            source = source,
                            status = ArtifactStatus.REQUIRES_PRIVILEGE,
                            sizeBytes = 0L,
                            sha256 = null,
                            modifiedAtEpochMs = null,
                            errorCode = "SHIZUKU_SETTINGS_SOURCE_UNAVAILABLE"
                        )
                        shizukuSettingsSnapshotOutcome =
                            ShizukuSettingsSnapshotOutcome.FAILED
                        shizukuSettingsSnapshotFailure =
                            "Shizuku was unavailable when the settings snapshot was requested."
                    } else {
                        try {
                            val snapshot = ShizukuSystemSettingsCollector.normalize(
                                rawReport = shizukuSettingsProvider.invoke(),
                                expectedUid = expectedShizukuUid,
                                collectedAtUtc = nowUtc()
                            )

                            if (snapshot.readableCount + snapshot.notSetCount == 0) {
                                artifacts += ArchiveArtifact(
                                    id = artifactId,
                                    kind = "system_settings_snapshot",
                                    archivePath = archivePath,
                                    source = source,
                                    status = ArtifactStatus.FAILED,
                                    sizeBytes = 0L,
                                    sha256 = null,
                                    modifiedAtEpochMs = null,
                                    errorCode = "SYSTEM_SETTINGS_READ_FAILED"
                                )
                                shizukuSettingsSnapshotOutcome =
                                    ShizukuSettingsSnapshotOutcome.FAILED
                                shizukuSettingsSnapshotFailure =
                                    "No allow-listed settings could be read."
                            } else {
                                val bytes = snapshot.json.toByteArray(Charsets.UTF_8)
                                writeEntry(zip, archivePath, snapshot.json)
                                artifacts += ArchiveArtifact(
                                    id = artifactId,
                                    kind = "system_settings_snapshot",
                                    archivePath = archivePath,
                                    source = source,
                                    status = ArtifactStatus.PARTIAL,
                                    sizeBytes = bytes.size.toLong(),
                                    sha256 = Sha256.digest(bytes),
                                    modifiedAtEpochMs = null
                                )
                                shizukuSettingsSnapshotOutcome =
                                    if (snapshot.failedCount > 0) {
                                        ShizukuSettingsSnapshotOutcome.PARTIAL
                                    } else {
                                        ShizukuSettingsSnapshotOutcome.SUCCEEDED
                                    }
                                shizukuSettingsSnapshotSummary =
                                    "Read " + snapshot.readableCount +
                                        " allow-listed value(s); " +
                                        snapshot.notSetCount + " not set; " +
                                        snapshot.failedCount + " read failure(s)."
                                shizukuSettingsSnapshotFailure =
                                    if (snapshot.failedCount > 0) {
                                        "Some allow-listed settings could not be read."
                                    } else {
                                        null
                                    }
                            }
                        } catch (_: Exception) {
                            artifacts += ArchiveArtifact(
                                id = artifactId,
                                kind = "system_settings_snapshot",
                                archivePath = archivePath,
                                source = source,
                                status = ArtifactStatus.FAILED,
                                sizeBytes = 0L,
                                sha256 = null,
                                modifiedAtEpochMs = null,
                                errorCode = "SYSTEM_SETTINGS_SNAPSHOT_FAILED"
                            )
                            shizukuSettingsSnapshotOutcome =
                                ShizukuSettingsSnapshotOutcome.FAILED
                            shizukuSettingsSnapshotFailure =
                                "The Shizuku settings snapshot could not be collected or validated."
                        }
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
                    .joinToString("\n") { it.sha256 + "  " + it.archivePath } +
                    "\n"

                writeEntry(zip, ArchivePaths.CHECKSUMS, checksums)
                writeEntry(
                    zip,
                    ArchivePaths.MANIFEST,
                    ArchiveManifestCodec.encode(manifest)
                )
                writeEntry(
                    zip,
                    ArchivePaths.COMPLETE_MARKER,
                    "writer_complete=true\n"
                )
            }
        }

        return BackupWriteResult(
            archiveId = archiveId,
            artifacts = artifacts,
            selectionErrors = selectionErrors,
            packageCount = packageCount,
            shizukuApkFallbackAttempts = shizukuApkFallbackAttempts,
            shizukuApkFallbackSuccesses = shizukuApkFallbackSuccesses,
            shizukuSettingsSnapshotOutcome = shizukuSettingsSnapshotOutcome,
            shizukuSettingsSnapshotSummary = shizukuSettingsSnapshotSummary,
            shizukuSettingsSnapshotFailure = shizukuSettingsSnapshotFailure
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

package io.github.rightward.orpheus

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

fun interface PackageApkSourceReader {
    fun open(path: String): InputStream
}

data class PackageBackupResult(
    val packageCount: Int,
    val artifacts: List<ArchiveArtifact>,
    val shizukuApkFallbackAttempts: Int = 0,
    val shizukuApkFallbackSuccesses: Int = 0
) {
    val failedApkCount: Int
        get() = artifacts.count {
            it.kind == "apk" && it.status == ArtifactStatus.FAILED
        }
}

class PackageArchiveWriter {
    fun write(
        zip: ZipOutputStream,
        inventory: PackageInventory,
        includeApkContent: Boolean,
        selectedPackageNames: Set<String>?,
        listener: BackupProgressListener,
        shizukuApkReader: PackageApkSourceReader? = null
    ): PackageBackupResult {
        val artifacts = ArrayList<ArchiveArtifact>()
        var fallbackAttempts = 0
        var fallbackSuccesses = 0
        val packages = if (selectedPackageNames == null) {
            inventory.packages
        } else {
            inventory.packages.filter {
                it.packageName in selectedPackageNames
            }
        }

        packages.forEachIndexed { packageIndex, packageInfo ->
            listener.onPackageStarted(packageInfo.packageName)

            val packageRoot =
                ArchivePaths.PACKAGES_PREFIX +
                    ArchivePath.sanitizeComponent(packageInfo.packageName)
            val packageApkArtifacts = ArrayList<ArchiveArtifact>()

            if (includeApkContent && packageInfo.shouldPreserveApks) {
                val baseResult = writeApk(
                    zip = zip,
                    packageIndex = packageIndex + 1,
                    packageName = packageInfo.packageName,
                    source = packageInfo.baseApk,
                    archivePath = packageRoot + "/base.apk",
                    artifactSuffix = "base",
                    listener = listener,
                    shizukuApkReader = shizukuApkReader
                )
                packageApkArtifacts += baseResult.artifact
                if (baseResult.shizukuFallbackAttempted) fallbackAttempts++
                if (baseResult.shizukuFallbackSucceeded) fallbackSuccesses++

                packageInfo.splitApks.forEachIndexed { splitIndex, source ->
                    val splitResult = writeApk(
                        zip = zip,
                        packageIndex = packageIndex + 1,
                        packageName = packageInfo.packageName,
                        source = source,
                        archivePath = packageRoot +
                            "/splits/" +
                            ArchivePath.sanitizeComponent(source.name) +
                            ".apk",
                        artifactSuffix = "split-%03d".format(Locale.US, splitIndex + 1),
                        listener = listener,
                        shizukuApkReader = shizukuApkReader
                    )
                    packageApkArtifacts += splitResult.artifact
                    if (splitResult.shizukuFallbackAttempted) fallbackAttempts++
                    if (splitResult.shizukuFallbackSucceeded) fallbackSuccesses++
                }
            }

            val metadataArtifactId =
                "package-%03d-metadata".format(Locale.US, packageIndex + 1)
            val metadataPath = packageRoot + "/metadata.json"
            val metadata = PackageMetadataCodec.encode(
                packageInfo = packageInfo,
                apkArtifacts = packageApkArtifacts,
                includeApkContent = includeApkContent
            )
            val metadataBytes = metadata.toByteArray(Charsets.UTF_8)
            val metadataDigest = Sha256.digest(metadataBytes)

            zip.putNextEntry(ZipEntry(metadataPath))
            zip.write(metadataBytes)
            zip.closeEntry()

            artifacts += ArchiveArtifact(
                id = metadataArtifactId,
                kind = "package_metadata",
                archivePath = metadataPath,
                source = "package:" + packageInfo.packageName + "#metadata",
                status = ArtifactStatus.RESTORABLE,
                sizeBytes = metadataBytes.size.toLong(),
                sha256 = metadataDigest,
                modifiedAtEpochMs = packageInfo.lastUpdateTime
            )
            artifacts += packageApkArtifacts
        }

        return PackageBackupResult(
            packageCount = packages.size,
            artifacts = artifacts,
            shizukuApkFallbackAttempts = fallbackAttempts,
            shizukuApkFallbackSuccesses = fallbackSuccesses
        )
    }

    private fun writeApk(
        zip: ZipOutputStream,
        packageIndex: Int,
        packageName: String,
        source: PackageApkSource,
        archivePath: String,
        artifactSuffix: String,
        listener: BackupProgressListener,
        shizukuApkReader: PackageApkSourceReader?
    ): ApkWriteOutcome {
        val artifactId = "package-%03d-%s-apk".format(Locale.US, packageIndex, artifactSuffix)
        val sourceId = "package:" + packageName + "#" + source.name
        val sourcePath = source.path ?: return ApkWriteOutcome(
            artifact = apkArtifact(
                artifactId, archivePath, sourceId, ArtifactStatus.UNAVAILABLE,
                0L, null, null, "APK_SOURCE_UNAVAILABLE"
            )
        )

        val file = File(sourcePath)
        val localInput = if (file.isFile) {
            runCatching { BufferedInputStream(FileInputStream(file), APK_BUFFER_SIZE) }
                .getOrNull()
        } else {
            null
        }

        val fallbackEligible = shizukuApkReader != null &&
            ShizukuPackageApkReader.supportsPath(sourcePath)
        val fallbackAttempted = localInput == null && fallbackEligible
        val fallbackInput = if (fallbackAttempted) {
            runCatching {
                BufferedInputStream(shizukuApkReader!!.open(sourcePath), APK_BUFFER_SIZE)
            }.getOrNull()
        } else {
            null
        }
        val usedShizukuFallback = fallbackInput != null
        val input = localInput ?: fallbackInput

        if (input == null) {
            val status = when {
                fallbackAttempted -> ArtifactStatus.FAILED
                file.isFile -> ArtifactStatus.REQUIRES_PRIVILEGE
                else -> ArtifactStatus.UNAVAILABLE
            }
            val errorCode = when {
                fallbackAttempted -> "SHIZUKU_APK_READ_FAILED"
                file.isFile -> "APK_READ_REQUIRES_PRIVILEGE"
                else -> "APK_FILE_MISSING"
            }
            return ApkWriteOutcome(
                artifact = apkArtifact(
                    artifactId,
                    archivePath,
                    if (fallbackAttempted) "shizuku:" + sourceId else sourceId,
                    status,
                    0L,
                    null,
                    file.lastModified().takeIf { it > 0L },
                    errorCode
                ),
                shizukuFallbackAttempted = fallbackAttempted
            )
        }

        listener.onFileStarted("package/" + packageName + "/" + source.name)
        zip.putNextEntry(ZipEntry(archivePath))

        return try {
            val digest = Sha256.newDigest()
            val bytes = input.use { stream ->
                stream.copyToWithDigest(
                    output = zip,
                    digest = digest,
                    onBytes = { delta -> listener.onBytesProcessed(delta, delta) }
                )
            }
            zip.closeEntry()
            ApkWriteOutcome(
                artifact = apkArtifact(
                    artifactId,
                    archivePath,
                    if (usedShizukuFallback) "shizuku:" + sourceId else sourceId,
                    ArtifactStatus.RESTORABLE,
                    bytes,
                    Sha256.finish(digest),
                    file.lastModified().takeIf { it > 0L },
                    null
                ),
                shizukuFallbackAttempted = fallbackAttempted,
                shizukuFallbackSucceeded = usedShizukuFallback
            )
        } catch (_: Exception) {
            runCatching { input.close() }
            runCatching { zip.closeEntry() }
            ApkWriteOutcome(
                artifact = apkArtifact(
                    artifactId,
                    archivePath,
                    if (usedShizukuFallback) "shizuku:" + sourceId else sourceId,
                    ArtifactStatus.FAILED,
                    0L,
                    null,
                    file.lastModified().takeIf { it > 0L },
                    "APK_READ_FAILED"
                ),
                shizukuFallbackAttempted = fallbackAttempted
            )
        }
    }

    private fun apkArtifact(
        id: String,
        archivePath: String,
        source: String,
        status: ArtifactStatus,
        sizeBytes: Long,
        sha256: String?,
        modifiedAtEpochMs: Long?,
        errorCode: String?
    ) = ArchiveArtifact(
        id = id,
        kind = "apk",
        archivePath = archivePath,
        source = source,
        status = status,
        sizeBytes = sizeBytes,
        sha256 = sha256,
        modifiedAtEpochMs = modifiedAtEpochMs,
        errorCode = errorCode
    )

    private data class ApkWriteOutcome(
        val artifact: ArchiveArtifact,
        val shizukuFallbackAttempted: Boolean = false,
        val shizukuFallbackSucceeded: Boolean = false
    )

    private companion object {
        const val APK_BUFFER_SIZE = 64 * 1024
    }
}

object PackageMetadataCodec {
    const val SCHEMA_VERSION = 1

    fun encode(
        packageInfo: InstalledPackage,
        apkArtifacts: List<ArchiveArtifact>,
        includeApkContent: Boolean
    ): String {
        val permissions = JSONArray()
        packageInfo.requestedPermissions.forEach { permission ->
            permissions.put(
                JSONObject()
                    .put("name", permission.name)
                    .put("granted", permission.granted)
                    .put("flags", permission.flags)
            )
        }

        val apks = JSONArray()
        apkArtifacts.forEach { artifact ->
            val item = JSONObject()
                .put("id", artifact.id)
                .put("kind", artifact.kind)
                .put("path", artifact.archivePath)
                .put("source", artifact.source)
                .put("status", artifact.status.name.lowercase())
                .put("size", artifact.sizeBytes)

            artifact.sha256?.let { item.put("sha256", it) }
            artifact.errorCode?.let { item.put("error_code", it) }
            apks.put(item)
        }

        return JSONObject()
            .put("schema_version", SCHEMA_VERSION)
            .put("package_name", packageInfo.packageName)
            .put("label", packageInfo.label)
            .put("version_name", packageInfo.versionName ?: JSONObject.NULL)
            .put("version_code", packageInfo.versionCode)
            .put("target_sdk", packageInfo.targetSdk)
            .put("min_sdk", packageInfo.minSdk)
            .put("first_install_time", packageInfo.firstInstallTime)
            .put("last_update_time", packageInfo.lastUpdateTime)
            .put("enabled", packageInfo.enabled)
            .put("system_app", packageInfo.systemApp)
            .put("updated_system_app", packageInfo.updatedSystemApp)
            .put(
                "installer_package",
                packageInfo.installerPackageName ?: JSONObject.NULL
            )
            .put(
                "signing_certificate_sha256",
                JSONArray(packageInfo.signingCertificateSha256)
            )
            .put("requested_permissions", permissions)
            .put(
                "apk_content_preservation",
                JSONObject().put("enabled", includeApkContent)
            )
            .put("apk_artifacts", apks)
            .put(
                "restore",
                JSONObject()
                    .put("package_name", packageInfo.packageName)
                    .put("requires_user_confirmation", true)
                    .put("requires_signature_match", true)
                    .put("runtime_permissions_review_required", true)
            )
            .toString(2)
    }
}

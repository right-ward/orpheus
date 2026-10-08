package io.github.rightward.orpheus

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class PackageBackupResult(
    val packageCount: Int,
    val artifacts: List<ArchiveArtifact>
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
        listener: BackupProgressListener
    ): PackageBackupResult {
        val artifacts = ArrayList<ArchiveArtifact>()

        inventory.packages.forEachIndexed { packageIndex, packageInfo ->
            listener.onPackageStarted(packageInfo.packageName)

            val packageRoot =
                ArchivePaths.PACKAGES_PREFIX +
                    ArchivePath.sanitizeComponent(packageInfo.packageName)
            val packageApkArtifacts = ArrayList<ArchiveArtifact>()

            if (packageInfo.shouldPreserveApks) {
                packageApkArtifacts += writeApk(
                    zip = zip,
                    packageIndex = packageIndex + 1,
                    packageName = packageInfo.packageName,
                    source = packageInfo.baseApk,
                    archivePath = packageRoot + "/base.apk",
                    artifactSuffix = "base",
                    listener = listener
                )

                packageInfo.splitApks.forEachIndexed { splitIndex, source ->
                    packageApkArtifacts += writeApk(
                        zip = zip,
                        packageIndex = packageIndex + 1,
                        packageName = packageInfo.packageName,
                        source = source,
                        archivePath = packageRoot +
                            "/splits/" +
                            ArchivePath.sanitizeComponent(source.name) +
                            ".apk",
                        artifactSuffix = "split-%03d".format(
                            Locale.US,
                            splitIndex + 1
                        ),
                        listener = listener
                    )
                }
            }

            val metadataArtifactId =
                "package-%03d-metadata".format(
                    Locale.US,
                    packageIndex + 1
                )
            val metadataPath = packageRoot + "/metadata.json"
            val metadata = PackageMetadataCodec.encode(
                packageInfo = packageInfo,
                apkArtifacts = packageApkArtifacts
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
            packageCount = inventory.packageCount,
            artifacts = artifacts
        )
    }

    private fun writeApk(
        zip: ZipOutputStream,
        packageIndex: Int,
        packageName: String,
        source: PackageApkSource,
        archivePath: String,
        artifactSuffix: String,
        listener: BackupProgressListener
    ): ArchiveArtifact {
        val artifactId = "package-%03d-%s-apk".format(
            Locale.US,
            packageIndex,
            artifactSuffix
        )
        val sourceId = "package:" + packageName + "#" + source.name

        if (source.path == null) {
            return ArchiveArtifact(
                id = artifactId,
                kind = "apk",
                archivePath = archivePath,
                source = sourceId,
                status = ArtifactStatus.UNAVAILABLE,
                sizeBytes = 0L,
                sha256 = null,
                modifiedAtEpochMs = null,
                errorCode = "APK_SOURCE_UNAVAILABLE"
            )
        }

        val file = File(source.path)
        if (!file.isFile) {
            return ArchiveArtifact(
                id = artifactId,
                kind = "apk",
                archivePath = archivePath,
                source = sourceId,
                status = ArtifactStatus.UNAVAILABLE,
                sizeBytes = 0L,
                sha256 = null,
                modifiedAtEpochMs = file.lastModified().takeIf { it > 0L },
                errorCode = "APK_FILE_MISSING"
            )
        }

        val input = try {
            BufferedInputStream(
                FileInputStream(file),
                64 * 1024
            )
        } catch (_: Exception) {
            return ArchiveArtifact(
                id = artifactId,
                kind = "apk",
                archivePath = archivePath,
                source = sourceId,
                status = ArtifactStatus.FAILED,
                sizeBytes = 0L,
                sha256 = null,
                modifiedAtEpochMs = file.lastModified().takeIf { it > 0L },
                errorCode = "APK_OPEN_FAILED"
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
                    onBytes = { delta ->
                        listener.onBytesProcessed(delta, delta)
                    }
                )
            }

            zip.closeEntry()

            ArchiveArtifact(
                id = artifactId,
                kind = "apk",
                archivePath = archivePath,
                source = sourceId,
                status = ArtifactStatus.RESTORABLE,
                sizeBytes = bytes,
                sha256 = Sha256.finish(digest),
                modifiedAtEpochMs = file.lastModified().takeIf { it > 0L }
            )
        } catch (_: Exception) {
            runCatching { zip.closeEntry() }

            ArchiveArtifact(
                id = artifactId,
                kind = "apk",
                archivePath = archivePath,
                source = sourceId,
                status = ArtifactStatus.FAILED,
                sizeBytes = 0L,
                sha256 = null,
                modifiedAtEpochMs = file.lastModified().takeIf { it > 0L },
                errorCode = "APK_READ_FAILED"
            )
        }
    }
}

object PackageMetadataCodec {
    const val SCHEMA_VERSION = 1

    fun encode(
        packageInfo: InstalledPackage,
        apkArtifacts: List<ArchiveArtifact>
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

package io.github.rightward.orpheus

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File

data class RequestedPackagePermission(
    val name: String,
    val granted: Boolean,
    val flags: Int
)

data class PackageApkSource(
    val name: String,
    val path: String?,
    val sizeBytes: Long?
)

data class InstalledPackage(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val versionCode: Long,
    val targetSdk: Int,
    val minSdk: Int,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val enabled: Boolean,
    val systemApp: Boolean,
    val updatedSystemApp: Boolean,
    val installerPackageName: String?,
    val signingCertificateSha256: List<String>,
    val requestedPermissions: List<RequestedPackagePermission>,
    val baseApk: PackageApkSource,
    val splitApks: List<PackageApkSource>
) {
    val shouldPreserveApks: Boolean
        get() = !systemApp || updatedSystemApp
}

data class PackageInventory(
    val packages: List<InstalledPackage>
) {
    val packageCount: Int
        get() = packages.size

    val preservablePackages: Int
        get() = packages.count { it.shouldPreserveApks }

    val obtainableApkCount: Int
        get() = packages
            .filter { it.shouldPreserveApks }
            .sumOf { packageInfo ->
                (if (packageInfo.baseApk.sizeBytes != null) 1 else 0) +
                    packageInfo.splitApks.count { it.sizeBytes != null }
            }

    val obtainableApkBytes: Long
        get() = packages
            .filter { it.shouldPreserveApks }
            .sumOf { packageInfo ->
                (packageInfo.baseApk.sizeBytes ?: 0L) +
                    packageInfo.splitApks.sumOf { it.sizeBytes ?: 0L }
            }

    val packagesWithoutReadableBaseApk: Int
        get() = packages.count {
            it.shouldPreserveApks && it.baseApk.sizeBytes == null
        }
}

class PackageInventoryCollector(
    private val packageManager: PackageManager
) {
    fun collect(): PackageInventory {
        val packages = packageManager
            .getInstalledPackages(packageInfoFlags())
            .map(::toInstalledPackage)
            .sortedBy { it.packageName }

        return PackageInventory(packages)
    }

    private fun packageInfoFlags(): Int {
        return if (Build.VERSION.SDK_INT >= 28) {
            PackageManager.GET_PERMISSIONS or
                PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNATURES
        }
    }

    private fun toInstalledPackage(info: PackageInfo): InstalledPackage {
        val applicationInfo = info.applicationInfo
            ?: throw IllegalStateException(
                "Package " + info.packageName + " has no application info"
            )

        val flags = applicationInfo.flags
        val splitNames = applicationInfo.splitNames ?: emptyArray()
        val splitPaths = applicationInfo.splitSourceDirs ?: emptyArray()

        return InstalledPackage(
            packageName = info.packageName,
            label = runCatching {
                applicationInfo.loadLabel(packageManager).toString()
            }.getOrDefault(info.packageName),
            versionName = info.versionName,
            versionCode = versionCode(info),
            targetSdk = applicationInfo.targetSdkVersion,
            minSdk = applicationInfo.minSdkVersion,
            firstInstallTime = info.firstInstallTime,
            lastUpdateTime = info.lastUpdateTime,
            enabled = applicationInfo.enabled,
            systemApp = flags and ApplicationInfo.FLAG_SYSTEM != 0,
            updatedSystemApp = flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0,
            installerPackageName = runCatching {
                packageManager.getInstallerPackageName(info.packageName)
            }.getOrNull(),
            signingCertificateSha256 = signingCertificateDigests(info),
            requestedPermissions = requestedPermissions(info),
            baseApk = apkSource(
                name = "base",
                path = applicationInfo.sourceDir
            ),
            splitApks = splitNames.mapIndexed { index, name ->
                apkSource(
                    name = name,
                    path = splitPaths.getOrNull(index)
                )
            }
        )
    }

    private fun versionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= 28) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }

    private fun requestedPermissions(
        info: PackageInfo
    ): List<RequestedPackagePermission> {
        val names = info.requestedPermissions ?: emptyArray()
        val flags = info.requestedPermissionsFlags ?: IntArray(names.size)

        return names.mapIndexed { index, name ->
            val permissionFlags = flags.getOrNull(index) ?: 0
            RequestedPackagePermission(
                name = name,
                granted = permissionFlags and
                    PackageInfo.REQUESTED_PERMISSION_GRANTED != 0,
                flags = permissionFlags
            )
        }
    }

    private fun signingCertificateDigests(
        info: PackageInfo
    ): List<String> {
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            val signingInfo = info.signingInfo ?: return emptyList()

            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
        } else {
            @Suppress("DEPRECATION")
            info.signatures ?: emptyArray()
        }

        return signatures
            .map { Sha256.digest(it.toByteArray()) }
            .distinct()
            .sorted()
    }

    private fun apkSource(
        name: String,
        path: String?
    ): PackageApkSource {
        val sizeBytes = path
            ?.let(::File)
            ?.takeIf { it.isFile && it.canRead() }
            ?.length()

        return PackageApkSource(
            name = name,
            path = path,
            sizeBytes = sizeBytes
        )
    }
}

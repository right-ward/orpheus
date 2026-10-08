package io.github.rightward.orpheus

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageMetadataCodecTest {
    @Test
    fun encodesPackageRestoreMetadataAndArtifacts() {
        val packageInfo = InstalledPackage(
            packageName = "com.example.app",
            label = "Example",
            versionName = "1.2.3",
            versionCode = 42L,
            targetSdk = 35,
            minSdk = 26,
            firstInstallTime = 1000L,
            lastUpdateTime = 2000L,
            enabled = true,
            systemApp = false,
            updatedSystemApp = false,
            installerPackageName = "com.android.vending",
            signingCertificateSha256 = listOf("abc123"),
            requestedPermissions = listOf(
                RequestedPackagePermission(
                    name = "android.permission.CAMERA",
                    granted = true,
                    flags = 1
                )
            ),
            baseApk = PackageApkSource(
                name = "base",
                path = "/data/app/example/base.apk",
                sizeBytes = 1234L
            ),
            splitApks = emptyList()
        )

        val artifact = ArchiveArtifact(
            id = "package-001-base-apk",
            kind = "apk",
            archivePath = "packages/com.example.app/base.apk",
            source = "package:com.example.app#base",
            status = ArtifactStatus.RESTORABLE,
            sizeBytes = 1234L,
            sha256 = "deadbeef",
            modifiedAtEpochMs = 2000L
        )

        val json = JSONObject(
            PackageMetadataCodec.encode(
                packageInfo = packageInfo,
                apkArtifacts = listOf(artifact),
                includeApkContent = true
            )
        )

        assertEquals(1, json.getInt("schema_version"))
        assertEquals("com.example.app", json.getString("package_name"))
        assertEquals(42L, json.getLong("version_code"))
        assertEquals(1, json.getJSONArray("requested_permissions").length())
        assertEquals(
            "packages/com.example.app/base.apk",
            json.getJSONArray("apk_artifacts")
                .getJSONObject(0)
                .getString("path")
        )
        assertTrue(
            json.getJSONObject("restore")
                .getBoolean("requires_signature_match")
        )
        assertTrue(
            json.getJSONObject("apk_content_preservation")
                .getBoolean("enabled")
        )
    }
}

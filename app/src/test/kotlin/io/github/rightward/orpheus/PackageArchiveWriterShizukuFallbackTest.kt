package io.github.rightward.orpheus

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageArchiveWriterShizukuFallbackTest {
    @Test
    fun usesShizukuReaderWhenNormalProcessCannotOpenPackageApk() {
        val payload = "test-apk-payload".toByteArray(Charsets.UTF_8)
        var requestedPath: String? = null
        val output = ByteArrayOutputStream()
        val listener = object : BackupProgressListener {
            override fun onFileStarted(path: String) = Unit
            override fun onBytesProcessed(delta: Long, totalBytes: Long) = Unit
        }

        val result = ZipOutputStream(output).use { zip ->
            PackageArchiveWriter().write(
                zip = zip,
                inventory = PackageInventory(
                    listOf(
                        InstalledPackage(
                            packageName = "example.app",
                            label = "Example",
                            versionName = "1.0",
                            versionCode = 1L,
                            targetSdk = 32,
                            minSdk = 26,
                            firstInstallTime = 1L,
                            lastUpdateTime = 2L,
                            enabled = true,
                            systemApp = false,
                            updatedSystemApp = false,
                            installerPackageName = null,
                            signingCertificateSha256 = emptyList(),
                            requestedPermissions = emptyList(),
                            baseApk = PackageApkSource(
                                name = "base",
                                path = "/data/app/example.app/base.apk",
                                sizeBytes = null
                            ),
                            splitApks = emptyList()
                        )
                    )
                ),
                includeApkContent = true,
                selectedPackageNames = null,
                listener = listener,
                shizukuApkReader = PackageApkSourceReader { path ->
                    requestedPath = path
                    ByteArrayInputStream(payload)
                }
            )
        }

        val apkArtifact = result.artifacts.single { it.kind == "apk" }
        assertEquals(1, result.shizukuApkFallbackAttempts)
        assertEquals(1, result.shizukuApkFallbackSuccesses)
        assertEquals(ArtifactStatus.RESTORABLE, apkArtifact.status)
        assertEquals("shizuku:package:example.app#base", apkArtifact.source)
        assertEquals(payload.size.toLong(), apkArtifact.sizeBytes)
        assertNotNull(apkArtifact.sha256)
        assertEquals("/data/app/example.app/base.apk", requestedPath)
        assertTrue(ShizukuPackageApkReader.supportsPath("/data/app/example/base.apk"))
        assertTrue(!ShizukuPackageApkReader.supportsPath("/data/app/example/oat/arm64/base.odex"))
        assertTrue(!ShizukuPackageApkReader.supportsPath("/data/system/packages.xml"))

        var archivedPayload: ByteArray? = null
        ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            while (true) {
                val entry: ZipEntry = zip.nextEntry ?: break
                if (entry.name == "packages/example.app/base.apk") {
                    archivedPayload = zip.readBytes()
                    break
                }
            }
        }
        assertArrayEquals(payload, archivedPayload)
    }
}

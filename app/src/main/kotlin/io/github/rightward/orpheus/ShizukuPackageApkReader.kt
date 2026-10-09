package io.github.rightward.orpheus

import android.os.ParcelFileDescriptor
import java.io.File
import java.io.InputStream

class ShizukuPackageApkReader(
    private val service: IShizukuProbeService
) : PackageApkSourceReader {
    override fun open(path: String): InputStream {
        val descriptor = service.openPackageApk(path)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor)
    }

    companion object {
        private const val APK_SOURCE_ROOT = "/data/app"

        fun supportsPath(path: String): Boolean =
            path.startsWith(APK_SOURCE_ROOT + File.separator) &&
                path.endsWith(".apk", ignoreCase = true)
    }
}

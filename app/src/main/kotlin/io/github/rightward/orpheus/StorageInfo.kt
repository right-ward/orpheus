package io.github.rightward.orpheus

import android.content.Context
import android.os.Environment
import android.os.StatFs
import java.io.File

data class StorageInfo(
    val label: String,
    val path: String,
    val totalBytes: Long,
    val availableBytes: Long
) {
    val usedBytes: Long
        get() = (totalBytes - availableBytes).coerceAtLeast(0L)

    companion object {
        fun read(context: Context): List<StorageInfo> {
            val targets = listOf(
                "App private storage" to context.filesDir,
                "Primary shared storage" to Environment.getExternalStorageDirectory()
            )

            return targets.mapNotNull { (label, file) ->
                readVolume(label, file)
            }
        }

        private fun readVolume(label: String, file: File): StorageInfo? =
            runCatching {
                val stat = StatFs(file.absolutePath)
                StorageInfo(
                    label = label,
                    path = file.absolutePath,
                    totalBytes = stat.totalBytes,
                    availableBytes = stat.availableBytes
                )
            }.getOrNull()
    }
}

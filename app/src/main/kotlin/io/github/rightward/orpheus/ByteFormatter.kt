package io.github.rightward.orpheus

import java.util.Locale

object ByteFormatter {
    private val units = arrayOf("B", "KiB", "MiB", "GiB", "TiB", "PiB", "EiB")

    fun format(bytes: Long): String {
        require(bytes >= 0L) { "bytes must not be negative" }

        if (bytes < 1024L) {
            return "$bytes B"
        }

        var value = bytes.toDouble()
        var unitIndex = 0

        while (value >= 1024.0 && unitIndex < units.lastIndex) {
            value /= 1024.0
            unitIndex++
        }

        return String.format(
            Locale.US,
            "%.1f %s",
            value,
            units[unitIndex]
        )
    }
}

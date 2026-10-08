package io.github.rightward.orpheus

import org.junit.Assert.assertEquals
import org.junit.Test

class ByteFormatterTest {
    @Test
    fun formatsBytesWithCorrectBinaryUnits() {
        assertEquals("0 B", ByteFormatter.format(0L))
        assertEquals("1023 B", ByteFormatter.format(1023L))
        assertEquals("1.0 KiB", ByteFormatter.format(1024L))
        assertEquals("1.0 MiB", ByteFormatter.format(1024L * 1024L))
        assertEquals("1.0 GiB", ByteFormatter.format(1024L * 1024L * 1024L))
        assertEquals("1.0 TiB", ByteFormatter.format(1024L * 1024L * 1024L * 1024L))
        assertEquals("28.6 MiB", ByteFormatter.format(30_000_000L))
    }
}

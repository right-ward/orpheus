package io.github.rightward.orpheus

import org.junit.Assert.assertEquals
import org.junit.Test

class ArchivePathTest {
    @Test
    fun sanitizesTraversalComponents() {
        assertEquals("__", ArchivePath.sanitizeComponent(".."))
        assertEquals("folder_file", ArchivePath.sanitizeComponent("folder/file"))
    }

    @Test
    fun sanitizesRelativePath() {
        assertEquals(
            "photos/2026/image.jpg",
            ArchivePath.sanitizeRelativePath("photos/2026/image.jpg")
        )
    }
}

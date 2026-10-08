package io.github.rightward.orpheus

import org.junit.Assert.assertEquals
import org.junit.Test

class Sha256Test {
    @Test
    fun matchesKnownVector() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Sha256.digest(ByteArray(0))
        )
    }
}

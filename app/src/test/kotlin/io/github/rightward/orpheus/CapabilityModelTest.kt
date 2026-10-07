package io.github.rightward.orpheus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CapabilityModelTest {
    @Test
    fun findsCapabilityById() {
        val expected = Capability(
            id = CapabilityId.DEVICE_METADATA,
            provider = CapabilityProvider.NORMAL_ANDROID,
            availability = CapabilityAvailability.AVAILABLE,
            detail = "test"
        )
        val snapshot = CapabilitySnapshot(listOf(expected))

        assertEquals(expected, snapshot.forId(CapabilityId.DEVICE_METADATA))
    }

    @Test
    fun returnsNullForMissingCapability() {
        val snapshot = CapabilitySnapshot(emptyList())

        assertNull(snapshot.forId(CapabilityId.PACKAGE_INVENTORY))
    }
}

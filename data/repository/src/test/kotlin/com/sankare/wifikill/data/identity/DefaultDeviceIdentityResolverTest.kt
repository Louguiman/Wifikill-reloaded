package com.sankare.wifikill.data.identity

import com.sankare.wifikill.core.domain.model.Device
import com.sankare.wifikill.core.domain.model.DeviceObservation
import com.sankare.wifikill.core.domain.model.DeviceType
import com.sankare.wifikill.core.domain.model.IdentityConfidence
import com.sankare.wifikill.core.domain.model.ObservationSource
import com.sankare.wifikill.core.domain.model.ReachabilityStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultDeviceIdentityResolverTest {

    private val resolver = DefaultDeviceIdentityResolver()

    @Test
    fun `matches a unique hostname with medium confidence`() = runTest {
        val existing = device(id = "device-1", hostname = "laptop.local")

        val resolution = resolver.resolve(observation(hostname = "LAPTOP.local"), listOf(existing))

        assertEquals("device-1", resolution.deviceId)
        assertEquals(IdentityConfidence.MEDIUM, resolution.confidence)
        assertFalse(resolution.shouldCreateNewDevice)
    }

    @Test
    fun `creates a new device when hostname is ambiguous`() = runTest {
        val existing = listOf(
            device(id = "device-1", hostname = "laptop.local"),
            device(id = "device-2", hostname = "laptop.local"),
        )

        val resolution = resolver.resolve(observation(hostname = "laptop.local"), existing)

        assertTrue(resolution.shouldCreateNewDevice)
        assertEquals(IdentityConfidence.LOW, resolution.confidence)
    }

    @Test
    fun `creates a new device when no evidence is available`() = runTest {
        val resolution = resolver.resolve(observation(hostname = null), emptyList())

        assertTrue(resolution.shouldCreateNewDevice)
        assertEquals(IdentityConfidence.LOW, resolution.confidence)
    }

    private fun device(id: String, hostname: String?) = Device(
        id = id,
        customName = null,
        hostname = hostname,
        manufacturer = null,
        deviceType = DeviceType.UNKNOWN,
        identityConfidence = IdentityConfidence.MEDIUM,
        isTrusted = false,
        notes = null,
        createdAt = 1L,
        updatedAt = 1L,
    )

    private fun observation(hostname: String?) = DeviceObservation(
        id = "observation",
        deviceId = null,
        networkId = "network",
        scanSessionId = "session",
        ipAddress = "192.168.1.20",
        macAddress = null,
        hostname = hostname,
        manufacturer = null,
        source = ObservationSource.LOCAL_DISCOVERY,
        reachability = ReachabilityStatus.RESPONDING,
        responseTimeMs = 5L,
        observedAt = 100L,
    )
}
